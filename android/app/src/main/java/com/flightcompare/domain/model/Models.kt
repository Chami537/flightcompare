package com.flightcompare.domain.model

data class Flight(
    val id: String,
    val origin: String,
    val destination: String,
    val departureDate: String,
    val returnDate: String?,
    val airline: String,
    val flightNumber: String?,
    val departureTime: String?,
    val arrivalTime: String?,
    val durationMin: Int?,
    val stops: Int,
    val cabinClass: String,
)

data class Offer(
    val id: Int,
    val source: String,
    val priceCents: Int,
    val currency: String,
    val bookingLink: String?,
    val flightId: String? = null,
    val scrapedAt: String? = null,
)

data class FlightWithOffers(
    val flight: Flight,
    val offers: List<Offer>,
    val lowestPriceCents: Int?,
)

data class PricePoint(
    val priceCents: Int,
    val currency: String,
    val sourceWebsite: String,
    val scrapedAt: String?,
)

data class PriceHistory(
    val flightId: String,
    val points: List<PricePoint>,
)

data class Bookmark(
    val id: Int,
    val flightId: String,
    val flight: Flight?,
    val note: String?,
    val currentPriceCents: Int?,
)

data class Alert(
    val id: Int,
    val flightId: String,
    val flight: Flight?,
    val targetPriceCents: Int,
    val currentPriceCents: Int?,
    val isActive: Boolean,
)

sealed class SearchState {
    data object Idle : SearchState()
    data object Loading : SearchState()
    data class Success(val offers: List<Offer>, val flights: Map<String, Flight>) : SearchState()
    data class Error(val message: String) : SearchState()
}

data class AirportSuggestion(
    val code: String,
    val name: String,
    val city: String,
    val country: String,
    val countryCode: String,
)
