import datetime

from sqlalchemy import DateTime, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class PriceSnapshot(Base):
    __tablename__ = "price_snapshots"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    flight_id: Mapped[str] = mapped_column(
        String(64), ForeignKey("flights.id", ondelete="CASCADE"), index=True
    )
    price_cents: Mapped[int] = mapped_column(Integer)
    currency: Mapped[str] = mapped_column(String(8), default="USD")
    source_website: Mapped[str] = mapped_column(String(64))
    scraped_at: Mapped[datetime.datetime] = mapped_column(
        DateTime, server_default=func.now(), index=True
    )
