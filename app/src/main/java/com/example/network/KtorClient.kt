package com.example.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.serialization.json.Json

object KtorClient {
    private var apiKey: String = ""

    fun updateApiKey(key: String) {
        apiKey = key
    }

    fun getApiKey(): String = apiKey

    val client = HttpClient(OkHttp) {
        engine {
            addInterceptor(AuthInterceptor { apiKey })
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
        install(WebSockets) {
            pingInterval = kotlin.time.Duration.parse("15s")
            contentConverter = KotlinxWebsocketSerializationConverter(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
}
