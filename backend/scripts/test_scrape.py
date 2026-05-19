"""Quick test of the Google Flights scraper."""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from app.scraper.engine import start_browser, stop_browser
from app.scraper.google_flights import GoogleFlightsScraper, SearchParams
from app.scraper.rate_limiter import TokenBucket


async def main():
    print("Starting browser...")
    browser = await start_browser()

    rate_limiter = TokenBucket(rate=0.1, burst=2)
    scraper = GoogleFlightsScraper(browser, rate_limiter)

    params = SearchParams(
        origin="JFK",
        destination="LAX",
        departure_date="2026-06-20",
        return_date="2026-06-27",
        passengers=1,
        cabin_class="economy",
    )

    print(f"Searching: {params.origin} -> {params.destination}, "
          f"{params.departure_date} -> {params.return_date}")
    result = await scraper.search(params)

    print(f"\nSearch ID: {result.search_id}")
    print(f"Error: {result.error}")

    if result.offers:
        print(f"\nFound {len(result.offers)} offers:")
        for i, offer in enumerate(result.offers[:10]):
            f = offer.flight
            print(f"  {i+1}. {f.airline:20s} | {offer.currency} {offer.price_cents/100:>10.2f} | "
                  f"{f.departure_time or '?'} - {f.arrival_time or '?'} | "
                  f"{f.duration_min or '?'}min | stops: {f.stops}")
    else:
        print("\nNo offers found.")

    await stop_browser()


if __name__ == "__main__":
    asyncio.run(main())
