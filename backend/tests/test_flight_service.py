"""Integration tests for FlightService using mock scrapers."""

import pytest
from unittest.mock import AsyncMock

from app.models.flight import Flight
from app.models.offer import Offer
from app.models.price_snapshot import PriceSnapshot
from app.services.flight_service import FlightService

from .conftest import MockScraper, make_mock_offers


@pytest.fixture
def success_scraper():
    return MockScraper(
        offers=make_mock_offers(("United", 35000), ("Delta", 42000)),
        source="MockA",
    )


@pytest.fixture
def error_scraper():
    return MockScraper(error="Connection timeout")


@pytest.fixture
def empty_scraper():
    return MockScraper(offers=[], source="MockEmpty")


@pytest.mark.asyncio
async def test_search_all_scrapers_succeed(db_session, success_scraper):
    """Both scrapers return offers — results are combined."""
    scraper_b = MockScraper(
        offers=make_mock_offers(("American", 38000)),
        source="MockB",
    )
    service = FlightService(db_session, scraper=success_scraper, kayak_scraper=scraper_b)

    result = await service.search_flights(
        origin="JFK", destination="LAX", departure_date="2026-06-20"
    )

    assert result["status"] == "complete"
    assert len(result["offers"]) == 3  # 2 from scraper_a + 1 from scraper_b

    # Verify flights were persisted
    from sqlalchemy import select, func

    flight_count = await db_session.scalar(select(func.count()).select_from(Flight))
    assert flight_count >= 2  # United + Delta + American (at least 2 distinct)

    # Verify offers
    offer_count = await db_session.scalar(select(func.count()).select_from(Offer))
    assert offer_count == 3

    # Verify price snapshots
    snapshot_count = await db_session.scalar(
        select(func.count()).select_from(PriceSnapshot)
    )
    assert snapshot_count == 3


@pytest.mark.asyncio
async def test_search_one_scraper_fails(db_session, success_scraper, error_scraper):
    """One scraper fails — the other's results are still saved."""
    service = FlightService(db_session, scraper=error_scraper, kayak_scraper=success_scraper)

    result = await service.search_flights(
        origin="LAX", destination="SFO", departure_date="2026-07-01"
    )

    assert result["status"] == "complete"
    assert len(result["offers"]) == 2  # from success_scraper only


@pytest.mark.asyncio
async def test_search_all_scrapers_fail(db_session, error_scraper):
    """All scrapers error — status is failed."""
    service = FlightService(db_session, scraper=error_scraper, kayak_scraper=error_scraper)

    result = await service.search_flights(
        origin="JFK", destination="LAX", departure_date="2026-06-20"
    )

    assert result["status"] == "failed"
    assert "Connection timeout" in result.get("error", "")


@pytest.mark.asyncio
async def test_search_no_scrapers(db_session):
    """No scrapers available — status is failed with appropriate error."""
    service = FlightService(db_session, scraper=None, kayak_scraper=None)

    result = await service.search_flights(
        origin="JFK", destination="LAX", departure_date="2026-06-20"
    )

    assert result["status"] == "failed"
    assert "No scrapers" in result["error"]


@pytest.mark.asyncio
async def test_get_price_history_with_days_filter(db_session):
    """Price history respects the days filter."""
    from datetime import datetime, timedelta
    from sqlalchemy import insert

    flight_id = "test-flight-id"

    # Insert a flight
    flight = Flight(
        id=flight_id,
        origin="JFK",
        destination="LAX",
        departure_date="2026-06-20",
        airline="Test Air",
        stops=0,
    )
    db_session.add(flight)
    await db_session.flush()

    # Insert snapshots at various dates
    now = datetime.utcnow()
    dates = [
        now - timedelta(days=60),   # old — should be excluded with days=30
        now - timedelta(days=10),   # recent
        now - timedelta(days=1),    # recent
    ]
    for i, d in enumerate(dates):
        snap = PriceSnapshot(
            flight_id=flight_id,
            price_cents=30000 + i * 1000,
            currency="USD",
            source_website="TestSource",
            scraped_at=d,
        )
        db_session.add(snap)
    await db_session.commit()

    service = FlightService(db_session)

    # Request 30 days — should get only 2 recent points
    points = await service.get_price_history(flight_id, days=30)
    assert len(points) == 2

    # Request 90 days — should get all 3
    points = await service.get_price_history(flight_id, days=90)
    assert len(points) == 3


@pytest.mark.asyncio
async def test_get_search_status_pending(db_session):
    """get_search_status returns 'pending' for an in-progress search."""
    from app.services.flight_service import _pending_searches

    service = FlightService(db_session)
    _pending_searches["test-001"] = {
        "search_id": "test-001",
        "status": "pending",
        "offers": [],
    }

    result = await service.get_search_status("test-001")
    assert result["status"] == "pending"


@pytest.mark.asyncio
async def test_get_search_status_not_found(db_session):
    """get_search_status returns 'not_found' for unknown search IDs."""
    service = FlightService(db_session)
    result = await service.get_search_status("nonexistent")
    assert result["status"] == "not_found"
