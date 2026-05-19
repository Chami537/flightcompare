package com.flightcompare.navigation

sealed class Route(val route: String) {
    data object Search : Route("search")
    data object Results : Route("results/{searchId}") {
        fun create(searchId: String) = "results/$searchId"
    }
    data object Detail : Route("flight/{flightId}") {
        fun create(flightId: String) = "flight/$flightId"
    }
    data object History : Route("flight/{flightId}/history") {
        fun create(flightId: String) = "flight/$flightId/history"
    }
    data object Bookmarks : Route("bookmarks")
    data object Alerts : Route("alerts")
}
