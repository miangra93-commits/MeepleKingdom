package com.example.meeplekingdom.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.meeplekingdom.databinding.ItemCartBinding
import com.example.meeplekingdom.model.CartItem
import com.example.meeplekingdom.util.Format

/**
 * Líneas del carrito con botones − / + para la cantidad y "Quitar".
 * Las acciones se delegan en los callbacks para que el fragmento hable con Firestore.
 */
class CartAdapter(
    private val onMinus: (CartItem) -> Unit,
    private val onPlus: (CartItem) -> Unit,
    private val onRemove: (CartItem) -> Unit
) : ListAdapter<CartItem, CartAdapter.VH>(DIFF) {

    /** Contenedor de las vistas de una línea (ViewBinding de item_cart). */
    class VH(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /** Pinta una línea del carrito y conecta sus botones. */
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        Glide.with(b.imgCart)
            .load(item.imagenUrl.ifEmpty { null })
            .placeholder(android.R.drawable.ic_menu_gallery)
            .centerCrop()
            .into(b.imgCart)

        b.tvCartNombre.text = item.nombre
        b.tvCartPrecio.text = "${Format.cop(item.precio)} c/u · Subtotal ${Format.cop(item.precio * item.cantidad)}"
        b.tvCartCantidad.text = item.cantidad.toString()

        b.btnMenos.setOnClickListener { onMinus(item) }
        b.btnMas.setOnClickListener { onPlus(item) }
        b.btnQuitar.setOnClickListener { onRemove(item) }
    }

    companion object {
        /** Calcula qué líneas cambiaron para refrescar solo lo necesario. */
        private val DIFF = object : DiffUtil.ItemCallback<CartItem>() {
            override fun areItemsTheSame(old: CartItem, new: CartItem) = old.productId == new.productId
            override fun areContentsTheSame(old: CartItem, new: CartItem) = old == new
        }
    }
}
