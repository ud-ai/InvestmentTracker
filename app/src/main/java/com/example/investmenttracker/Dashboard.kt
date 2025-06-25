package com.example.investmenttracker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.investmenttracker.databinding.ActivityDashboardBinding
import com.example.investmenttracker.ui.theme.AddInvestmentActivity
import com.example.investmenttracker.ui.theme.WatchlistActivity
import com.example.investmenttracker.ui.theme.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.example.investmenttracker.ui.theme.Investment

import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class Dashboard : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Check if user is logged in
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Use ViewBinding
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fetch and display real portfolio data
        fetchAndDisplayPortfolioData()

        // Button to Add Investment
        binding.addInvestmentButton.setOnClickListener {
            val intent = Intent(this, AddInvestmentActivity::class.java)
            startActivity(intent)
        }

        // Button to View Watchlist
        binding.viewWatchlistButton.setOnClickListener {
            val intent = Intent(this, WatchlistActivity::class.java)
            startActivity(intent)
        }

        // Add logout functionality
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun fetchAndDisplayPortfolioData() {
        val userId = auth.currentUser?.uid ?: return
        val database = FirebaseDatabase.getInstance()
        val reference = database.getReference("users").child(userId).child("investments")
        reference.keepSynced(true)  // Enable offline persistence for this reference

        // Use addValueEventListener for real-time updates
        reference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val investments = mutableListOf<Investment>()
                for (child in snapshot.children) {
                    val investment = child.getValue(Investment::class.java)
                    if (investment != null) {
                        investments.add(investment)
                    }
                }
                updatePortfolioWithInvestments(investments)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                runOnUiThread {
                    Toast.makeText(this@Dashboard, 
                        "Failed to load portfolio data: ${error.message}", 
                        Toast.LENGTH_LONG).show()
                    updatePortfolioData(0.0, 0.0)
                    initializeChart(emptyList())
                }
            }
        })
    }

    private fun updatePortfolioWithInvestments(investments: List<Investment>) {
        if (investments.isEmpty()) {
            updatePortfolioData(0.0, 0.0)
            initializeChart(emptyList())
            return
        }
        var totalValue = 0.0
        var totalCost = 0.0
        val chartEntries = ArrayList<Entry>()
        investments.forEachIndexed { index, inv ->
            val value = inv.quantity * inv.price
            totalValue += value
            totalCost += inv.quantity * inv.price // Assuming price is purchase price
            chartEntries.add(Entry(index.toFloat(), value.toFloat()))
        }
        val growth = totalValue - totalCost
        updatePortfolioData(totalValue, growth)
        initializeChart(chartEntries)
    }

    private fun updatePortfolioData(portfolioValue: Double, growth: Double) {
        val growthPercentage = if (portfolioValue != 0.0) (growth / portfolioValue) * 100 else 0.0
        binding.portfolioSummary.text = "Portfolio Value: $${String.format("%.2f", portfolioValue)}"
        binding.portfolioGrowth.text = "Growth: +$${String.format("%.2f", growth)} (${String.format("%.2f", growthPercentage)}%)"
    }

    private fun initializeChart(entries: List<Entry> = listOf()) {
        val chartEntries = if (entries.isNotEmpty()) entries else arrayListOf(
            Entry(0f, 10f), Entry(1f, 20f), Entry(2f, 15f), Entry(3f, 25f)
        )
        val dataSet = LineDataSet(chartEntries, "Portfolio Value")
        val lineData = LineData(dataSet)
        binding.portfolioChart.data = lineData
        binding.portfolioChart.invalidate()
    }
}