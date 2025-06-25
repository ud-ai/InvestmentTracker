package com.example.investmenttracker

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class InvestmentTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Enable offline capabilities
        FirebaseDatabase.getInstance().apply {
            setPersistenceEnabled(true)
        }
    }
} 