package io.github.balancewidget

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

fun defaultHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
}
