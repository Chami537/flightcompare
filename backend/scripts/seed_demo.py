"""Populate the database with demo flight data for testing without scraping."""

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from app.db.session import engine
from app.models import Base
from app.models.flight import Flight
from app.models.offer import Offer
from app.models.price_snapshot import PriceSnapshot
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

DemoSession = async_sessionmaker(engine, class_=AsyncSession)

DEMO_FLIGHTS = [
    {
        "id": "demo-jfk-lax-ua",
        "origin": "JFK",
        "destination": "LAX",
        "departure_date": "2026-06-20",
        "return_date": "2026-06-27",
        "airline": "United Airlines",
        "flight_number": "UA 2405",
        "departure_time": "08:30",
        "arrival_time": "11:45",
        "duration_min": 315,
        "stops": 0,
        "cabin_class": "economy",
    },
    {
        "id": "demo-jfk-lax-dl",
        "origin": "JFK",
        "destination": "LAX",
        "departure_date": "2026-06-20",
        "return_date": None,
        "airline": "Delta",
        "flight_number": "DL 1278",
        "departure_time": "10:15",
        "arrival_time": "13:30",
        "duration_min": 315,
        "stops": 0,
        "cabin_class": "economy",
    },
    {
        "id": "demo-jfk-lax-aa",
        "origin": "JFK",
        "destination": "LAX",
        "departure_date": "2026-06-20",
        "return_date": None,
        "airline": "American Airlines",
        "flight_number": "AA 33",
        "departure_time": "14:00",
        "arrival_time": "17:20",
        "duration_min": 330,
        "stops": 1,
        "cabin_class": "economy",
    },
    {
        "id": "demo-lax-jfk-b6",
        "origin": "LAX",
        "destination": "JFK",
        "departure_date": "2026-06-27",
        "return_date": None,
        "airline": "JetBlue",
        "flight_number": "B6 1123",
        "departure_time": "07:00",
        "arrival_time": "15:20",
        "duration_min": 320,
        "stops": 0,
        "cabin_class": "economy",
    },
    {
        "id": "demo-sfo-ord-wn",
        "origin": "SFO",
        "destination": "ORD",
        "departure_date": "2026-07-01",
        "return_date": "2026-07-08",
        "airline": "Southwest",
        "flight_number": "WN 4567",
        "departure_time": "06:45",
        "arrival_time": "12:55",
        "duration_min": 250,
        "stops": 0,
        "cabin_class": "economy",
    },
]

DEMO_OFFERS = [
    # JFK->LAX on United
    {"search_id": "demo-search-1", "flight_id": "demo-jfk-lax-ua", "price_cents": 28450, "source": "Google Flights"},
    {"search_id": "demo-search-1", "flight_id": "demo-jfk-lax-ua", "price_cents": 29100, "source": "Kayak"},
    {"search_id": "demo-search-1", "flight_id": "demo-jfk-lax-ua", "price_cents": 27999, "source": "Expedia"},
    {"search_id": "demo-search-1", "flight_id": "demo-jfk-lax-ua", "price_cents": 29600, "source": "Skyscanner"},
    # JFK->LAX on Delta
    {"search_id": "demo-search-1", "flight_id": "demo-jfk-lax-dl", "price_cents": 31200, "source": "Google Flights"},
    {"search_id": "demo-search-1", "flight_id": "demo-jfk-lax-dl", "price_cents": 31999, "source": "Kayak"},
    {"search_id": "demo-search-1", "flight_id": "demo-jfk-lax-dl", "price_cents": 30500, "source": "Expedia"},
    # JFK->LAX on AA
    {"search_id": "demo-search-1", "flight_id": "demo-jfk-lax-aa", "price_cents": 34500, "source": "Google Flights"},
    {"search_id": "demo-search-1", "flight_id": "demo-jfk-lax-aa", "price_cents": 35100, "source": "Kayak"},
    # LAX->JFK on JetBlue
    {"search_id": "demo-search-2", "flight_id": "demo-lax-jfk-b6", "price_cents": 26800, "source": "Google Flights"},
    {"search_id": "demo-search-2", "flight_id": "demo-lax-jfk-b6", "price_cents": 27200, "source": "Expedia"},
    # SFO->ORD on Southwest
    {"search_id": "demo-search-3", "flight_id": "demo-sfo-ord-wn", "price_cents": 19800, "source": "Google Flights"},
    {"search_id": "demo-search-3", "flight_id": "demo-sfo-ord-wn", "price_cents": 20100, "source": "Southwest.com"},
]

DEMO_PRICE_HISTORY = [
    {"flight_id": "demo-jfk-lax-ua", "price_cents": 31200, "source_website": "Google Flights"},
    {"flight_id": "demo-jfk-lax-ua", "price_cents": 30500, "source_website": "Google Flights"},
    {"flight_id": "demo-jfk-lax-ua", "price_cents": 29800, "source_website": "Google Flights"},
    {"flight_id": "demo-jfk-lax-ua", "price_cents": 29200, "source_website": "Google Flights"},
    {"flight_id": "demo-jfk-lax-ua", "price_cents": 28450, "source_website": "Google Flights"},
    {"flight_id": "demo-jfk-lax-dl", "price_cents": 34500, "source_website": "Google Flights"},
    {"flight_id": "demo-jfk-lax-dl", "price_cents": 33800, "source_website": "Google Flights"},
    {"flight_id": "demo-jfk-lax-dl", "price_cents": 32500, "source_website": "Google Flights"},
    {"flight_id": "demo-jfk-lax-dl", "price_cents": 31200, "source_website": "Google Flights"},
    {"flight_id": "demo-lax-jfk-b6", "price_cents": 29000, "source_website": "Google Flights"},
    {"flight_id": "demo-lax-jfk-b6", "price_cents": 28500, "source_website": "Google Flights"},
    {"flight_id": "demo-lax-jfk-b6", "price_cents": 27500, "source_website": "Google Flights"},
    {"flight_id": "demo-lax-jfk-b6", "price_cents": 26800, "source_website": "Google Flights"},
]


async def seed():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    async with DemoSession() as session:
        async with session.begin():
            # Insert flights
            for fdata in DEMO_FLIGHTS:
                flight = Flight(**fdata)
                session.add(flight)

            # Insert offers
            for odata in DEMO_OFFERS:
                offer = Offer(**odata)
                session.add(offer)

            # Insert price history
            for pdata in DEMO_PRICE_HISTORY:
                snapshot = PriceSnapshot(**pdata)
                session.add(snapshot)

        print(f"Seeded {len(DEMO_FLIGHTS)} flights, {len(DEMO_OFFERS)} offers, "
              f"{len(DEMO_PRICE_HISTORY)} price snapshots")

    await engine.dispose()


if __name__ == "__main__":
    asyncio.run(seed())
