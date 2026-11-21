package com.example.meeplekingdom

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meeplekingdom.data.OrderRepository
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.databinding.ActivitySellerOrdersBinding
import com.example.meeplekingdom.model.Order
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.ui.OrderAdapter
import com.example.meeplekingdom.util.MapsHelper
import com.example.meeplekingdom.util.Navigator
import com.google.firebase.firestore.ListenerRegistration

/** Pedidos recibidos por el vendedor: puede marcarlos como enviados/entregados o cancelarlos. */
class SellerOrdersActivity : AppCompatActivity() {
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.VENDEDOR)) return
        val binding = ActivitySellerOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = OrderAdapter(
            OrderAdapter.Mode.SELLER,
            onChangeStatus = { pedido, estado ->
                OrderRepository.updateStatus(
                    pedido.id, estado,
                    onSuccess = { Toast.makeText(this, "Pedido actualizado", Toast.LENGTH_SHORT).show() },
                    onError = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                )
            },
            onCancel = { pedido -> confirmarCancelacion(pedido) },
            onMap = { pedido ->
                MapsHelper.open(this, pedido.latitud, pedido.longitud, "Entrega ${pedido.compradorNombre}")
            }
        )
        binding.rvPedidosVendedor.layoutManager = LinearLayoutManager(this)
        binding.rvPedidosVendedor.adapter = adapter
        binding.btnVolverOrders.setOnClickListener { finish() }

        listener = OrderRepository.listenBySeller(
            Session.user?.uid.orEmpty(),
            onChange = { pedidos ->
                adapter.submitList(pedidos)
                binding.tvVacioPedidosVendedor.visibility = if (pedidos.isEmpty()) View.VISIBLE else View.GONE
                binding.tvVacioPedidosVendedor.text = "Aún no has recibido pedidos"
            },
            onError = { mensaje ->
                binding.tvVacioPedidosVendedor.visibility = View.VISIBLE
                binding.tvVacioPedidosVendedor.text = mensaje
            }
        )
    }

    /** Pide confirmación antes de cancelar (se devuelve el stock al inventario). */
    private fun confirmarCancelacion(pedido: Order) {
        AlertDialog.Builder(this)
            .setTitle("Cancelar pedido")
            .setMessage("Las unidades del pedido volverán a tu inventario. ¿Cancelar?")
            .setPositiveButton("Sí, cancelar") { _, _ ->
                OrderRepository.cancel(
                    pedido,
                    onSuccess = { Toast.makeText(this, "Pedido cancelado", Toast.LENGTH_SHORT).show() },
                    onError = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
                )
            }
            .setNegativeButton("Volver", null)
            .show()
    }

    /** Deja de escuchar Firestore al cerrar la pantalla. */
    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}
