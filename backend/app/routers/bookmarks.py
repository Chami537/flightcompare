from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_db
from app.schemas import BookmarkRequest, BookmarkResponse
from app.services.bookmark_service import BookmarkService

router = APIRouter(prefix="/bookmarks", tags=["bookmarks"])


def get_bookmark_service(db: AsyncSession = Depends(get_db)) -> BookmarkService:
    return BookmarkService(db)


@router.post("/", response_model=BookmarkResponse, status_code=201)
async def create_bookmark(
    body: BookmarkRequest,
    service: BookmarkService = Depends(get_bookmark_service),
):
    try:
        bookmark = await service.create(body.flight_id, body.note)
    except IntegrityError:
        raise HTTPException(status_code=409, detail="Bookmark already exists for this flight")
    return bookmark


@router.get("/")
async def list_bookmarks(
    service: BookmarkService = Depends(get_bookmark_service),
):
    return await service.list_all()


@router.delete("/{bookmark_id}", status_code=204)
async def delete_bookmark(
    bookmark_id: int,
    service: BookmarkService = Depends(get_bookmark_service),
):
    deleted = await service.delete(bookmark_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Bookmark not found")
