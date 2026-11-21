package com.example.meeplekingdom

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SellerProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seller_profile)

        // Busca el botón de volver y le dice qué hacer
        findViewById<TextView>(R.id.btnVolverProfile).setOnClickListener { finish() }
    }
}