from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas import FlightSearchRequest, SearchResponse, FlightWithOffers
from app.services.flight_service import FlightService

router = APIRouter(prefix="/flights", tags=["flights"])


def get_flight_service(
    request: Request, db: AsyncSession = Depends(get_db)
) -> FlightService:
    scraper = getattr(request.app.state, "scraper", None)
    kayak = getattr(request.app.state, "kayak_scraper", None)
    return FlightService(db, scraper, kayak)


@router.post("/search", response_model=SearchResponse)
async def search_flights(
    body: FlightSearchRequest,
    service: FlightService = Depends(get_flight_service),
):
    result = await service.search_flights(
        origin=body.origin,
        destination=body.destination,
        departure_date=body.departure_date,
        return_date=body.return_date,
        passengers=body.passengers,
        cabin_class=body.cabin_class,
    )
    return result


@router.get("/search/{search_id}", response_model=SearchResponse)
async def get_search_status(
    search_id: str,
    service: FlightService = Depends(get_flight_service),
):
    result = await service.get_search_status(search_id)
    if result["status"] == "not_found":
        raise HTTPException(status_code=404, detail="Search not found")
    return result


@router.get("/{flight_id}", response_model=FlightWithOffers)
async def get_flight_detail(
    flight_id: str,
    service: FlightService = Depends(get_flight_service),
):
    detail = await service.get_flight_detail(flight_id)
    if detail is None:
        raise HTTPException(status_code=404, detail="Flight not found")
    return detail


@router.get("/{flight_id}/prices")
async def get_price_history(
    flight_id: str,
    days: int = 30,
    service: FlightService = Depends(get_flight_service),
):
    flight = await service.get_flight_detail(flight_id)
    if flight is None:
        raise HTTPException(status_code=404, detail="Flight not found")
    points = await service.get_price_history(flight_id, days)
    return {"flight_id": flight_id, "points": points}
