package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnIngresar = findViewById<Button>(R.id.btnIngresar)
        val btnHuella = findViewById<Button>(R.id.btnHuella)

        // 1. Lógica para el botón de Iniciar Sesión
        btnIngresar.setOnClickListener {
            val email = etEmail.text.toString().trim().lowercase()
            when {
                email.contains("admin") -> startActivity(Intent(this, AdminActivity::class.java))
                email.contains("vendedor") -> startActivity(Intent(this, SellerActivity::class.java))
                else -> startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }

        // 2. Lógica para ir a Recuperar Contraseña
        findViewById<TextView>(R.id.btnOlvideContrasena).setOnClickListener {
            startActivity(Intent(this, RecoveryActivity::class.java))
        }

        // 3. Lógica para la Biometría Real
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(applicationContext, "Huella reconocida", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Error: $errString", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Ingreso a Meeple Kingdom")
            .setSubtitle("Usa tu huella para entrar")
            .setNegativeButtonText("Usar contraseña")
            .build()

        btnHuella.setOnClickListener {
            val biometricManager = BiometricManager.from(this)
            when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    biometricPrompt.authenticate(promptInfo)
                }
                else -> {
                    Toast.makeText(this, "La biometría no está configurada en este emulador", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}