package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class CartFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_cart, container, false)

        val btnIrAPagar = view.findViewById<Button>(R.id.btnIrAPagar)
        btnIrAPagar.setOnClickListener {
            // Abre la pantalla de pagos
            startActivity(Intent(activity, PaymentActivity::class.java))
        }

        return view
    }
}