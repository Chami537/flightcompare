package com.flightcompare.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_offers")
data class CachedOffer(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "search_id") val searchId: String,
    @ColumnInfo(name = "flight_id") val flightId: String,
    @ColumnInfo(name = "price_cents") val priceCents: Int,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "booking_link") val bookingLink: String?,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "scraped_at") val scrapedAt: String?,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "cached_flights")
data class CachedFlight(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "origin") val origin: String,
    @ColumnInfo(name = "destination") val destination: String,
    @ColumnInfo(name = "departure_date") val departureDate: String,
    @ColumnInfo(name = "return_date") val returnDate: String?,
    @ColumnInfo(name = "airline") val airline: String,
    @ColumnInfo(name = "flight_number") val flightNumber: String?,
    @ColumnInfo(name = "departure_time") val departureTime: String?,
    @ColumnInfo(name = "arrival_time") val arrivalTime: String?,
    @ColumnInfo(name = "duration_min") val durationMin: Int?,
    @ColumnInfo(name = "stops") val stops: Int,
    @ColumnInfo(name = "cabin_class") val cabinClass: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
)
