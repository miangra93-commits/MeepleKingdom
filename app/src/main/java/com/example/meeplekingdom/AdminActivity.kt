package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AdminActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        findViewById<TextView>(R.id.btnAtras).setOnClickListener { finish() }

        // Conectamos los nuevos botones
        findViewById<Button>(R.id.btnGestionUsuarios).setOnClickListener {
            startActivity(Intent(this, AdminUsersActivity::class.java))
        }

        findViewById<Button>(R.id.btnReporteVentas).setOnClickListener {
            startActivity(Intent(this, AdminSalesActivity::class.java))
        }
        findViewById<Button>(R.id.btnModerarProductos).setOnClickListener {
            startActivity(Intent(this, AdminProductsActivity::class.java))
        }
    }
}