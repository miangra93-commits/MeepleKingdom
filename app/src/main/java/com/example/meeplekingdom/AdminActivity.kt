package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.meeplekingdom.data.UserRepository
import com.example.meeplekingdom.databinding.ActivityAdminBinding
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.util.Navigator
import com.google.firebase.firestore.ListenerRegistration

/** Panel principal del Administrador: métricas y accesos a usuarios, ventas y moderación. */
class AdminActivity : AppCompatActivity() {
    private var usersListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.ADMIN)) return
        val binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAtras.setOnClickListener { Navigator.logout(this) }
        binding.btnGestionUsuarios.setOnClickListener {
            startActivity(Intent(this, AdminUsersActivity::class.java))
        }
        binding.btnReporteVentas.setOnClickListener {
            startActivity(Intent(this, AdminSalesActivity::class.java))
        }
        binding.btnModerarProductos.setOnClickListener {
            startActivity(Intent(this, AdminProductsActivity::class.java))
        }

        // Contador de usuarios en tiempo real.
        usersListener = UserRepository.listen(
            onChange = { users -> binding.tvTotalUsuarios.text = users.size.toString() },
            onError = { }
        )
    }

    /** Deja de escuchar Firestore al cerrar la pantalla. */
    override fun onDestroy() {
        usersListener?.remove()
        super.onDestroy()
    }
}
