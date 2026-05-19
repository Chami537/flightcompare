package com.flightcompare.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flightcompare.data.local.entity.CachedFlight

@Dao
interface FlightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(flights: List<CachedFlight>)

    @Query("SELECT * FROM cached_flights WHERE id = :id")
    suspend fun getById(id: String): CachedFlight?

    @Query("DELETE FROM cached_flights WHERE cached_at < :before")
    suspend fun deleteOlderThan(before: Long)
}
