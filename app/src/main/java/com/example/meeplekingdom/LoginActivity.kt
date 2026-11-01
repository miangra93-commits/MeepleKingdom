package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnIngresar = findViewById<Button>(R.id.btnIngresar)
        val btnHuella = findViewById<Button>(R.id.btnHuella)

        // Lógica para el botón de Iniciar Sesión
        btnIngresar.setOnClickListener {
            val email = etEmail.text.toString().trim().lowercase()

            // Aquí decidimos a dónde ir según el correo
            when {
                email.contains("admin") -> {
                    startActivity(Intent(this, AdminActivity::class.java))
                }
                email.contains("vendedor") -> {
                    startActivity(Intent(this, SellerActivity::class.java))
                }
                else -> {
                    // Si no es admin ni vendedor, es comprador
                    startActivity(Intent(this, MainActivity::class.java))
                }
            }
            finish() // Cierra el login para que no puedan volver atrás
        }

        // Lógica para el botón de Huella (siempre entra como comprador por defecto)
        btnHuella.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}