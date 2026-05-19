from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas import AlertRequest, AlertResponse, AlertToggleRequest
from app.services.alert_service import AlertService

router = APIRouter(prefix="/alerts", tags=["alerts"])


def get_alert_service(db: AsyncSession = Depends(get_db)) -> AlertService:
    return AlertService(db)


@router.post("/", response_model=AlertResponse, status_code=201)
async def create_alert(
    body: AlertRequest,
    service: AlertService = Depends(get_alert_service),
):
    alert = await service.create(body.flight_id, body.target_price_cents)
    return alert


@router.get("/")
async def list_alerts(
    service: AlertService = Depends(get_alert_service),
):
    return await service.list_all()


@router.put("/{alert_id}/toggle", status_code=200)
async def toggle_alert(
    alert_id: int,
    body: AlertToggleRequest,
    service: AlertService = Depends(get_alert_service),
):
    updated = await service.toggle(alert_id, body.is_active)
    if not updated:
        raise HTTPException(status_code=404, detail="Alert not found")
    return {"ok": True}


@router.delete("/{alert_id}", status_code=204)
async def delete_alert(
    alert_id: int,
    service: AlertService = Depends(get_alert_service),
):
    deleted = await service.delete(alert_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Alert not found")
