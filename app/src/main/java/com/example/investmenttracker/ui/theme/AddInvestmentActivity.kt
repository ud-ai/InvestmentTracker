package com.example.investmenttracker.ui.theme

import android.app.DatePickerDialog
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.investmenttracker.databinding.ActivityAddInvestmentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Calendar
import kotlin.math.pow
import com.google.firebase.database.Transaction
import com.google.firebase.database.MutableData

class AddInvestmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddInvestmentBinding
    private lateinit var auth: FirebaseAuth
    private var assetList: List<Coin> = emptyList()
    private var assetNames: List<String> = emptyList()
    private lateinit var progressBar: ProgressBar
    private var latestUnitPrice: Double? = null
    private lateinit var database: FirebaseDatabase
    private var isConnected = false
    private var connectionListener: ValueEventListener? = null
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 2000L // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddInvestmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        try {
            auth = FirebaseAuth.getInstance()
            database = FirebaseDatabase.getInstance()
            database.setPersistenceEnabled(true)  // Enable offline capabilities
            
            // Check connection state
            val connectedRef = database.getReference(".info/connected")
            connectionListener = connectedRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    isConnected = snapshot.getValue(Boolean::class.java) ?: false
                    runOnUiThread {
                        binding.btnSaveInvestment.isEnabled = isConnected
                        if (!isConnected) {
                            Toast.makeText(this@AddInvestmentActivity, 
                                "Waiting for connection...", 
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    isConnected = false
                    runOnUiThread {
                        binding.btnSaveInvestment.isEnabled = false
                        Toast.makeText(this@AddInvestmentActivity,
                            "Connection error: ${error.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }
            })
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initialize Firebase: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Initialize UI components
        progressBar = binding.progressBar
        progressBar.visibility = View.GONE

        // Set up the save button
        binding.btnSaveInvestment.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "No connection. Please check your internet and try again.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            retryCount = 0  // Reset retry count
            saveInvestmentToFirebase()
        }

        // Fetch asset data from API on activity start
        fetchAssetData()

        // Set up date picker
        binding.etPurchaseDate.setOnClickListener {
            showDatePicker()
        }

        // Set up suggestion selection listener
        binding.etAssetType.setOnItemClickListener { parent, view, position, id ->
            val selectedName = parent.getItemAtPosition(position) as String
            updatePriceForSelectedAsset(selectedName)
        }

        // Set up search filter for assets
        binding.etAssetType.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().toLowerCase()
                val filteredNames = assetNames.filter { it.toLowerCase().contains(query) }
                val adapter = ArrayAdapter(this@AddInvestmentActivity, android.R.layout.simple_dropdown_item_1line, filteredNames)
                binding.etAssetType.setAdapter(adapter)
            }
        })

        // Listen for changes in quantity to update total price
        binding.etQuantity.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateTotalPrice()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove connection listener to prevent memory leaks
        connectionListener?.let {
            database.getReference(".info/connected").removeEventListener(it)
        }
    }

    private fun fetchAssetData() {
        progressBar.visibility = View.VISIBLE
        val call: Call<List<Coin>> = ApiClient.retrofit.getCoinMarkets(
            vsCurrency = "usd",
            perPage = 100,
            order = "market_cap_desc"
        )
        call.enqueue(object : Callback<List<Coin>> {
            override fun onResponse(call: Call<List<Coin>>, response: Response<List<Coin>>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    assetList = response.body() ?: emptyList()
                    assetNames = assetList.mapNotNull { it.name }
                    val adapter = ArrayAdapter(this@AddInvestmentActivity, android.R.layout.simple_dropdown_item_1line, assetNames)
                    binding.etAssetType.setAdapter(adapter)
                } else {
                    Toast.makeText(this@AddInvestmentActivity, "Failed to fetch asset data: ${response.code()}", Toast.LENGTH_SHORT).show()
                    showRetryDialog()
                }
            }

            override fun onFailure(call: Call<List<Coin>>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@AddInvestmentActivity, "Failed to fetch asset data: ${t.message}", Toast.LENGTH_SHORT).show()
                showRetryDialog()
            }
        })
    }

    private fun showRetryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage("Failed to fetch asset data. Would you like to retry?")
            .setPositiveButton("Retry") { _, _ -> fetchAssetData() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePriceForSelectedAsset(assetName: String) {
        progressBar.visibility = View.VISIBLE
        val selectedAsset = assetList.find { it.name == assetName }
        if (selectedAsset == null) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Asset not found", Toast.LENGTH_SHORT).show()
            return
        }
        val call: Call<Map<String, Map<String, Double>>> = ApiClient.retrofit.getPrice(
            ids = selectedAsset.id,
            vsCurrencies = "usd"
        )
        call.enqueue(object : Callback<Map<String, Map<String, Double>>> {
            override fun onResponse(call: Call<Map<String, Map<String, Double>>>, response: Response<Map<String, Map<String, Double>>>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    val price = response.body()?.get(selectedAsset.id)?.get("usd")
                    if (price != null) {
                        latestUnitPrice = price
                        updateTotalPrice()
                    } else {
                        latestUnitPrice = null
                        binding.etPurchasePrice.setText("")
                    }
                } else {
                    Toast.makeText(this@AddInvestmentActivity, "Failed to fetch price: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Map<String, Map<String, Double>>>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@AddInvestmentActivity, "Failed to fetch price: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateTotalPrice() {
        val quantityText = binding.etQuantity.text.toString()
        val quantity = quantityText.toDoubleOrNull()
        if (latestUnitPrice != null && quantity != null) {
            val total = latestUnitPrice!! * quantity
            binding.etPurchasePrice.setText(total.toString())
        } else if (latestUnitPrice != null) {
            binding.etPurchasePrice.setText("")
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val date = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
            binding.etPurchaseDate.setText(date)
        }, year, month, day).show()
    }

    private fun saveInvestmentToFirebase() {
        try {
            if (!isConnected) {
                Toast.makeText(this, "No connection. Please check your internet and try again.", Toast.LENGTH_LONG).show()
                return
            }

            val assetType = binding.etAssetType.text.toString().trim()
            val quantity = binding.etQuantity.text.toString().trim()
            val price = binding.etPurchasePrice.text.toString().trim()
            val date = binding.etPurchaseDate.text.toString().trim()

            // Validate all fields
            if (assetType.isEmpty()) {
                Toast.makeText(this, "Please select an asset", Toast.LENGTH_SHORT).show()
                return
            }
            if (quantity.isEmpty()) {
                Toast.makeText(this, "Please enter quantity", Toast.LENGTH_SHORT).show()
                return
            }
            if (price.isEmpty()) {
                Toast.makeText(this, "Please enter price", Toast.LENGTH_SHORT).show()
                return
            }
            if (date.isEmpty()) {
                Toast.makeText(this, "Please select purchase date", Toast.LENGTH_SHORT).show()
                return
            }

            // Validate numeric values
            val quantityValue = quantity.toDoubleOrNull()
            val priceValue = price.toDoubleOrNull()

            if (quantityValue == null || quantityValue <= 0) {
                Toast.makeText(this, "Please enter a valid quantity", Toast.LENGTH_SHORT).show()
                return
            }
            if (priceValue == null || priceValue <= 0) {
                Toast.makeText(this, "Please enter a valid price", Toast.LENGTH_SHORT).show()
                return
            }

            // Show progress
            progressBar.visibility = View.VISIBLE
            binding.btnSaveInvestment.isEnabled = false

            val investment = Investment(
                assetType = assetType,
                quantity = quantityValue,
                price = priceValue,
                purchaseDate = date
            )

            val userId = auth.currentUser?.uid
            if (userId == null) {
                progressBar.visibility = View.GONE
                binding.btnSaveInvestment.isEnabled = true
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
                return
            }

            val investmentsRef = database.getReference("users").child(userId).child("investments")
            val newInvestmentRef = investmentsRef.push()

            // Try to save with retry
            newInvestmentRef.setValue(investment)
                .addOnSuccessListener {
                    progressBar.visibility = View.GONE
                    binding.btnSaveInvestment.isEnabled = true
                    Toast.makeText(this@AddInvestmentActivity, 
                        "Investment saved successfully!", 
                        Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    if (retryCount < maxRetries) {
                        retryCount++
                        // Wait before retrying with exponential backoff
                        val delay = retryDelayMs * (2.0.pow(retryCount - 1)).toLong()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            saveInvestmentToFirebase()
                        }, delay)
                        
                        Toast.makeText(this@AddInvestmentActivity,
                            "Retrying save (Attempt $retryCount of $maxRetries)...",
                            Toast.LENGTH_SHORT).show()
                    } else {
                        progressBar.visibility = View.GONE
                        binding.btnSaveInvestment.isEnabled = true
                        val errorMessage = when {
                            e.message?.contains("connection") == true -> 
                                "Connection error. Please check your internet and try again."
                            e.message?.contains("permission") == true -> 
                                "Permission denied. Please check your Firebase rules."
                            else -> "Failed to save: ${e.message}"
                        }
                        Toast.makeText(this@AddInvestmentActivity, 
                            errorMessage, 
                            Toast.LENGTH_LONG).show()
                    }
                }

        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            binding.btnSaveInvestment.isEnabled = true
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
