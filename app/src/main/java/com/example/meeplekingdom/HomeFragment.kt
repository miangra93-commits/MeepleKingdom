package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Buscamos la tarjeta de Catan y le decimos que abra el detalle
        val cardCatan = view.findViewById<View>(R.id.cardCatan)
        cardCatan.setOnClickListener {
            val intent = Intent(activity, ProductDetailActivity::class.java)
            startActivity(intent)
        }

        return view
    }
}