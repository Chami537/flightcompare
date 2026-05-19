import logging

from sqlalchemy.ext.asyncio import AsyncSession
from apscheduler.schedulers.asyncio import AsyncIOScheduler

from app.services.alert_service import AlertService
from app.sse.manager import EventManager

logger = logging.getLogger(__name__)

scheduler = AsyncIOScheduler()


async def check_price_alerts(db: AsyncSession, event_manager: EventManager):
    """Check all active alerts and fire SSE events for triggered ones."""
    alert_service = AlertService(db)
    triggered = await alert_service.check_alerts()
    for t in triggered:
        await event_manager.send_event(
            "alert_triggered",
            {
                "alert_id": t["alert_id"],
                "flight_id": t["flight_id"],
                "current_price_cents": t["current_price_cents"],
                "target_price_cents": t["target_price_cents"],
                "message": f"Price dropped to ${t['current_price_cents'] / 100:.2f}!",
            },
        )
    return len(triggered)


def start_monitor(db_factory, event_manager: EventManager):
    """Start the background price monitor."""
    async def _check():
        async with db_factory() as db:
            try:
                count = await check_price_alerts(db, event_manager)
                if count:
                    logger.info(f"Triggered {count} price alerts")
            except Exception as e:
                logger.error(f"Price monitor error: {e}", exc_info=True)

    scheduler.add_job(_check, "interval", hours=4, id="price_monitor")
    scheduler.start()
    logger.info("Price monitor started (every 4 hours)")


def stop_monitor():
    if scheduler.running:
        scheduler.shutdown(wait=False)
        logger.info("Price monitor stopped")
