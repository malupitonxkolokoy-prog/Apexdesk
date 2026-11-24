package com.example.data.remote

import android.net.Uri
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class BridgeAccountInfoDto(
    val balance: Double = 0.0,
    val equity: Double = 0.0,
    val margin: Double = 0.0,
    val leverage: Int = 100,
    val currency: String = "USD"
)

@JsonClass(generateAdapter = true)
data class BridgeCandleDto(
    val time: Long = 0L,
    val open: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val close: Double = 0.0,
    val tickVolume: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class BridgePositionDto(
    val id: String = "",
    val symbol: String = "",
    val type: String = "POSITION_TYPE_BUY",
    val volume: Double = 0.01,
    val openPrice: Double = 0.0,
    val currentPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0,
    val profit: Double = 0.0,
    val openTimeMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class BridgeTradeRequestDto(
    val actionType: String,
    val symbol: String,
    val volume: Double,
    val openPrice: Double? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val trailingStopPips: Int? = null
)

@JsonClass(generateAdapter = true)
data class BridgeTradeResponseDto(
    val orderId: String = "",
    val positionId: String = "",
    val stringCode: String = "ERR_NO_ERROR",
    val message: String = ""
)

interface Mt5BridgeApi {
    @GET("users/current/accounts/{accountId}/account-information")
    suspend fun getAccountInfo(
        @Header("auth-token") token: String,
        @Path("accountId") accountId: String
    ): Response<BridgeAccountInfoDto>

    @GET("users/current/accounts/{accountId}/positions")
    suspend fun getOpenPositions(
        @Header("auth-token") token: String,
        @Path("accountId") accountId: String
    ): Response<List<BridgePositionDto>>

    @GET("users/current/accounts/{accountId}/historical-market-data/symbols/{symbol}/timeframes/{timeframe}/candles")
    suspend fun getHistoricalCandles(
        @Header("auth-token") token: String,
        @Path("accountId") accountId: String,
        @Path("symbol") symbol: String,
        @Path("timeframe") timeframe: String,
        @Query("limit") limit: Int = 120
    ): Response<List<BridgeCandleDto>>

    @POST("users/current/accounts/{accountId}/trade")
    suspend fun executeTrade(
        @Header("auth-token") token: String,
        @Path("accountId") accountId: String,
        @Body request: BridgeTradeRequestDto
    ): Response<BridgeTradeResponseDto>

    @DELETE("users/current/accounts/{accountId}/positions/{positionId}")
    suspend fun closePosition(
        @Header("auth-token") token: String,
        @Path("accountId") accountId: String,
        @Path("positionId") positionId: String
    ): Response<BridgeTradeResponseDto>
}

object Mt5BridgeClientFactory {
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("auth-token")
            redactHeader("Authorization")
        }
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    fun isSafeHttpsBridgeUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        val uri = Uri.parse(trimmed)
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "169.254.169.254") {
            return false
        }
        return try {
            val addr = InetAddress.getByName(host)
            !(addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress)
        } catch (_: Exception) {
            // Host could be an external domain resolved later on network
            !host.startsWith("10.") && !host.startsWith("192.168.") && !host.startsWith("172.16.")
        }
    }

    fun create(baseUrl: String): Mt5BridgeApi? {
        if (!isSafeHttpsBridgeUrl(baseUrl)) return null
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(Mt5BridgeApi::class.java)
    }
}
