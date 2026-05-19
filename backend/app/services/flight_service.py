import logging
import uuid
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.flight import Flight
from app.models.offer import Offer
from app.models.price_snapshot import PriceSnapshot
from app.models.search_history import SearchHistory
from app.scraper.cache import cached
from app.scraper.google_flights import (
    GoogleFlightsScraper,
    ScrapedOffer,
    SearchParams,
)

logger = logging.getLogger(__name__)

# In-memory store for pending searches
_pending_searches: dict[str, dict] = {}


@cached(ttl=1800)
async def _cached_scrape(scraper: GoogleFlightsScraper, params: SearchParams):
    """Scrape with cache - duplicate params within 30 min return cached results."""
    return await scraper.search(params)


class FlightService:
    def __init__(self, db: AsyncSession, scraper: GoogleFlightsScraper | None = None):
        self.db = db
        self.scraper = scraper

    async def search_flights(
        self,
        origin: str,
        destination: str,
        departure_date: str,
        return_date: str | None = None,
        passengers: int = 1,
        cabin_class: str = "economy",
    ) -> dict:
        origin = origin.upper()
        destination = destination.upper()
        search_id = uuid.uuid4().hex[:16]

        # Record search history
        history = SearchHistory(
            search_id=search_id,
            origin=origin,
            destination=destination,
            departure_date=departure_date,
            return_date=return_date,
            passengers=passengers,
            cabin_class=cabin_class,
        )
        self.db.add(history)
        await self.db.commit()

        if self.scraper is None:
            # No scraper - check if we have cached data in DB
            return {
                "search_id": search_id,
                "status": "failed",
                "offers": [],
                "error": "Scraper not available",
            }

        params = SearchParams(
            origin=origin,
            destination=destination,
            departure_date=departure_date,
            return_date=return_date,
            passengers=passengers,
            cabin_class=cabin_class,
        )

        # Mark as pending
        _pending_searches[search_id] = {"status": "pending", "offers": []}

        result = await _cached_scrape(self.scraper, params)

        if result.error:
            _pending_searches[search_id] = {
                "status": "failed",
                "error": result.error,
                "offers": [],
            }
            return _pending_searches[search_id]

        # Persist flights and offers
        saved_offers = await self._save_offers(search_id, result.offers)
        await self.db.commit()

        _pending_searches[search_id] = {
            "status": "complete",
            "offers": saved_offers,
        }
        return _pending_searches[search_id]

    async def get_search_status(self, search_id: str) -> dict:
        if search_id in _pending_searches:
            return _pending_searches[search_id]

        # Check DB for completed search
        stmt = select(Offer).where(Offer.search_id == search_id)
        result = await self.db.execute(stmt)
        offers = result.scalars().all()

        if offers:
            return {"search_id": search_id, "status": "complete", "offers": offers}

        return {"search_id": search_id, "status": "not_found", "offers": []}

    async def get_flight_detail(self, flight_id: str):
        stmt = select(Flight).where(Flight.id == flight_id)
        result = await self.db.execute(stmt)
        flight = result.scalar_one_or_none()

        if not flight:
            return None

        # Get latest offers
        offer_stmt = (
            select(Offer)
            .where(Offer.flight_id == flight_id)
            .order_by(Offer.scraped_at.desc())
            .limit(10)
        )
        offer_result = await self.db.execute(offer_stmt)
        offers = offer_result.scalars().all()

        lowest = min((o.price_cents for o in offers), default=None)

        return {"flight": flight, "offers": offers, "lowest_price_cents": lowest}

    async def get_price_history(self, flight_id: str, days: int = 30):
        stmt = (
            select(PriceSnapshot)
            .where(PriceSnapshot.flight_id == flight_id)
            .order_by(PriceSnapshot.scraped_at.asc())
        )
        result = await self.db.execute(stmt)
        return result.scalars().all()

    async def _save_offers(self, search_id: str, scraped_offers: list[ScrapedOffer]):
        saved = []
        for so in scraped_offers:
            flight_id = Flight.make_id(
                so.flight.origin,
                so.flight.destination,
                so.flight.departure_date,
                so.flight.airline,
                so.flight.flight_number,
            )

            # Upsert flight
            existing = await self.db.get(Flight, flight_id)
            if not existing:
                flight = Flight(
                    id=flight_id,
                    origin=so.flight.origin,
                    destination=so.flight.destination,
                    departure_date=so.flight.departure_date,
                    return_date=so.flight.return_date,
                    airline=so.flight.airline,
                    flight_number=so.flight.flight_number,
                    departure_time=so.flight.departure_time,
                    arrival_time=so.flight.arrival_time,
                    duration_min=so.flight.duration_min,
                    stops=so.flight.stops,
                    cabin_class=so.flight.cabin_class,
                )
                self.db.add(flight)

            # Save offer
            offer = Offer(
                search_id=search_id,
                flight_id=flight_id,
                price_cents=so.price_cents,
                currency=so.currency,
                booking_link=so.booking_link,
                source=so.source,
            )
            self.db.add(offer)

            # Save price snapshot
            snapshot = PriceSnapshot(
                flight_id=flight_id,
                price_cents=so.price_cents,
                currency=so.currency,
                source_website=so.source,
            )
            self.db.add(snapshot)

            saved.append(offer)

        return saved
