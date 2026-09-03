package com.example.network

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val apiKeyProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${apiKeyProvider()}")
            .build()
        return chain.proceed(request)
    }
}
