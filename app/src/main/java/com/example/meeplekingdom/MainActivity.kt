package com.example.meeplekingdom

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.util.Navigator
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Contenedor principal del rol Comprador: navegación inferior entre Inicio, Catálogo, Carrito, Pedidos y Perfil. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.COMPRADOR)) return
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // Solo cargamos Inicio la primera vez; al rotar, Android restaura el fragmento actual.
        if (savedInstanceState == null) loadFragment(HomeFragment())

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { loadFragment(HomeFragment()); true }
                R.id.nav_search -> { loadFragment(CatalogFragment()); true }
                R.id.nav_cart -> { loadFragment(CartFragment()); true }
                R.id.nav_orders -> { loadFragment(OrdersFragment()); true }
                R.id.nav_profile -> { loadFragment(ProfileFragment()); true }
                else -> false
            }
        }
        // Tocar la pestaña ya seleccionada no recarga el fragmento.
        bottomNav.setOnItemReselectedListener { }
    }

    /** Reemplaza el fragmento visible. */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
