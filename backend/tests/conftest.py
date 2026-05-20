"""Shared test fixtures for FlightCompare backend tests."""

import pytest_asyncio
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.models import Base


@pytest_asyncio.fixture
async def db_session():
    """Provide an async SQLAlchemy session backed by an in-memory SQLite DB."""
    engine = create_async_engine("sqlite+aiosqlite://", echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    async_session = async_sessionmaker(engine, expire_on_commit=False)
    async with async_session() as session:
        yield session

    await engine.dispose()


class MockScraper:
    """A mock BaseScraper that returns canned results."""

    def __init__(self, offers=None, error=None, source="MockSource"):
        self.browser = None
        self.rate_limiter = None
        self._offers = offers or []
        self._error = error
        self._source = source

    async def search(self, params):
        from app.scraper.base import ScrapedFlight, ScrapedOffer, SearchResult

        if self._error:
            return SearchResult(search_id="mock-001", error=self._error)

        offers = []
        for o in self._offers:
            flight = ScrapedFlight(
                origin=params.origin,
                destination=params.destination,
                departure_date=params.departure_date,
                return_date=params.return_date,
                airline=o.get("airline", "Test Air"),
                flight_number=o.get("flight_number"),
                departure_time=o.get("departure_time", "08:00"),
                arrival_time=o.get("arrival_time", "12:00"),
                duration_min=o.get("duration_min", 240),
                stops=o.get("stops", 0),
            )
            offers.append(
                ScrapedOffer(
                    flight=flight,
                    price_cents=o["price_cents"],
                    currency=o.get("currency", "USD"),
                    source=self._source,
                    booking_link=o.get("booking_link"),
                )
            )

        return SearchResult(search_id="mock-001", offers=offers)


def make_mock_offers(*price_specs):
    """Return a list of offer dicts suitable for MockScraper.

    Each price_spec is a tuple: (airline, price_cents).
    """
    return [{"airline": airline, "price_cents": cents} for airline, cents in price_specs]
