package com.flightcompare.data.remote

import com.flightcompare.data.remote.dto.*
import com.flightcompare.domain.model.*

fun FlightDto.toDomain() = Flight(
    id = id,
    origin = origin,
    destination = destination,
    departureDate = departureDate,
    returnDate = returnDate,
    airline = airline,
    flightNumber = flightNumber,
    departureTime = departureTime,
    arrivalTime = arrivalTime,
    durationMin = durationMin,
    stops = stops ?: 0,
    cabinClass = cabinClass ?: "economy",
)

fun OfferDto.toDomain() = Offer(
    id = id,
    source = source,
    priceCents = priceCents,
    currency = currency ?: "USD",
    bookingLink = bookingLink,
    flightId = flightId,
    scrapedAt = scrapedAt,
)

fun FlightDetailResponse.toDomain() = FlightWithOffers(
    flight = flight?.toDomain() ?: Flight("", "", "", "", null, "", null, null, null, null, 0, ""),
    offers = offers?.map { it.toDomain() } ?: emptyList(),
    lowestPriceCents = lowestPriceCents,
)

fun PricePointDto.toDomain() = PricePoint(
    priceCents = priceCents,
    currency = currency ?: "USD",
    sourceWebsite = sourceWebsite ?: "",
    scrapedAt = scrapedAt,
)

fun BookmarkResponse.toDomain() = Bookmark(
    id = id,
    flightId = flightId,
    flight = flight?.toDomain(),
    note = note,
    currentPriceCents = currentPriceCents,
)

fun AlertResponse.toDomain() = Alert(
    id = id,
    flightId = flightId,
    flight = flight?.toDomain(),
    targetPriceCents = targetPriceCents,
    currentPriceCents = currentPriceCents,
    isActive = isActive,
)

fun AirportDto.toDomain() = AirportSuggestion(
    code = code,
    name = name,
    city = city,
    country = country,
    countryCode = countryCode,
)
