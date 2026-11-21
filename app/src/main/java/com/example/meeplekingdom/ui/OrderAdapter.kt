package com.example.meeplekingdom.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meeplekingdom.R
import com.example.meeplekingdom.databinding.ItemOrderBinding
import com.example.meeplekingdom.model.Order
import com.example.meeplekingdom.model.OrderStatus
import com.example.meeplekingdom.util.Format

/**
 * Lista de pedidos reutilizada por tres roles:
 *  - [Mode.BUYER]: consulta el estado y puede pagar los pedidos pendientes ([onPay]).
 *  - [Mode.SELLER]: avanza el estado (enviar / entregar) o cancela, y ve la entrega en el mapa ([onMap]).
 *  - [Mode.ADMIN]: ve todos los pedidos y puede cancelar los abiertos para liberar su stock.
 *
 * @param onChangeStatus recibe el pedido y el nuevo estado elegido por el vendedor.
 * @param onCancel       se invoca al pulsar "Cancelar pedido".
 * @param onPay          se invoca cuando el comprador pulsa "Pagar ahora".
 * @param onMap          se invoca al pulsar "Ver en mapa" (solo si el pedido tiene coordenadas).
 */
class OrderAdapter(
    private val mode: Mode,
    private val onChangeStatus: (Order, String) -> Unit = { _, _ -> },
    private val onCancel: (Order) -> Unit = { },
    private val onPay: (Order) -> Unit = { },
    private val onMap: (Order) -> Unit = { }
) : ListAdapter<Order, OrderAdapter.VH>(DIFF) {

    /** Quién está viendo la lista; define qué datos y botones se muestran. */
    enum class Mode { BUYER, SELLER, ADMIN }

    /** Contenedor de las vistas de un pedido (ViewBinding de item_order). */
    class VH(val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /** Pinta un pedido y habilita las acciones que correspondan a su estado y al rol. */
    override fun onBindViewHolder(holder: VH, position: Int) {
        val order = getItem(position)
        val b = holder.binding
        val context = b.root.context

        b.tvOrderTitulo.text = "Pedido #${order.id.takeLast(6).uppercase()}"
        b.tvOrderEstado.text = OrderStatus.etiqueta(order.estado)
        val colorEstado = when (order.estado) {
            OrderStatus.CANCELADO -> R.color.soft_red
            OrderStatus.PENDIENTE_PAGO -> R.color.orange_cta
            OrderStatus.ENTREGADO -> R.color.gray_text
            else -> R.color.teal_confirm
        }
        b.tvOrderEstado.setTextColor(ContextCompat.getColor(context, colorEstado))

        b.tvOrderItems.text = order.items.joinToString("\n") { "${it.cantidad} × ${it.nombre}" }
        val pago = if (order.metodoPago.isNotEmpty()) " · Pago: ${order.metodoPago}" else ""
        b.tvOrderTotal.text = "Total: ${Format.cop(order.total)}$pago"
        val entrega = "Entrega: ${order.direccion}"
        b.tvOrderExtra.text = when (mode) {
            Mode.BUYER -> "Vendedor: ${order.vendedorNombre} · ${Format.fecha(order.creadoEn)}\n$entrega"
            Mode.SELLER -> "Comprador: ${order.compradorNombre} (${order.compradorEmail}) · ${Format.fecha(order.creadoEn)}\n$entrega"
            Mode.ADMIN -> "${order.compradorNombre} → ${order.vendedorNombre} · ${Format.fecha(order.creadoEn)}\n$entrega"
        }

        // Qué botones corresponden según el rol y el estado del pedido.
        var textoAccion: String? = null
        var accion: () -> Unit = { }
        var puedeCancelar = false
        when (mode) {
            Mode.BUYER -> if (order.estado == OrderStatus.PENDIENTE_PAGO) {
                textoAccion = "Pagar ahora"
                accion = { onPay(order) }
            }
            Mode.SELLER -> when (order.estado) {
                OrderStatus.PENDIENTE_PAGO -> puedeCancelar = true
                OrderStatus.PAGADO -> {
                    textoAccion = "Marcar enviado"
                    accion = { onChangeStatus(order, OrderStatus.ENVIADO) }
                    puedeCancelar = true
                }
                OrderStatus.ENVIADO -> {
                    textoAccion = "Marcar entregado"
                    accion = { onChangeStatus(order, OrderStatus.ENTREGADO) }
                }
            }
            Mode.ADMIN ->
                puedeCancelar = order.estado == OrderStatus.PENDIENTE_PAGO || order.estado == OrderStatus.PAGADO
        }
        val verMapa = mode != Mode.BUYER && (order.latitud != 0.0 || order.longitud != 0.0)

        b.btnOrderAccion.visibility = if (textoAccion != null) View.VISIBLE else View.GONE
        b.btnOrderAccion.text = textoAccion.orEmpty()
        b.btnOrderCancelar.visibility = if (puedeCancelar) View.VISIBLE else View.GONE
        b.btnOrderMapa.visibility = if (verMapa) View.VISIBLE else View.GONE
        b.filaAcciones.visibility =
            if (textoAccion != null || puedeCancelar || verMapa) View.VISIBLE else View.GONE

        b.btnOrderAccion.setOnClickListener { accion() }
        b.btnOrderCancelar.setOnClickListener { onCancel(order) }
        b.btnOrderMapa.setOnClickListener { onMap(order) }
    }

    companion object {
        /** Calcula qué pedidos cambiaron para refrescar solo lo necesario. */
        private val DIFF = object : DiffUtil.ItemCallback<Order>() {
            override fun areItemsTheSame(old: Order, new: Order) = old.id == new.id
            override fun areContentsTheSame(old: Order, new: Order) = old == new
        }
    }
}
