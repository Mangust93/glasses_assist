package com.cyanbridge.app.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicBaseUrlInterceptor @Inject constructor() : Interceptor {

    @Volatile
    var baseUrl: String = "http://192.168.1.100:8000"

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val parsed = baseUrl.trimEnd('/').toHttpUrlOrNull()

        if (parsed == null) {
            Timber.w("Invalid base URL: $baseUrl — using original request")
            return chain.proceed(original)
        }

        val newUrl = original.url.newBuilder()
            .scheme(parsed.scheme)
            .host(parsed.host)
            .port(parsed.port)
            .build()

        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}
