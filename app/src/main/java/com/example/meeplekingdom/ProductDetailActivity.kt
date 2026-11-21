package com.example.meeplekingdom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.meeplekingdom.data.CartRepository
import com.example.meeplekingdom.data.ProductRepository
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.databinding.ActivityProductDetailBinding
import com.example.meeplekingdom.model.Product
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.util.Format
import com.example.meeplekingdom.util.Navigator

/** Detalle de un producto (recibe su id por Intent) con el botón para añadirlo al carrito. */
class ProductDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductDetailBinding
    private var product: Product? = null

    companion object {
        private const val EXTRA_ID = "productId"

        /** Crea el Intent para abrir el detalle del producto [productId]. */
        fun newIntent(context: Context, productId: String): Intent =
            Intent(context, ProductDetailActivity::class.java).putExtra(EXTRA_ID, productId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.COMPRADOR)) return
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVolverDetalle.setOnClickListener { finish() }
        binding.btnAnadirCarrito.setOnClickListener { anadirAlCarrito() }

        val id = intent.getStringExtra(EXTRA_ID)
        if (id == null) {
            finish()
            return
        }
        ProductRepository.get(
            id,
            onSuccess = { loaded ->
                if (loaded == null || !loaded.activo || loaded.bloqueado) {
                    Toast.makeText(this, "Este producto ya no está disponible", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    mostrar(loaded)
                }
            },
            onError = { mensaje ->
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                finish()
            }
        )
    }

    /** Muestra los datos del producto y habilita el botón si hay stock. */
    private fun mostrar(p: Product) {
        product = p
        Glide.with(this).load(p.imagenUrl.ifEmpty { null })
            .placeholder(android.R.drawable.ic_menu_gallery).centerCrop().into(binding.imgDetalle)
        binding.tvDetalleNombre.text = p.nombre
        binding.tvDetallePrecio.text = Format.cop(p.precio)
        binding.tvDetalleInfo.text =
            "${p.categoria} · Vendido por ${p.vendedorNombre}\n" +
                if (p.stock > 0) "${p.stock} unidades disponibles" else "Agotado"
        binding.tvDetalleDescripcion.text = p.descripcion
        binding.btnAnadirCarrito.isEnabled = p.stock > 0
        if (p.stock <= 0) binding.btnAnadirCarrito.text = "Agotado"
    }

    /** Añade una unidad al carrito del usuario en Firestore. */
    private fun anadirAlCarrito() {
        val p = product ?: return
        val uid = Session.user?.uid ?: return
        binding.btnAnadirCarrito.isEnabled = false
        CartRepository.add(
            uid, p,
            onSuccess = {
                binding.btnAnadirCarrito.isEnabled = true
                Toast.makeText(this, "Añadido al carrito", Toast.LENGTH_SHORT).show()
            },
            onError = { mensaje ->
                binding.btnAnadirCarrito.isEnabled = true
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
            }
        )
    }
}
