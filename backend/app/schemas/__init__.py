import datetime

from pydantic import BaseModel, Field


class FlightSearchRequest(BaseModel):
    origin: str = Field(..., min_length=3, max_length=10, examples=["JFK"])
    destination: str = Field(..., min_length=3, max_length=10, examples=["LAX"])
    departure_date: str = Field(
        ...,
        pattern=r"^\d{4}-\d{2}-\d{2}$",
        examples=["2026-06-15"],
    )
    return_date: str | None = Field(None, examples=["2026-06-22"])
    passengers: int = Field(default=1, ge=1, le=9)
    cabin_class: str = Field(
        default="economy",
        pattern=r"^(economy|premium_economy|business|first)$",
        examples=["economy"],
    )


class FlightBase(BaseModel):
    id: str
    origin: str
    destination: str
    departure_date: str
    return_date: str | None = None
    airline: str
    flight_number: str | None = None
    departure_time: str | None = None
    arrival_time: str | None = None
    duration_min: int | None = None
    stops: int = 0
    cabin_class: str = "economy"


class FlightDetail(FlightBase):
    created_at: datetime.datetime | None = None

    model_config = {"from_attributes": True}


class OfferResponse(BaseModel):
    id: int
    flight_id: str = ""
    source: str
    price_cents: int
    currency: str = "USD"
    booking_link: str | None = None
    scraped_at: datetime.datetime | None = None

    model_config = {"from_attributes": True}


class FlightWithOffers(BaseModel):
    flight: FlightDetail
    offers: list[OfferResponse] = []
    lowest_price_cents: int | None = None


class SearchResponse(BaseModel):
    search_id: str
    status: str  # "pending" | "complete" | "failed"
    offers: list[OfferResponse] = []
    error: str | None = None


class PricePoint(BaseModel):
    price_cents: int
    currency: str = "USD"
    source_website: str
    scraped_at: datetime.datetime | None = None

    model_config = {"from_attributes": True}


class PriceHistoryResponse(BaseModel):
    flight_id: str
    points: list[PricePoint] = []


class BookmarkRequest(BaseModel):
    flight_id: str
    note: str | None = None


class BookmarkResponse(BaseModel):
    id: int
    flight_id: str
    flight: FlightDetail | None = None
    note: str | None = None
    current_price_cents: int | None = None
    created_at: datetime.datetime | None = None

    model_config = {"from_attributes": True}


class AlertRequest(BaseModel):
    flight_id: str
    target_price_cents: int = Field(..., gt=0)


class AlertToggleRequest(BaseModel):
    is_active: bool


class AlertResponse(BaseModel):
    id: int
    flight_id: str
    flight: FlightDetail | None = None
    target_price_cents: int
    current_price_cents: int | None = None
    is_active: bool
    last_triggered_at: datetime.datetime | None = None
    created_at: datetime.datetime | None = None

    model_config = {"from_attributes": True}


class AirportResponse(BaseModel):
    code: str = Field(..., examples=["JFK"])
    name: str = Field(..., examples=["John F. Kennedy International Airport"])
    city: str = Field(..., examples=["New York"])
    country: str = Field(..., examples=["United States"])
    country_code: str = Field(..., examples=["US"])
