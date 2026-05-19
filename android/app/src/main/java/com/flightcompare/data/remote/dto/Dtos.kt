package com.flightcompare.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SearchRequest(
    @SerializedName("origin") val origin: String,
    @SerializedName("destination") val destination: String,
    @SerializedName("departure_date") val departureDate: String,
    @SerializedName("return_date") val returnDate: String? = null,
    @SerializedName("passengers") val passengers: Int = 1,
    @SerializedName("cabin_class") val cabinClass: String = "economy",
)

data class SearchResponse(
    @SerializedName("search_id") val searchId: String,
    @SerializedName("status") val status: String,
    @SerializedName("offers") val offers: List<OfferDto>?,
    @SerializedName("error") val error: String?,
)

data class OfferDto(
    @SerializedName("id") val id: Int,
    @SerializedName("source") val source: String,
    @SerializedName("price_cents") val priceCents: Int,
    @SerializedName("currency") val currency: String?,
    @SerializedName("booking_link") val bookingLink: String?,
    @SerializedName("scraped_at") val scrapedAt: String?,
    @SerializedName("flight_id") val flightId: String?,
)

data class FlightDto(
    @SerializedName("id") val id: String,
    @SerializedName("origin") val origin: String,
    @SerializedName("destination") val destination: String,
    @SerializedName("departure_date") val departureDate: String,
    @SerializedName("return_date") val returnDate: String?,
    @SerializedName("airline") val airline: String,
    @SerializedName("flight_number") val flightNumber: String?,
    @SerializedName("departure_time") val departureTime: String?,
    @SerializedName("arrival_time") val arrivalTime: String?,
    @SerializedName("duration_min") val durationMin: Int?,
    @SerializedName("stops") val stops: Int?,
    @SerializedName("cabin_class") val cabinClass: String?,
)

data class FlightDetailResponse(
    @SerializedName("id") val id: String,
    @SerializedName("origin") val origin: String,
    @SerializedName("destination") val destination: String,
    @SerializedName("departure_date") val departureDate: String,
    @SerializedName("return_date") val returnDate: String?,
    @SerializedName("airline") val airline: String,
    @SerializedName("flight_number") val flightNumber: String?,
    @SerializedName("departure_time") val departureTime: String?,
    @SerializedName("arrival_time") val arrivalTime: String?,
    @SerializedName("duration_min") val durationMin: Int?,
    @SerializedName("stops") val stops: Int?,
    @SerializedName("cabin_class") val cabinClass: String?,
    @SerializedName("offers") val offers: List<OfferDto>?,
    @SerializedName("lowest_price_cents") val lowestPriceCents: Int?,
)

data class PricePointDto(
    @SerializedName("price_cents") val priceCents: Int,
    @SerializedName("currency") val currency: String?,
    @SerializedName("source_website") val sourceWebsite: String?,
    @SerializedName("scraped_at") val scrapedAt: String?,
)

data class PriceHistoryResponse(
    @SerializedName("flight_id") val flightId: String,
    @SerializedName("points") val points: List<PricePointDto>?,
)

data class BookmarkResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("flight_id") val flightId: String,
    @SerializedName("flight") val flight: FlightDto?,
    @SerializedName("note") val note: String?,
    @SerializedName("current_price_cents") val currentPriceCents: Int?,
    @SerializedName("created_at") val createdAt: String?,
)

data class BookmarkRequest(
    @SerializedName("flight_id") val flightId: String,
    @SerializedName("note") val note: String? = null,
)

data class AlertResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("flight_id") val flightId: String,
    @SerializedName("flight") val flight: FlightDto?,
    @SerializedName("target_price_cents") val targetPriceCents: Int,
    @SerializedName("current_price_cents") val currentPriceCents: Int?,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("last_triggered_at") val lastTriggeredAt: String?,
    @SerializedName("created_at") val createdAt: String?,
)

data class AlertRequest(
    @SerializedName("flight_id") val flightId: String,
    @SerializedName("target_price_cents") val targetPriceCents: Int,
)

data class AlertToggleRequest(
    @SerializedName("is_active") val isActive: Boolean,
)

data class AirportDto(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("city") val city: String,
    @SerializedName("country") val country: String,
    @SerializedName("country_code") val countryCode: String,
)
