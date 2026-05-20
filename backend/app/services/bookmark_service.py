from sqlalchemy import func, select
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
        # 1 query: all bookmarks
        stmt = select(Bookmark).order_by(Bookmark.created_at.desc())
        result = await self.db.execute(stmt)
        bookmarks = result.scalars().all()

        if not bookmarks:
            return []

        flight_ids = [bm.flight_id for bm in bookmarks]

        # 1 query: all flights in bulk
        flight_stmt = select(Flight).where(Flight.id.in_(flight_ids))
        flight_result = await self.db.execute(flight_stmt)
        flights_map = {f.id: f for f in flight_result.scalars().all()}

        # 1 query: latest price per flight (window function)
        latest_price_cte = (
            select(
                Offer.flight_id,
                Offer.price_cents,
                func.row_number()
                .over(
                    partition_by=Offer.flight_id,
                    order_by=Offer.scraped_at.desc(),
                )
                .label("rn"),
            )
            .where(Offer.flight_id.in_(flight_ids))
            .cte()
        )
        price_stmt = select(
            latest_price_cte.c.flight_id,
            latest_price_cte.c.price_cents,
        ).where(latest_price_cte.c.rn == 1)
        price_result = await self.db.execute(price_stmt)
        prices_map = {row[0]: row[1] for row in price_result}

        # Total: 3 queries regardless of N
        enriched = []
        for bm in bookmarks:
            enriched.append(
                {
                    "id": bm.id,
                    "flight_id": bm.flight_id,
                    "flight": flights_map.get(bm.flight_id),
                    "note": bm.note,
                    "current_price_cents": prices_map.get(bm.flight_id),
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
