from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.bookmark import Bookmark
from app.models.flight import Flight
from app.models.offer import Offer


class BookmarkService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def create(self, flight_id: str, note: str | None = None) -> Bookmark:
        bookmark = Bookmark(flight_id=flight_id, note=note)
        self.db.add(bookmark)
        await self.db.commit()
        await self.db.refresh(bookmark)
        return bookmark

    async def list_all(self):
        stmt = select(Bookmark).order_by(Bookmark.created_at.desc())
        result = await self.db.execute(stmt)
        bookmarks = result.scalars().all()

        # Enrich with flight info and current price
        enriched = []
        for bm in bookmarks:
            flight = await self.db.get(Flight, bm.flight_id)
            latest_offer = await self.db.execute(
                select(Offer.price_cents)
                .where(Offer.flight_id == bm.flight_id)
                .order_by(Offer.scraped_at.desc())
                .limit(1)
            )
            current_price = latest_offer.scalar_one_or_none()
            enriched.append(
                {
                    "id": bm.id,
                    "flight_id": bm.flight_id,
                    "flight": flight,
                    "note": bm.note,
                    "current_price_cents": current_price,
                    "created_at": bm.created_at,
                }
            )
        return enriched

    async def delete(self, bookmark_id: int) -> bool:
        bookmark = await self.db.get(Bookmark, bookmark_id)
        if bookmark:
            await self.db.delete(bookmark)
            await self.db.commit()
            return True
        return False
