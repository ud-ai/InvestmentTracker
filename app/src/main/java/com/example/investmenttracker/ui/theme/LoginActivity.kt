package com.example.investmenttracker.ui.theme

import com.example.investmenttracker.Dashboard
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.example.investmenttracker.databinding.ActivityLoginBinding
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()

        // Check if user is already logged in
        if (auth.currentUser != null) {
            startActivity(Intent(this, Dashboard::class.java))
            finish()
            return
        }

        // Get the email from signup if available
        val emailFromSignup = intent.getStringExtra("email")
        if (!emailFromSignup.isNullOrEmpty()) {
            binding.etEmail.setText(emailFromSignup)
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            var valid = true
            if (email.isEmpty()) {
                binding.etEmailLayout.error = null
                binding.etPasswordLayout.error = null
                binding.etEmailLayout.error = "Email is required"
                valid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmailLayout.error = "Enter a valid email"
                valid = false
            } else {
                binding.etEmailLayout.error = null
            }

            if (password.isEmpty()) {
                binding.etPasswordLayout.error = "Password is required"
                valid = false
            } else if (password.length < 6) {
                binding.etPasswordLayout.error = "Password must be at least 6 characters"
                valid = false
            } else {
                binding.etPasswordLayout.error = null
            }

            if (!valid) return@setOnClickListener

            // Show loading state
            binding.btnLogin.isEnabled = false
            binding.btnLogin.text = "Logging in..."

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "Login"

                    if (task.isSuccessful) {
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, Dashboard::class.java))
                        finish()
                    } else {
                        binding.etPasswordLayout.error = "Login failed: ${task.exception?.message}"
                    }
                }
        }

        // Clear errors on input change
        binding.etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.etEmailLayout.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.etPasswordLayout.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.tvSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in and update UI accordingly
        val currentUser = auth.currentUser
        if (currentUser != null) {
            startActivity(Intent(this, Dashboard::class.java))
            finish()
        }
    }
}

}
