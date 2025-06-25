package com.example.investmenttracker.ui.theme

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface CoinGeckoApiService {
    @GET("simple/price")
    fun getPrice(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrencies: String = "usd",
        @Query("include_market_cap") includeMarketCap: Boolean = false,
        @Query("include_24hr_vol") include24hrVol: Boolean = false,
        @Query("include_24hr_change") include24hrChange: Boolean = false,
        @Query("include_last_updated_at") includeLastUpdatedAt: Boolean = false
    ): Call<Map<String, Map<String, Double>>>

    @GET("coins/markets")
    fun getCoinMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("ids") ids: String? = null,
        @Query("category") category: String? = null,
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = false,
        @Query("price_change_percentage") priceChangePercentage: String? = null
    ): Call<List<Coin>>

    @GET("coins/list")
    fun getAllCoins(): Call<List<Coin>>
}
