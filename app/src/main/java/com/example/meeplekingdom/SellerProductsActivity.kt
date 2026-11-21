package com.example.meeplekingdom

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meeplekingdom.data.ProductRepository
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.databinding.ActivitySellerProductsBinding
import com.example.meeplekingdom.model.Product
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.ui.ProductAdapter
import com.example.meeplekingdom.util.Navigator
import com.google.firebase.firestore.ListenerRegistration

/** "Mis Productos" del Vendedor: lista en tiempo real de sus productos con editar y eliminar. */
class SellerProductsActivity : AppCompatActivity() {
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.VENDEDOR)) return
        val binding = ActivitySellerProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = ProductAdapter(
            onEdit = { product -> startActivity(ProductFormActivity.newIntent(this, product.id)) },
            onDelete = { product -> confirmarEliminar(product) }
        )
        binding.rvProductos.layoutManager = LinearLayoutManager(this)
        binding.rvProductos.adapter = adapter

        binding.btnVolverProd.setOnClickListener { finish() }
        binding.btnCrearProducto.setOnClickListener {
            startActivity(ProductFormActivity.newIntent(this))
        }

        val sellerId = Session.user?.uid.orEmpty()
        listener = ProductRepository.listenBySeller(
            sellerId,
            onChange = { products ->
                adapter.submitList(products)
                binding.tvVacioProductos.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
                binding.tvVacioProductos.text = "Aún no has publicado productos"
            },
            onError = { mensaje ->
                binding.tvVacioProductos.visibility = View.VISIBLE
                binding.tvVacioProductos.text = mensaje
            }
        )
    }

    /** Pide confirmación antes de eliminar el producto y su foto. */
    private fun confirmarEliminar(product: Product) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar producto")
            .setMessage("¿Eliminar \"${product.nombre}\"? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                ProductRepository.delete(
                    product,
                    onSuccess = { Toast.makeText(this, "Producto eliminado", Toast.LENGTH_SHORT).show() },
                    onError = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Deja de escuchar Firestore al cerrar la pantalla. */
    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}
