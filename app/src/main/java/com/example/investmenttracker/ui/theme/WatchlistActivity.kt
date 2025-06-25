package com.example.investmenttracker.ui.theme

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.investmenttracker.R
import com.example.investmenttracker.databinding.ActivityWatchlistBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WatchlistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWatchlistBinding
    private lateinit var adapter: WatchlistAdapter
    private val watchlistItems = mutableListOf<WatchItem>()
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 5 * 60 * 1000L  // 5 minutes interval

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatchlistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadSavedWatchlist()
        startPriceRefresh()

        binding.fabAddWatch.setOnClickListener {
            showAddCoinDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = WatchlistAdapter(watchlistItems)
        binding.recyclerWatchlist.layoutManager = LinearLayoutManager(this)
        binding.recyclerWatchlist.adapter = adapter
    }

    private fun loadSavedWatchlist() {
        val savedItems = getSavedWatchlist(this)
        if (savedItems.isNotEmpty()) {
            watchlistItems.clear()
            watchlistItems.addAll(savedItems)
            adapter.notifyDataSetChanged()
        } else {
            // If no saved items, fetch initial data
            fetchInitialCoins()
        }
    }

    private fun fetchInitialCoins() {
        val call: Call<List<Coin>> = ApiClient.retrofit.getCoinMarkets(
            vsCurrency = "usd",
            perPage = 5,
            order = "market_cap_desc"
        )
        call.enqueue(object : Callback<List<Coin>> {
            override fun onResponse(call: Call<List<Coin>>, response: Response<List<Coin>>) {
                if (response.isSuccessful) {
                    val coins = response.body() ?: emptyList()
                    watchlistItems.clear()
                    coins.forEach { coin ->
                        val price = coin.market_data.current_price["usd"] ?: 0.0
                        watchlistItems.add(WatchItem(coin.symbol, coin.name, price))
                    }
                    adapter.notifyDataSetChanged()
                    saveWatchlist(this@WatchlistActivity, watchlistItems)
                    saveCoinListLocally(this@WatchlistActivity, coins)
                } else {
                    Toast.makeText(this@WatchlistActivity, "Failed to load coins: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<Coin>>, t: Throwable) {
                Toast.makeText(this@WatchlistActivity, "Failed to load coins: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddCoinDialog() {
        val coins = getSavedCoinList(this)
        if (coins.isEmpty()) {
            val call: Call<List<Coin>> = ApiClient.retrofit.getAllCoins()
            call.enqueue(object : Callback<List<Coin>> {
                override fun onResponse(call: Call<List<Coin>>, response: Response<List<Coin>>) {
                    if (response.isSuccessful) {
                        val coinList = response.body() ?: emptyList()
                        saveCoinListLocally(this@WatchlistActivity, coinList)
                        showCoinSelectionDialog(coinList)
                    } else {
                        Toast.makeText(this@WatchlistActivity, "Failed to load coins: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<Coin>>, t: Throwable) {
                    Toast.makeText(this@WatchlistActivity, "Failed to load coins: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            showCoinSelectionDialog(coins)
        }
    }

    private fun showCoinSelectionDialog(coins: List<Coin>) {
        val coinNames = coins.map { it.name }
        
        AlertDialog.Builder(this)
            .setTitle("Add to Watchlist")
            .setItems(coinNames.toTypedArray()) { _, which ->
                val selectedCoin = coins[which]
                val price = selectedCoin.market_data.current_price["usd"] ?: 0.0
                val watchItem = WatchItem(selectedCoin.symbol, selectedCoin.name, price)
                
                if (!watchlistItems.any { it.symbol == watchItem.symbol }) {
                    watchlistItems.add(watchItem)
                    adapter.notifyDataSetChanged()
                    saveWatchlist(this, watchlistItems)
                    Toast.makeText(this, "Added ${watchItem.name} to watchlist", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "${watchItem.name} is already in watchlist", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startPriceRefresh() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchPrices()
                handler.postDelayed(this, refreshInterval)
            }
        }, refreshInterval)
    }

    private fun fetchPrices() {
        if (watchlistItems.isEmpty()) return

        val coinIds = watchlistItems.joinToString(",") { it.symbol }
        val call: Call<Map<String, Map<String, Double>>> = ApiClient.retrofit.getPrice(
            ids = coinIds,
            vsCurrencies = "usd"
        )
        call.enqueue(object : Callback<Map<String, Map<String, Double>>> {
            override fun onResponse(
                call: Call<Map<String, Map<String, Double>>>,
                response: Response<Map<String, Map<String, Double>>>
            ) {
                if (response.isSuccessful) {
                    val prices = response.body()
                    watchlistItems.forEach { item ->
                        prices?.get(item.symbol)?.get("usd")?.let { newPrice ->
                            item.price = newPrice
                        }
                    }
                    adapter.notifyDataSetChanged()
                } else {
                    Log.e("API Error", "Failed to fetch prices: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<Map<String, Map<String, Double>>>, t: Throwable) {
                Log.e("API Error", "Failed to fetch prices: ${t.message}")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        fun saveWatchlist(context: Context, items: List<WatchItem>) {
            val gson = Gson()
            val json = gson.toJson(items)
            context.getSharedPreferences("watchlist_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("watchlist", json)
                .apply()
        }

        fun getSavedWatchlist(context: Context): List<WatchItem> {
            val prefs = context.getSharedPreferences("watchlist_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("watchlist", null)
            return if (json != null) {
                val type = object : TypeToken<List<WatchItem>>() {}.type
                Gson().fromJson(json, type)
            } else {
                emptyList()
            }
        }

        fun saveCoinListLocally(context: Context, coinList: List<Coin>) {
            val gson = Gson()
            val json = gson.toJson(coinList)
            context.getSharedPreferences("crypto_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("coin_list", json)
                .apply()
        }

        fun getSavedCoinList(context: Context): List<Coin> {
            val prefs = context.getSharedPreferences("crypto_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("coin_list", null)
            return if (json != null) {
                val type = object : TypeToken<List<Coin>>() {}.type
                Gson().fromJson(json, type)
            } else {
                emptyList()
            }
        }
    }
}
