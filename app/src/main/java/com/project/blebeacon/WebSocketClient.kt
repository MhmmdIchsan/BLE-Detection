package com.project.blebeacon

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class WebSocketClient(
    private val url: String,
    private val onMessageReceived: (String) -> Unit
) {
    private val TAG = "WebSocketClient"
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnecting = false
    private var shouldReconnect = true
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Exponential backoff parameters
    private var reconnectAttempt = 0
    private val maxReconnectDelay = 60_000L // Maximum delay of 60 seconds
    private val baseReconnectDelay = 1_000L // Start with 1 second delay

    private var onConnectCallback: (() -> Unit)? = null
    private var onCloseCallback: (() -> Unit)? = null
    private var onErrorCallback: (() -> Unit)? = null

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            isConnecting = false
            reconnectAttempt = 0 // Reset reconnect attempt counter on successful connection
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Message received: $text")
            onMessageReceived(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $reason")
            webSocket.close(1000, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}")
            isConnecting = false
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $reason")
            isConnecting = false
            if (shouldReconnect) {
                scheduleReconnect()
            }
        }
    }

    fun connect() {
        if (isConnecting || webSocket != null) return

        isConnecting = true
        shouldReconnect = true

        try {
            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = client.newWebSocket(request, webSocketListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}")
            isConnecting = false
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect || isConnecting) return

        coroutineScope.launch {
            try {
                // Calculate delay with exponential backoff and jitter
                val delay = calculateReconnectDelay()
                Log.d(TAG, "Scheduling reconnect in $delay ms")
                delay(delay)

                // Check again if we should reconnect before attempting
                if (shouldReconnect && !isConnecting) {
                    Log.d(TAG, "Attempting to reconnect...")
                    reconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in reconnect schedule: ${e.message}")
            }
        }
    }

    private fun calculateReconnectDelay(): Long {
        // Exponential backoff with random jitter
        val exponentialDelay = baseReconnectDelay * (1 shl reconnectAttempt)
        val maxDelay = exponentialDelay.coerceAtMost(maxReconnectDelay)
        val jitter = Random.nextLong(maxDelay / 4) // Add up to 25% random jitter

        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6) // Cap at 2^6 = 64 seconds

        return maxDelay + jitter
    }

    fun sendMessage(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    fun close() {
        shouldReconnect = false
        coroutineScope.cancel()
        webSocket?.close(1000, "Closing")
        webSocket = null
        isConnecting = false
    }

    // Method to check if WebSocket is currently connected
    fun isConnected(): Boolean {
        return webSocket != null && !isConnecting
    }

    // Method to force a reconnection
    fun reconnect() {
        webSocket?.close(1000, "Force reconnect")
        webSocket = null
        isConnecting = false
        reconnectAttempt = 0
        connect()
    }

    fun setOnConnectCallback(callback: () -> Unit) {
        onConnectCallback = callback
        // If already connected, trigger callback immediately
        if (isConnected()) {
            callback.invoke()
        }
    }

    fun setOnCloseCallback(callback: () -> Unit) {
        onCloseCallback = callback
        // If already disconnected, trigger callback immediately
        if (!isConnected()) {
            callback.invoke()
        }
    }

    fun setOnErrorCallback(callback: () -> Unit) {
        onErrorCallback = callback
    }
}