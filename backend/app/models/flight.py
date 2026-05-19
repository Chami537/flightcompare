import datetime
import hashlib

from sqlalchemy import DateTime, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Flight(Base):
    __tablename__ = "flights"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    origin: Mapped[str] = mapped_column(String(10), index=True)
    destination: Mapped[str] = mapped_column(String(10), index=True)
    departure_date: Mapped[str] = mapped_column(String(10), index=True)
    return_date: Mapped[str | None] = mapped_column(String(10), nullable=True)
    airline: Mapped[str] = mapped_column(String(128))
    flight_number: Mapped[str | None] = mapped_column(String(32), nullable=True)
    departure_time: Mapped[str | None] = mapped_column(String(8), nullable=True)
    arrival_time: Mapped[str | None] = mapped_column(String(8), nullable=True)
    duration_min: Mapped[int | None] = mapped_column(Integer, nullable=True)
    stops: Mapped[int] = mapped_column(Integer, default=0)
    cabin_class: Mapped[str] = mapped_column(String(32), default="economy")
    created_at: Mapped[datetime.datetime] = mapped_column(
        DateTime, server_default=func.now()
    )

    @staticmethod
    def make_id(
        origin: str,
        destination: str,
        departure_date: str,
        airline: str,
        flight_number: str | None = None,
        departure_time: str | None = None,
        arrival_time: str | None = None,
    ) -> str:
        key = (
            f"{origin}|{destination}|{departure_date}|{airline}"
            f"|{flight_number or ''}|{departure_time or ''}|{arrival_time or ''}"
        )
        return hashlib.sha256(key.encode()).hexdigest()[:64]
