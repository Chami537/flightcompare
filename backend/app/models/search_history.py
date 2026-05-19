import datetime
import uuid

from sqlalchemy import DateTime, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class SearchHistory(Base):
    __tablename__ = "search_history"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    search_id: Mapped[str] = mapped_column(
        String(64), unique=True, default=lambda: uuid.uuid4().hex[:16]
    )
    origin: Mapped[str] = mapped_column(String(10))
    destination: Mapped[str] = mapped_column(String(10))
    departure_date: Mapped[str] = mapped_column(String(10))
    return_date: Mapped[str | None] = mapped_column(String(10), nullable=True)
    passengers: Mapped[int] = mapped_column(Integer, default=1)
    cabin_class: Mapped[str] = mapped_column(String(32), default="economy")
    searched_at: Mapped[datetime.datetime] = mapped_column(
        DateTime, server_default=func.now()
    )
