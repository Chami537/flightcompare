import asyncio
import logging
import re
from dataclasses import dataclass, field

from playwright.async_api import Browser

from app.config import settings
from app.scraper.rate_limiter import TokenBucket
from app.scraper.user_agents import random_user_agent, random_viewport

logger = logging.getLogger(__name__)

STEALTH_JS = """
// Remove webdriver property
Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
// Fake plugins
Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
// Fake languages
Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
// Override permissions
const originalQuery = window.navigator.permissions.query;
window.navigator.permissions.query = (parameters) => (
    parameters.name === 'notifications' ?
    Promise.resolve({ state: Notification.permission }) :
    originalQuery(parameters)
);
"""


@dataclass
class ScrapedFlight:
    origin: str
    destination: str
    departure_date: str
    return_date: str | None
    airline: str
    flight_number: str | None
    departure_time: str | None
    arrival_time: str | None
    duration_min: int | None
    stops: int
    cabin_class: str = "economy"


@dataclass
class ScrapedOffer:
    flight: ScrapedFlight
    price_cents: int
    currency: str
    source: str
    booking_link: str | None = None


@dataclass
class SearchParams:
    origin: str
    destination: str
    departure_date: str
    return_date: str | None = None
    passengers: int = 1
    cabin_class: str = "economy"


@dataclass
class SearchResult:
    search_id: str
    offers: list[ScrapedOffer] = field(default_factory=list)
    error: str | None = None


def _parse_price(text: str) -> int | None:
    """Parse a price string like '$1,234' to cents (123400)."""
    m = re.search(r"[\$€£¥]\s*([\d,]+(?:\.\d{2})?)", text)
    if not m:
        return None
    clean = m.group(1).replace(",", "")
    return int(float(clean) * 100)


def _parse_duration(text: str) -> int | None:
    """Parse duration like '5h 30m' to minutes (330)."""
    h = re.search(r"(\d+)\s*h", text)
    m = re.search(r"(\d+)\s*m", text)
    hours = int(h.group(1)) if h else 0
    minutes = int(m.group(1)) if m else 0
    return hours * 60 + minutes if (hours or minutes) else None


class GoogleFlightsScraper:
    """Scrapes Google Flights search results using Playwright."""

    def __init__(self, browser: Browser, rate_limiter: TokenBucket):
        self.browser = browser
        self.rate_limiter = rate_limiter

    async def search(self, params: SearchParams) -> SearchResult:
        await self.rate_limiter.acquire()
        import uuid
        search_id = uuid.uuid4().hex[:16]

        ua = random_user_agent()
        vp = random_viewport()
        context = await self.browser.new_context(
            user_agent=ua,
            viewport=vp,
            locale="en-US",
            timezone_id="America/New_York",
        )
        page = await context.new_page()
        await page.add_init_script(STEALTH_JS)

        try:
            await page.goto(
                settings.google_flights_base_url,
                wait_until="domcontentloaded",
                timeout=30000,
            )
            await asyncio.sleep(2)  # let dynamic content load

            # Build URL with search params directly
            search_url = self._build_search_url(params)
            logger.info(f"Navigating to search URL: {search_url}")
            await page.goto(search_url, wait_until="domcontentloaded", timeout=30000)
            await asyncio.sleep(3)

            # Check for captcha
            if await page.locator("form[action*='captcha']").count() > 0:
                return SearchResult(
                    search_id=search_id,
                    error="Captcha encountered - manual intervention needed",
                )

            # Scroll to load more results
            await page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
            await asyncio.sleep(1)

            offers = await self._parse_results(page, params)
            return SearchResult(search_id=search_id, offers=offers)

        except Exception as e:
            logger.error(f"Scrape error: {e}", exc_info=True)
            return SearchResult(search_id=search_id, error=str(e))
        finally:
            await context.close()

    def _build_search_url(self, params: SearchParams) -> str:
        url = (
            f"{settings.google_flights_base_url}?"
            f"q=Flights+to+{params.destination}+from+{params.origin}+"
            f"on+{params.departure_date}"
        )
        if params.return_date:
            url += f"+return+{params.return_date}"
        return url

    async def _parse_results(self, page, params: SearchParams) -> list[ScrapedOffer]:
        offers: list[ScrapedOffer] = []

        # Google Flights renders results in a complex DOM.
        # We target common CSS selector patterns.
        result_items = page.locator(
            'li[role="listitem"], div[role="listitem"], '
            'div[class*="flight"], li[class*="result"], '
            "div.flight-results > div"
        )
        count = await result_items.count()
        logger.info(f"Found {count} potential result items")

        for i in range(min(count, 30)):
            try:
                item = result_items.nth(i)

                # Extract price
                price_text = ""
                price_el = item.locator('[data-gs][class*="price"], span[class*="price"]').first
                try:
                    price_text = await price_el.inner_text(timeout=2000)
                except Exception:
                    # Try generic price pattern
                    try:
                        price_text = await item.locator(
                            'text=/\\$[\\d,]+/'
                        ).first.inner_text(timeout=2000)
                    except Exception:
                        continue

                price_cents = _parse_price(price_text)
                if not price_cents:
                    continue

                # Extract airline and timing info
                airline_text = ""
                try:
                    airline_text = await item.locator(
                        '[class*="airline"], span[class*="carrier"]'
                    ).first.inner_text(timeout=1000)
                except Exception:
                    airline_text = "Unknown Airline"

                # Parse departure/arrival times
                times_text = ""
                try:
                    times_text = await item.locator(
                        '[class*="time"], span[class*="depart"], div[class*="segment"]'
                    ).first.inner_text(timeout=1000)
                except Exception:
                    pass

                # Parse duration
                duration_text = ""
                try:
                    duration_text = await item.locator(
                        '[class*="duration"], span:has-text("h")'
                    ).first.inner_text(timeout=1000)
                except Exception:
                    pass

                # Parse stops
                stops_text = ""
                try:
                    stops_text = await item.locator(
                        '[class*="stops"], span:has-text("stop"), span:has-text("Nonstop")'
                    ).first.inner_text(timeout=1000)
                except Exception:
                    pass

                stops = 0
                if "nonstop" in stops_text.lower():
                    stops = 0
                elif "1 stop" in stops_text.lower():
                    stops = 1
                elif "2 stop" in stops_text.lower():
                    stops = 2
                elif "stop" in stops_text.lower():
                    stops = 1

                # Parse time values
                dep_time, arr_time = self._parse_times(times_text)

                flight = ScrapedFlight(
                    origin=params.origin,
                    destination=params.destination,
                    departure_date=params.departure_date,
                    return_date=params.return_date,
                    airline=airline_text.strip(),
                    flight_number=None,
                    departure_time=dep_time,
                    arrival_time=arr_time,
                    duration_min=_parse_duration(duration_text),
                    stops=stops,
                    cabin_class=params.cabin_class,
                )

                offers.append(
                    ScrapedOffer(
                        flight=flight,
                        price_cents=price_cents,
                        currency="USD",
                        source="Google Flights",
                        booking_link=f"{settings.google_flights_base_url}?"
                        f"q=Flights+to+{params.destination}+from+{params.origin}+"
                        f"on+{params.departure_date}",
                    )
                )
            except Exception as e:
                logger.debug(f"Failed to parse result item {i}: {e}")
                continue

        return offers

    def _parse_times(self, text: str) -> tuple[str | None, str | None]:
        """Extract departure and arrival times from a time range string."""
        if not text:
            return None, None
        # Pattern: "6:30 AM – 10:45 PM" or "06:30 - 22:45"
        pattern = r"(\d{1,2}:\d{2})\s*(?:AM|PM|am|pm)?\s*[-–—]\s*(\d{1,2}:\d{2})\s*(?:AM|PM|am|pm)?"
        m = re.search(pattern, text)
        if m:
            return m.group(1), m.group(2)
        return None, None
