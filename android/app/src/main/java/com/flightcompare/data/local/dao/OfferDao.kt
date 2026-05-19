package com.flightcompare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flightcompare.data.local.entity.CachedOffer

@Dao
interface OfferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(offers: List<CachedOffer>)

    @Query("SELECT * FROM cached_offers WHERE search_id = :searchId ORDER BY price_cents ASC")
    suspend fun getBySearchId(searchId: String): List<CachedOffer>

    @Query("DELETE FROM cached_offers WHERE cached_at < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT * FROM cached_offers WHERE flight_id = :flightId ORDER BY scraped_at DESC LIMIT :limit")
    suspend fun getLatestForFlight(flightId: String, limit: Int = 10): List<CachedOffer>
}
