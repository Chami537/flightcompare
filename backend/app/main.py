import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.db.session import engine, async_session
from app.models import Base
from app.routers import flights, bookmarks, alerts, events
from app.scraper.engine import start_browser, stop_browser
from app.scraper.google_flights import GoogleFlightsScraper
from app.scraper.rate_limiter import TokenBucket
from app.sse.manager import EventManager
from app.services.monitor_service import start_monitor, stop_monitor

logging.basicConfig(
    level=getattr(logging, settings.log_level.upper()),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("Starting FlightCompare backend...")
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    logger.info("Database tables created")

    # Start browser for scraper
    try:
        browser = await start_browser()
        rate_limiter = TokenBucket(
            rate=1.0 / settings.scraper_rate_limit_seconds,
            burst=settings.scraper_burst_limit,
        )
        app.state.scraper = GoogleFlightsScraper(browser, rate_limiter)
        logger.info("Scraper engine ready")
    except Exception as e:
        logger.warning(f"Scraper not available (Playwright may not be installed): {e}")
        app.state.scraper = None

    # Start event manager
    app.state.event_manager = EventManager()

    # Start price monitor
    start_monitor(async_session, app.state.event_manager)

    yield

    # Shutdown
    stop_monitor()
    if app.state.scraper:
        await stop_browser()
    await engine.dispose()
    logger.info("FlightCompare backend stopped")


app = FastAPI(
    title="FlightCompare API",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(flights.router, prefix="/api/v1")
app.include_router(bookmarks.router, prefix="/api/v1")
app.include_router(alerts.router, prefix="/api/v1")
app.include_router(events.router, prefix="/api/v1")


@app.get("/api/v1/health")
async def health():
    return {"status": "ok", "version": "0.1.0"}
