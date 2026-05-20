"""Skyscanner flight search scraper."""

import asyncio
import logging
import re

from app.scraper.base import (
    BaseScraper,
    ScrapedFlight,
    ScrapedOffer,
    SearchParams,
    SearchResult,
    parse_duration,
    parse_price,
)
from app.scraper.user_agents import random_user_agent, random_viewport

logger = logging.getLogger(__name__)

STEALTH_JS = """
Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
"""


class SkyscannerScraper(BaseScraper):
    """Scrapes Skyscanner flight search results using Playwright."""

    async def search(self, params: SearchParams) -> SearchResult:
        import uuid

        await self.rate_limiter.acquire()
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
            logger.info(f"Skyscanner URL: {url}")
            await page.goto(url, wait_until="domcontentloaded", timeout=30000)
            await asyncio.sleep(8)  # Skyscanner renders slowly

            offers = await self._parse_results(page, params)
            return SearchResult(search_id=search_id, offers=offers)

        except Exception as e:
            logger.error(f"Skyscanner error: {e}", exc_info=True)
            return SearchResult(search_id=search_id, error=str(e))
        finally:
            await context.close()

    def _build_url(self, params: SearchParams) -> str:
        dep = params.departure_date.replace("-", "")
        url = (
            f"https://www.skyscanner.com/transport/flights/"
            f"{params.origin.lower()}/{params.destination.lower()}/{dep}"
        )
        if params.return_date:
            ret = params.return_date.replace("-", "")
            url += f"/{ret}"
        return url + "/"

    async def _parse_results(self, page, params: SearchParams) -> list[ScrapedOffer]:
        offers: list[ScrapedOffer] = []

        # Try CSS selectors first (Tier 1)
        price_selectors = [
            '[class*="Price"]',
            '[class*="price"]',
            'span:has-text("$")',
        ]
        for sel in price_selectors:
            try:
                count = await page.locator(sel).count()
                if count > 0:
                    logger.info(f"Skyscanner: found {count} elements for '{sel}'")
            except Exception:
                pass

        # Text-based parsing fallback (Tier 2)
        try:
            text = await page.locator("body").inner_text(timeout=5000)
        except Exception:
            return offers

        lines = text.split("\n")
        seen = set()

        for i, line in enumerate(lines):
            stripped = line.strip()
            price_cents = parse_price(stripped)
            if not price_cents or price_cents < 1000:  # skip trivial amounts
                continue
            if not stripped.startswith("$") or len(stripped) > 15:
                continue

            airline = "Unknown Airline"
            dep_time = arr_time = None
            duration_min = None
            stops = 0
            flight_number = None

            # Scan surrounding lines for flight details
            for j in range(max(0, i - 20), min(len(lines), i + 30)):
                text_line = lines[j].strip()

                if any(
                    skip in text_line.lower()
                    for skip in [
                        "filter", "sort", "loading", "cookies", "privacy",
                        "adults", "adult", "cabin", "edit search",
                        "skyscanner", "feedback", "about", "terms",
                        "cookie", "consent", "legend", "price alert",
                        "cheapest", "fastest", "best", "outbound",
                    ]
                ):
                    continue

                # Airline detection
                for a in [
                    "American Airlines", "Delta", "United Airlines",
                    "JetBlue", "Southwest", "Alaska Airlines", "Frontier",
                    "Spirit", "Hawaiian Airlines", "Sun Country",
                    "British Airways", "Lufthansa", "Air France",
                    "Emirates", "Qatar Airways", "Singapore Airlines",
                    "Cathay Pacific", "ANA", "Japan Airlines",
                    "Air Canada", "WestJet", "Aeromexico",
                ]:
                    if a in text_line and airline == "Unknown Airline":
                        airline = a
                        break

                # Time pattern: "6:49 AM – 12:01 PM" or "06:49 – 12:01"
                time_m = re.match(
                    r"(\d{1,2}:\d{2})\s*(AM|PM|am|pm)?\s*[–\-]\s*"
                    r"(\d{1,2}:\d{2})\s*(AM|PM|am|pm)?",
                    text_line,
                )
                if time_m and not dep_time:
                    dep_time = (
                        f"{time_m.group(1)} {time_m.group(2) or 'AM'}"
                    ).strip()
                    arr_time = (
                        f"{time_m.group(3)} {time_m.group(4) or 'PM'}"
                    ).strip()

                # Stops
                if "nonstop" in text_line.lower():
                    stops = 0
                else:
                    stop_m = re.match(r"(\d+)\+?\s*stop", text_line, re.IGNORECASE)
                    if stop_m:
                        stops = int(stop_m.group(1))

                # Duration
                dur = parse_duration(text_line)
                if dur and not duration_min:
                    duration_min = dur

            # Deduplicate
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
                flight_number=flight_number,
                departure_time=dep_time,
                arrival_time=arr_time,
                duration_min=duration_min,
                stops=stops,
                cabin_class=params.cabin_class,
            )

            offers.append(
                ScrapedOffer(
                    flight=flight,
                    price_cents=price_cents,
                    currency="USD",
                    source="Skyscanner",
                    booking_link=self._build_url(params),
                )
            )

        logger.info(f"Skyscanner parsed {len(offers)} offers")
        return offers[:30]
