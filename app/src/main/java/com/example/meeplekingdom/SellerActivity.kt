package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.meeplekingdom.data.OrderRepository
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.databinding.ActivitySellerBinding
import com.example.meeplekingdom.model.OrderStatus
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.util.Format
import com.example.meeplekingdom.util.Navigator
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

/** Panel principal del rol Vendedor: acceso a sus productos, pedidos y perfil de tienda. */
class SellerActivity : AppCompatActivity() {
    private var ordersListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.VENDEDOR)) return
        val binding = ActivitySellerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAtras.setOnClickListener { Navigator.logout(this) }
        binding.btnMisProductos.setOnClickListener {
            startActivity(Intent(this, SellerProductsActivity::class.java))
        }
        binding.btnHistorialPedidos.setOnClickListener {
            startActivity(Intent(this, SellerOrdersActivity::class.java))
        }
        binding.btnPerfilTienda.setOnClickListener {
            startActivity(Intent(this, SellerProfileActivity::class.java))
        }

        // Métricas reales: ventas del mes en curso y pedidos pagados pendientes de enviar.
        ordersListener = OrderRepository.listenBySeller(
            Session.user?.uid.orEmpty(),
            onChange = { pedidos ->
                val inicioMes = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val ventasMes = pedidos
                    .filter { it.creadoEn >= inicioMes && it.estado in listOf(OrderStatus.PAGADO, OrderStatus.ENVIADO, OrderStatus.ENTREGADO) }
                    .sumOf { it.total }
                binding.tvVentasMes.text = Format.cop(ventasMes)
                binding.tvPedidosNuevos.text = "${pedidos.count { it.estado == OrderStatus.PAGADO }} Nuevos"
            },
            onError = { }
        )
    }

    /** Deja de escuchar Firestore al cerrar la pantalla. */
    override fun onDestroy() {
        ordersListener?.remove()
        super.onDestroy()
    }
}
