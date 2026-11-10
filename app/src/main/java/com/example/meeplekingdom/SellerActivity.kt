package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SellerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seller)

        // Botón para volver al login
        findViewById<TextView>(R.id.btnAtras).setOnClickListener {
            finish()
        }

        // Botón de Mis Productos
        findViewById<Button>(R.id.btnMisProductos).setOnClickListener {
            startActivity(Intent(this, SellerProductsActivity::class.java))
        }

        // Botón de Historial de Pedidos
        findViewById<Button>(R.id.btnHistorialPedidos).setOnClickListener {
            startActivity(Intent(this, SellerOrdersActivity::class.java))
        }

        // Botón del Perfil de la Tienda
        findViewById<Button>(R.id.btnPerfilTienda).setOnClickListener {
            startActivity(Intent(this, SellerProfileActivity::class.java))
        }
    }
}