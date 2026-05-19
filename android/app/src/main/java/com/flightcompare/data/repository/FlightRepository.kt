package com.flightcompare.data.repository

import com.flightcompare.data.local.dao.FlightDao
import com.flightcompare.data.local.dao.OfferDao
import com.flightcompare.data.local.entity.CachedFlight
import com.flightcompare.data.local.entity.CachedOffer
import com.flightcompare.data.remote.ApiService
import com.flightcompare.data.remote.toDomain
import com.flightcompare.data.remote.dto.*
import com.flightcompare.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlightRepository @Inject constructor(
    private val apiService: ApiService,
    private val offerDao: OfferDao,
    private val flightDao: FlightDao,
) {
    suspend fun searchFlights(
        origin: String,
        destination: String,
        departureDate: String,
        returnDate: String? = null,
        passengers: Int = 1,
        cabinClass: String = "economy",
    ): Result<SearchResponse> = runCatching {
        apiService.searchFlights(
            SearchRequest(origin, destination, departureDate, returnDate, passengers, cabinClass)
        )
    }

    suspend fun getSearchStatus(searchId: String): Result<SearchResponse> = runCatching {
        apiService.getSearchStatus(searchId)
    }

    suspend fun getFlightDetail(flightId: String): Result<FlightWithOffers> = runCatching {
        val response = apiService.getFlightDetail(flightId)
        response.toDomain()
    }

    suspend fun getPriceHistory(flightId: String, days: Int = 30): Result<PriceHistory> = runCatching {
        val response = apiService.getPriceHistory(flightId, days)
        PriceHistory(
            flightId = response.flightId,
            points = response.points?.map { it.toDomain() } ?: emptyList()
        )
    }

    suspend fun createBookmark(flightId: String, note: String? = null): Result<Bookmark> = runCatching {
        apiService.createBookmark(BookmarkRequest(flightId, note)).toDomain()
    }

    suspend fun getBookmarks(): Result<List<Bookmark>> = runCatching {
        apiService.getBookmarks().map { it.toDomain() }
    }

    suspend fun deleteBookmark(id: Int): Result<Unit> = runCatching {
        apiService.deleteBookmark(id)
        Unit
    }

    suspend fun createAlert(flightId: String, targetPriceCents: Int): Result<Alert> = runCatching {
        apiService.createAlert(AlertRequest(flightId, targetPriceCents)).toDomain()
    }

    suspend fun getAlerts(): Result<List<Alert>> = runCatching {
        apiService.getAlerts().map { it.toDomain() }
    }

    suspend fun toggleAlert(id: Int, isActive: Boolean): Result<Unit> = runCatching {
        apiService.toggleAlert(id, AlertToggleRequest(isActive))
        Unit
    }

    suspend fun deleteAlert(id: Int): Result<Unit> = runCatching {
        apiService.deleteAlert(id)
        Unit
    }

    // Cache methods
    suspend fun cacheOffers(searchId: String, offers: List<OfferDto>) {
        offerDao.insertAll(offers.map { o ->
            CachedOffer(
                id = o.id,
                searchId = searchId,
                flightId = o.flightId ?: "",
                priceCents = o.priceCents,
                currency = o.currency ?: "USD",
                bookingLink = o.bookingLink,
                source = o.source,
                scrapedAt = o.scrapedAt,
            )
        })
    }

    suspend fun getCachedResults(searchId: String): List<CachedOffer> {
        return offerDao.getBySearchId(searchId)
    }

    suspend fun cleanCache(maxAge: Long = 24 * 3600 * 1000) {
        val cutoff = System.currentTimeMillis() - maxAge
        offerDao.deleteOlderThan(cutoff)
        flightDao.deleteOlderThan(cutoff)
    }
}
