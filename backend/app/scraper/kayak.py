import asyncio
import logging
import re
from dataclasses import dataclass, field

from playwright.async_api import Browser

from app.scraper.rate_limiter import TokenBucket
from app.scraper.user_agents import random_user_agent, random_viewport

logger = logging.getLogger(__name__)

STEALTH_JS = """
Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
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
    m = re.search(r"\$\s*([\d,]+(?:\.\d{1,2})?)", text)
    if not m:
        return None
    return int(float(m.group(1).replace(",", "")) * 100)


def _parse_duration(text: str) -> int | None:
    """Parse '8h 12m' or '5h 47m' to minutes."""
    h = re.search(r"(\d+)\s*h", text)
    m = re.search(r"(\d+)\s*m", text)
    hours = int(h.group(1)) if h else 0
    minutes = int(m.group(1)) if m else 0
    return hours * 60 + minutes if (hours or minutes) else None


class KayakScraper:
    """Scrapes Kayak flight search results using Playwright."""

    def __init__(self, browser: Browser, rate_limiter: TokenBucket):
        self.browser = browser
        self.rate_limiter = rate_limiter

    async def search(self, params: SearchParams) -> SearchResult:
        await self.rate_limiter.acquire()
        import uuid
        search_id = uuid.uuid4().hex[:16]

        context = await self.browser.new_context(
            user_agent=random_user_agent(),
            viewport=random_viewport(),
            locale="en-US",
        )
        page = await context.new_page()
        await page.add_init_script(STEALTH_JS)

        try:
            url = self._build_url(params)
            logger.info(f"Kayak URL: {url}")
            await page.goto(url, wait_until="domcontentloaded", timeout=30000)
            await asyncio.sleep(8)

            offers = await self._parse_results(page, params)
            return SearchResult(search_id=search_id, offers=offers)

        except Exception as e:
            logger.error(f"Kayak error: {e}", exc_info=True)
            return SearchResult(search_id=search_id, error=str(e))
        finally:
            await context.close()

    def _build_url(self, params: SearchParams) -> str:
        dep = params.departure_date.replace("-", "")
        ret = (params.return_date or "").replace("-", "")
        url = (
            f"https://www.kayak.com/flights/"
            f"{params.origin}-{params.destination}/{dep[:4]}-{dep[4:6]}-{dep[6:8]}"
        )
        if ret:
            url += f"/{ret[:4]}-{ret[4:6]}-{ret[6:8]}"
        url += "?sort=price_a"
        return url

    async def _parse_results(self, page, params: SearchParams) -> list[ScrapedOffer]:
        offers: list[ScrapedOffer] = []

        # Kayak renders flight results in a structured format.
        # Each result has a price line like "$451" followed by airline + times info.
        # We parse the full page text and extract flight result blocks.

        try:
            text = await page.locator("body").inner_text(timeout=5000)
        except Exception:
            return offers

        # Find all price lines that are standalone (result prices)
        # Pattern: "$451\nBasic Economy\nSelect" or "$451\nFARE_CLASS\nSelect"
        result_blocks = re.findall(
            r'(\$[\d,]+)\s*(?:[^\n]*?\n)?\s*([^\n]*?)\n\s*(?:View Deal|Select)\s*\n'
            r'(.*?)(?=\n\s*\$\d|$)',
            text, re.DOTALL
        )

        if not result_blocks:
            # Alternative: find by price + Select pattern
            pass

        # Simpler approach: extract all price + following detail blocks
        # Each flight result contains: price, airline, times, duration, stops
        lines = text.split("\n")
        seen = set()

        # Find all dollar amounts that are likely result prices
        for i, line in enumerate(lines):
            stripped = line.strip()
            price_cents = _parse_price(stripped)
            if not price_cents or price_cents < 1000:
                continue
            if not stripped.startswith("$") or len(stripped) > 15:
                continue

            # Look for airline and timing info in surrounding lines
            airline = "Unknown Airline"
            dep_time = arr_time = None
            duration_min = None
            stops = 0

            # Search nearby lines for flight details
            for j in range(max(0, i - 20), min(len(lines), i + 30)):
                l = lines[j].strip()

                # Skip non-result lines
                if any(skip in l.lower() for skip in ['filter', 'sort', 'loading', 'track price',
                                                         'adults', 'adult', 'cabin', 'edit search',
                                                         'kayak', 'feedback', 'book on',
                                                         'amenities', 'airports', 'alliance']):
                    continue

                # Airline detection
                for a in ['American Airlines', 'Delta', 'United Airlines', 'JetBlue',
                          'Southwest', 'Alaska Airlines', 'Frontier', 'Spirit',
                          'Hawaiian Airlines', 'Sun Country Air']:
                    if a in l and airline == "Unknown Airline":
                        airline = a
                        break

                # Time pattern: "6:49 am – 12:01 pm"
                time_m = re.match(
                    r'(\d{1,2}:\d{2})\s*(am|pm)\s*[–\-]\s*(\d{1,2}:\d{2})\s*(am|pm)',
                    l, re.IGNORECASE
                )
                if time_m and not dep_time:
                    dep_time = f"{time_m.group(1)} {time_m.group(2).upper()}"
                    arr_time = f"{time_m.group(3)} {time_m.group(4).upper()}"

                # Stops: "nonstop", "1 stop", "2+ stops"
                if 'nonstop' in l.lower():
                    stops = 0
                else:
                    stop_m = re.match(r'(\d+)\+?\s*stop', l, re.IGNORECASE)
                    if stop_m:
                        stops = int(stop_m.group(1))

                # Duration: "8h 12m" or "5h 47m"
                dur = _parse_duration(l)
                if dur and not duration_min:
                    duration_min = dur

            # Deduplicate by price + airline
            dedup_key = f"{price_cents}-{airline}"
            if dedup_key in seen:
                continue
            seen.add(dedup_key)

            flight = ScrapedFlight(
                origin=params.origin,
                destination=params.destination,
                departure_date=params.departure_date,
                return_date=params.return_date,
                airline=airline,
                flight_number=None,
                departure_time=dep_time,
                arrival_time=arr_time,
                duration_min=duration_min,
                stops=stops,
                cabin_class=params.cabin_class,
            )

            offers.append(ScrapedOffer(
                flight=flight,
                price_cents=price_cents,
                currency="USD",
                source="Kayak",
                booking_link=self._build_url(params),
            ))

        logger.info(f"Kayak parsed {len(offers)} offers")
        return offers[:30]
