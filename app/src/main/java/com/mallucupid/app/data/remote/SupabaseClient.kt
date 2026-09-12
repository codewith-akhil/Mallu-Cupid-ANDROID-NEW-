package com.mallucupid.app.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Singleton holding the OkHttp client, Moshi instance, and the current
 * access-token holder. All Supabase requests go through this client.
 */
object SupabaseClient {

    @Volatile
    var accessToken: String? = null

    val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private val headerInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .apply {
                accessToken?.let { addHeader("Authorization", "Bearer $it") }
                    ?: addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            }
            .build()
        chain.proceed(req)
    }

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(headerInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }
}
