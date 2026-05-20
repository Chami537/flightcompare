"""Shared scraper types and base class."""

import re
from abc import ABC, abstractmethod
from dataclasses import dataclass, field

from playwright.async_api import Browser

from app.scraper.rate_limiter import TokenBucket


# ── Shared data types ──────────────────────────────────────────────


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


# ── Shared helpers ─────────────────────────────────────────────────


def parse_price(text: str) -> int | None:
    """Parse a price string like '$1,234', '¥64,651', '€299', 'From 64651 Japanese yen'.

    Returns price in integer cents, or None if no price found.
    """
    # Try standalone currency symbol: $, €, £, ¥ followed by digits
    m = re.search(r"[\$\€\£\¥]\s*([\d,]+(?:\.\d{1,2})?)", text)
    if m:
        clean = m.group(1).replace(",", "")
        return int(float(clean) * 100)

    # Try "From <number> <currency_word>" pattern (Google Flights aria-label)
    m = re.search(
        r"From\s+([\d,]+(?:\.\d{1,2})?)\s*(?:Japanese\s*yen|US\s*dollars?|euros?|pounds?)",
        text,
    )
    if m:
        clean = m.group(1).replace(",", "")
        return int(float(clean) * 100)

    return None


def parse_currency(text: str) -> str:
    """Detect currency from price text. Returns ISO 4217 code."""
    if "¥" in text or "yen" in text.lower():
        return "JPY"
    if "€" in text or "euro" in text.lower():
        return "EUR"
    if "£" in text or "pound" in text.lower():
        return "GBP"
    return "USD"


def parse_duration(text: str) -> int | None:
    """Parse duration like '5h 30m', '8h 12m', '8 hr 12 min' to minutes."""
    # Try compact format first: "5h 30m"
    h = re.search(r"(\d+)\s*h", text)
    m = re.search(r"(\d+)\s*m", text)
    # Also match expanded format: "8 hr 12 min"
    if not h:
        h = re.search(r"(\d+)\s*(?:hr|hour)", text)
    if not m:
        m = re.search(r"(\d+)\s*(?:min|minute)", text)
    hours = int(h.group(1)) if h else 0
    minutes = int(m.group(1)) if m else 0
    return hours * 60 + minutes if (hours or minutes) else None


# ── Abstract base class ────────────────────────────────────────────


class BaseScraper(ABC):
    """Abstract base class for flight search scrapers.

    Subclasses must implement `search()` and may define their own
    STEALTH_JS, URL builders, and result parsers.
    """

    def __init__(self, browser: Browser, rate_limiter: TokenBucket):
        self.browser = browser
        self.rate_limiter = rate_limiter

    @abstractmethod
    async def search(self, params: SearchParams) -> SearchResult:
        ...
