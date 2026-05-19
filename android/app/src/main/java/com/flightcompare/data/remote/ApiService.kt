package com.flightcompare.data.remote

import com.flightcompare.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("flights/search")
    suspend fun searchFlights(@Body request: SearchRequest): SearchResponse

    @GET("flights/search/{searchId}")
    suspend fun getSearchStatus(@Path("searchId") searchId: String): SearchResponse

    @GET("flights/{flightId}")
    suspend fun getFlightDetail(@Path("flightId") flightId: String): FlightDetailResponse

    @GET("flights/{flightId}/prices")
    suspend fun getPriceHistory(
        @Path("flightId") flightId: String,
        @Query("days") days: Int = 30
    ): PriceHistoryResponse

    @POST("bookmarks/")
    suspend fun createBookmark(@Body request: BookmarkRequest): BookmarkResponse

    @GET("bookmarks/")
    suspend fun getBookmarks(): List<BookmarkResponse>

    @DELETE("bookmarks/{id}")
    suspend fun deleteBookmark(@Path("id") id: Int): Response<Unit>

    @POST("alerts/")
    suspend fun createAlert(@Body request: AlertRequest): AlertResponse

    @GET("alerts/")
    suspend fun getAlerts(): List<AlertResponse>

    @PUT("alerts/{id}/toggle")
    suspend fun toggleAlert(
        @Path("id") id: Int,
        @Body request: AlertToggleRequest
    ): Response<Unit>

    @DELETE("alerts/{id}")
    suspend fun deleteAlert(@Path("id") id: Int): Response<Unit>
}
