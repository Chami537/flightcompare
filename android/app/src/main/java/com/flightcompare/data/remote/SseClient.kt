package com.flightcompare.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

sealed class SseEvent {
    data class PriceDrop(
        val flightId: String,
        val oldPrice: Int,
        val newPrice: Int,
    ) : SseEvent()

    data class AlertTriggered(
        val alertId: Int,
        val flightId: String,
        val currentPriceCents: Int,
        val targetPriceCents: Int,
        val message: String,
    ) : SseEvent()

    data class SearchComplete(val searchId: String) : SseEvent()
    data object Ping : SseEvent()
    data class Unknown(val type: String, val data: String) : SseEvent()
}

@Singleton
class SseClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    fun connect(baseUrl: String): Flow<SseEvent> = callbackFlow {
        val request = Request.Builder()
            .url("${baseUrl}events/stream")
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                val event = when (type) {
                    "ping" -> SseEvent.Ping
                    "price_drop" -> parsePriceDrop(data)
                    "alert_triggered" -> parseAlertTriggered(data)
                    "search_complete" -> SseEvent.SearchComplete(
                        gson.fromJson(data, Map::class.java)["search_id"] as? String ?: ""
                    )
                    else -> SseEvent.Unknown(type ?: "", data)
                }
                trySend(event)
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                // Reconnect on failure
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient)
            .newEventSource(request, listener)

        awaitClose { eventSource.cancel() }
    }

    private fun parsePriceDrop(data: String): SseEvent.PriceDrop {
        val map = gson.fromJson(data, Map::class.java) as Map<String, Any>
        return SseEvent.PriceDrop(
            flightId = map["flight_id"] as? String ?: "",
            oldPrice = (map["old_price"] as? Double)?.toInt() ?: 0,
            newPrice = (map["new_price"] as? Double)?.toInt() ?: 0,
        )
    }

    private fun parseAlertTriggered(data: String): SseEvent.AlertTriggered {
        val map = gson.fromJson(data, Map::class.java) as Map<String, Any>
        return SseEvent.AlertTriggered(
            alertId = (map["alert_id"] as? Double)?.toInt() ?: 0,
            flightId = map["flight_id"] as? String ?: "",
            currentPriceCents = (map["current_price_cents"] as? Double)?.toInt() ?: 0,
            targetPriceCents = (map["target_price_cents"] as? Double)?.toInt() ?: 0,
            message = map["message"] as? String ?: "",
        )
    }
}
