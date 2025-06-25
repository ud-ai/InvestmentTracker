package com.example.investmenttracker.ui.theme

data class Coin(
    val id: String,
    val symbol: String,
    val name: String,
    val market_data: MarketData // Include MarketData class as a property
)


// MarketData class to represent the structure of the price data in the API response
data class MarketData(
    val current_price: Map<String, Double> // This holds the price in various currencies
)

