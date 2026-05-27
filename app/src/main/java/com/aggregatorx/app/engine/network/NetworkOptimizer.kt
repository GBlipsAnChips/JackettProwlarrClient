package com.aggregatorx.app.engine.network

import okhttp3.*
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object NetworkOptimizer {

    fun buildOptimizedHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionPool(ConnectionPool(
                maxIdleConnections = 20,
                keepAliveDuration = 5,
                timeUnit = TimeUnit.MINUTES
            ))
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return try {
                        java.net.InetAddress.getAllByName(hostname)?.toList() ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }
}
