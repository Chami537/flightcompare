import logging
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.alert import Alert
from app.models.flight import Flight
from app.models.offer import Offer

logger = logging.getLogger(__name__)


class AlertService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def create(self, flight_id: str, target_price_cents: int) -> Alert:
        alert = Alert(
            flight_id=flight_id,
            target_price_cents=target_price_cents,
        )
        self.db.add(alert)
        await self.db.commit()
        await self.db.refresh(alert)
        return alert

    async def list_all(self):
        stmt = select(Alert).order_by(Alert.created_at.desc())
        result = await self.db.execute(stmt)
        alerts = result.scalars().all()

        enriched = []
        for a in alerts:
            flight = await self.db.get(Flight, a.flight_id)
            latest_offer = await self.db.execute(
                select(Offer.price_cents)
                .where(Offer.flight_id == a.flight_id)
                .order_by(Offer.scraped_at.desc())
                .limit(1)
            )
            current_price = latest_offer.scalar_one_or_none()
            enriched.append(
                {
                    "id": a.id,
                    "flight_id": a.flight_id,
                    "flight": flight,
                    "target_price_cents": a.target_price_cents,
                    "current_price_cents": current_price,
                    "is_active": a.is_active,
                    "last_triggered_at": a.last_triggered_at,
                    "created_at": a.created_at,
                }
            )
        return enriched

    async def toggle(self, alert_id: int, is_active: bool) -> bool:
        alert = await self.db.get(Alert, alert_id)
        if alert:
            alert.is_active = is_active
            await self.db.commit()
            return True
        return False

    async def delete(self, alert_id: int) -> bool:
        alert = await self.db.get(Alert, alert_id)
        if alert:
            await self.db.delete(alert)
            await self.db.commit()
            return True
        return False

    async def check_alerts(self) -> list[dict]:
        """Check all active alerts against current prices. Returns triggered alerts."""
        stmt = select(Alert).where(Alert.is_active == True)
        result = await self.db.execute(stmt)
        active_alerts = result.scalars().all()

        triggered = []
        for alert in active_alerts:
            latest = await self.db.execute(
                select(Offer.price_cents)
                .where(Offer.flight_id == alert.flight_id)
                .order_by(Offer.scraped_at.desc())
                .limit(1)
            )
            current_price = latest.scalar_one_or_none()
            if current_price and current_price <= alert.target_price_cents:
                logger.info(
                    f"Alert {alert.id} triggered: price {current_price} <= "
                    f"target {alert.target_price_cents}"
                )
                alert.last_triggered_at = datetime.utcnow()
                alert.is_active = False  # Auto-deactivate after trigger
                triggered.append(
                    {
                        "alert_id": alert.id,
                        "flight_id": alert.flight_id,
                        "current_price_cents": current_price,
                        "target_price_cents": alert.target_price_cents,
                    }
                )

        if triggered:
            await self.db.commit()

        return triggered
