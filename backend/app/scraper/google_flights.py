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
    """Parse a price string like '$1,234' or '¥64,651' to cents."""
    # Match $, €, £, ¥ followed by digits
    m = re.search(r"[\$\€\£\¥]\s*([\d,]+(?:\.\d{1,2})?)", text)
    if not m:
        return None
    clean = m.group(1).replace(",", "")
    return int(float(clean) * 100)


def _parse_currency(text: str) -> str:
    """Detect currency symbol from price text."""
    if "¥" in text:
        return "JPY"
    if "€" in text:
        return "EUR"
    if "£" in text:
        return "GBP"
    return "USD"


def _parse_duration(text: str) -> int | None:
    """Parse duration like '5h 30m' or '8 hr 12 min' to minutes."""
    h = re.search(r"(\d+)\s*(?:h|hr|hour)", text)
    m = re.search(r"(\d+)\s*(?:m|min|minute)", text)
    hours = int(h.group(1)) if h else 0
    minutes = int(m.group(1)) if m else 0
    return hours * 60 + minutes if (hours or minutes) else None


def _parse_aria_label(label: str) -> dict | None:
    """Parse a Google Flights aria-label string into structured data.

    Example: "From 64651 Japanese yen round trip total. 1 stop flight with American.
    Leaves John F. Kennedy International Airport at 6:49 AM on Saturday, June 20
    and arrives at Los Angeles International Airport at 12:01 PM on Saturday, June 20.
    Total duration 8 hr 12 min."
    """
    result: dict = {}

    # Price: "From 64651 Japanese yen" or "From $284.50"
    price_m = re.search(
        r"From\s+[\$\€\£\¥]?\s*([\d,]+(?:\.\d{1,2})?)\s*(?:Japanese\s*yen|US\s*dollars?|euros?|pounds?)?",
        label,
    )
    if price_m:
        clean = price_m.group(1).replace(",", "")
        result["price_cents"] = int(float(clean) * 100)

        # Currency
        if "yen" in label[price_m.start() : price_m.end()].lower():
            result["currency"] = "JPY"
        elif "euro" in label[price_m.start() : price_m.end()].lower():
            result["currency"] = "EUR"
        elif "pound" in label[price_m.start() : price_m.end()].lower():
            result["currency"] = "GBP"
        else:
            result["currency"] = "USD"

    # Stops: "Nonstop flight" or "1 stop flight" or "2 stops"
    if re.search(r"Nonstop", label):
        result["stops"] = 0
    else:
        stop_m = re.search(r"(\d+)\s*[-]?\s*stop", label)
        if stop_m:
            result["stops"] = int(stop_m.group(1))

    # Airline: "with American." or "with United Airlines."
    airline_m = re.search(r"with\s+([^.]+)\.", label)
    if airline_m:
        result["airline"] = airline_m.group(1).strip()

    # Departure time: "at 6:49 AM on"
    dep_time_m = re.search(r"Leaves.*?at\s+(\d{1,2}:\d{2}[ \s]*(?:AM|PM))", label)
    if dep_time_m:
        result["departure_time"] = dep_time_m.group(1).replace(" ", " ")

    # Arrival time: "arrives at 12:01 PM on"
    arr_time_m = re.search(r"arrives.*?at\s+(\d{1,2}:\d{2}[ \s]*(?:AM|PM))", label)
    if arr_time_m:
        result["arrival_time"] = arr_time_m.group(1).replace(" ", " ")

    # Departure date: "on Saturday, June 20"
    dep_date_m = re.search(r"Leaves.*?on\s+[A-Z][a-z]+day,\s*([A-Z][a-z]+ \d{1,2})", label)
    if dep_date_m:
        result["departure_date_str"] = dep_date_m.group(1)

    # Return date: Look for second "on <day>, <month> <day>" after "arrives"
    arrives_part = label[label.find("arrives"):] if "arrives" in label else ""
    ret_date_m = re.search(r"on\s+[A-Z][a-z]+day,\s*([A-Z][a-z]+ \d{1,2})", arrives_part)
    if ret_date_m:
        result["arrival_date_str"] = ret_date_m.group(1)

    # Duration: "Total duration 8 hr 12 min"
    dur_m = re.search(r"Total\s+duration\s+([^.]*)", label)
    if dur_m:
        dur_text = dur_m.group(1).strip()
        result["duration_min"] = _parse_duration(dur_text)

    # Round trip or one way
    if "round trip" in label.lower():
        result["trip_type"] = "round_trip"
    elif "one way" in label.lower():
        result["trip_type"] = "one_way"

    return result if result else None


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
            f"gl=US&hl=en&"
            f"q=Flights+to+{params.destination}+from+{params.origin}+"
            f"on+{params.departure_date}"
        )
        if params.return_date:
            url += f"+return+{params.return_date}"
        return url

    async def _parse_results(self, page, params: SearchParams) -> list[ScrapedOffer]:
        offers: list[ScrapedOffer] = []

        # Primary approach: Find flight cards by aria-label on .JMc5Xc elements.
        # Each card has an aria-label like:
        # "From 64651 Japanese yen round trip total. 1 stop flight with American.
        #  Leaves ... at 6:49 AM on Saturday, June 20
        #  and arrives at ... at 12:01 PM on Saturday, June 20.
        #  Total duration 8 hr 12 min."
        cards = page.locator(".JMc5Xc")
        card_count = await cards.count()
        logger.info(f"Found {card_count} flight cards via .JMc5Xc")

        for i in range(card_count):
            try:
                card = cards.nth(i)
                aria_label = await card.get_attribute("aria-label") or ""

                if not aria_label:
                    # Fallback: try to get text from inner elements
                    continue

                parsed = _parse_aria_label(aria_label)
                if not parsed or "price_cents" not in parsed:
                    continue

                flight = ScrapedFlight(
                    origin=params.origin,
                    destination=params.destination,
                    departure_date=params.departure_date,
                    return_date=params.return_date,
                    airline=parsed.get("airline", "Unknown Airline"),
                    flight_number=None,
                    departure_time=parsed.get("departure_time"),
                    arrival_time=parsed.get("arrival_time"),
                    duration_min=parsed.get("duration_min"),
                    stops=parsed.get("stops", 0),
                    cabin_class=params.cabin_class,
                )

                booking_link = (
                    f"{settings.google_flights_base_url}?"
                    f"q=Flights+to+{params.destination}+from+{params.origin}+"
                    f"on+{params.departure_date}"
                )
                if params.return_date:
                    booking_link += f"+return+{params.return_date}"

                offers.append(
                    ScrapedOffer(
                        flight=flight,
                        price_cents=parsed["price_cents"],
                        currency=parsed.get("currency", "USD"),
                        source="Google Flights",
                        booking_link=booking_link,
                    )
                )
            except Exception as e:
                logger.debug(f"Failed to parse result card {i}: {e}")
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
