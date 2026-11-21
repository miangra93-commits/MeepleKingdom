package com.example.meeplekingdom

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meeplekingdom.data.OrderRepository
import com.example.meeplekingdom.databinding.ActivityAdminSalesBinding
import com.example.meeplekingdom.model.Order
import com.example.meeplekingdom.model.OrderStatus
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.ui.OrderAdapter
import com.example.meeplekingdom.util.Format
import com.example.meeplekingdom.util.MapsHelper
import com.example.meeplekingdom.util.Navigator
import com.google.firebase.firestore.ListenerRegistration

/**
 * Reporte de ventas del Administrador, calculado en tiempo real desde la colección `orders`:
 * ventas totales, ticket promedio, ventas por vendedor y lista de todos los pedidos
 * (con opción de cancelar los abiertos para liberar su stock).
 */
class AdminSalesActivity : AppCompatActivity() {
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.ADMIN)) return
        val binding = ActivityAdminSalesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = OrderAdapter(
            OrderAdapter.Mode.ADMIN,
            onCancel = { pedido -> confirmarCancelacion(pedido) },
            onMap = { pedido ->
                MapsHelper.open(this, pedido.latitud, pedido.longitud, "Entrega ${pedido.compradorNombre}")
            }
        )
        binding.rvVentas.layoutManager = LinearLayoutManager(this)
        binding.rvVentas.adapter = adapter
        binding.btnVolverVentas.setOnClickListener { finish() }

        listener = OrderRepository.listenAll(
            onChange = { pedidos ->
                adapter.submitList(pedidos)
                binding.tvVacioVentas.visibility = if (pedidos.isEmpty()) View.VISIBLE else View.GONE
                binding.tvVacioVentas.text = "Aún no hay pedidos"

                // Una venta cuenta cuando el pedido ya fue pagado (aunque luego se envíe o entregue).
                val ventas = pedidos.filter {
                    it.estado == OrderStatus.PAGADO || it.estado == OrderStatus.ENVIADO || it.estado == OrderStatus.ENTREGADO
                }
                val total = ventas.sumOf { it.total }
                val ticket = if (ventas.isEmpty()) 0.0 else total / ventas.size
                binding.tvVentasTotales.text = "Ventas: ${Format.cop(total)}"
                binding.tvResumenVentas.text =
                    "${ventas.size} pedidos pagados · Ticket promedio ${Format.cop(ticket)}\n" +
                        "${pedidos.count { it.estado == OrderStatus.PENDIENTE_PAGO }} pendientes de pago · " +
                        "${pedidos.count { it.estado == OrderStatus.CANCELADO }} cancelados"

                val porVendedor = ventas.groupBy { it.vendedorNombre }
                    .map { (nombre, lista) -> Triple(nombre, lista.sumOf { it.total }, lista.size) }
                    .sortedByDescending { it.second }
                binding.tvVentasPorVendedor.text =
                    if (porVendedor.isEmpty()) "Sin ventas por vendedor todavía"
                    else "Ventas por vendedor:\n" + porVendedor.joinToString("\n") {
                        "• ${it.first}: ${Format.cop(it.second)} (${it.third})"
                    }
            },
            onError = { mensaje ->
                binding.tvVacioVentas.visibility = View.VISIBLE
                binding.tvVacioVentas.text = mensaje
            }
        )
    }

    /** Pide confirmación antes de cancelar un pedido (se devuelve el stock al inventario). */
    private fun confirmarCancelacion(pedido: Order) {
        AlertDialog.Builder(this)
            .setTitle("Cancelar pedido")
            .setMessage("Las unidades volverán al inventario del vendedor. ¿Cancelar este pedido?")
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
