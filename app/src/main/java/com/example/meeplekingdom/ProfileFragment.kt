package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class ProfileFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflamos el diseño
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Buscamos los botones
        val btnVendedor = view.findViewById<Button>(R.id.btnRolVendedor)
        val btnAdmin = view.findViewById<Button>(R.id.btnRolAdmin)

        // Acción del botón Vendedor
        btnVendedor.setOnClickListener {
            val intent = Intent(activity, SellerActivity::class.java)
            startActivity(intent)
        }

        // Acción del botón Admin
        btnAdmin.setOnClickListener {
            val intent = Intent(activity, AdminActivity::class.java)
            startActivity(intent)
        }

        return view
    }
}