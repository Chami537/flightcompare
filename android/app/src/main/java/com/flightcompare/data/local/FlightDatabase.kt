package com.flightcompare.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.flightcompare.data.local.dao.OfferDao
import com.flightcompare.data.local.dao.FlightDao
import com.flightcompare.data.local.entity.CachedOffer
import com.flightcompare.data.local.entity.CachedFlight

@Database(
    entities = [CachedOffer::class, CachedFlight::class],
    version = 1,
    exportSchema = false
)
abstract class FlightDatabase : RoomDatabase() {
    abstract fun offerDao(): OfferDao
    abstract fun flightDao(): FlightDao
}
