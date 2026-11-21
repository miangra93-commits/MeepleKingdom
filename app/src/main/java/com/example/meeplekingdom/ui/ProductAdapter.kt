package com.example.meeplekingdom.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.meeplekingdom.R
import com.example.meeplekingdom.databinding.ItemProductoBinding
import com.example.meeplekingdom.model.Product
import com.example.meeplekingdom.util.Format

/**
 * Adaptador de lista de productos reutilizado por dos pantallas:
 *  - Vendedor ("Mis Productos"): usa [onEdit] y [onDelete].
 *  - Administrador (moderación): usa [onToggleBlock] y [onDelete].
 * Un botón solo se muestra si su callback no es null.
 */
class ProductAdapter(
    private val onEdit: ((Product) -> Unit)? = null,
    private val onDelete: ((Product) -> Unit)? = null,
    private val onToggleBlock: ((Product) -> Unit)? = null
) : ListAdapter<Product, ProductAdapter.VH>(DIFF) {

    /** Contenedor de las vistas de una fila (ViewBinding de item_producto). */
    class VH(val binding: ItemProductoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemProductoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /** Pinta un producto: foto (Glide), datos, estado y botones disponibles. */
    override fun onBindViewHolder(holder: VH, position: Int) {
        val product = getItem(position)
        val b = holder.binding
        val context = b.root.context

        Glide.with(b.imgProducto)
            .load(product.imagenUrl.ifEmpty { null })
            .placeholder(android.R.drawable.ic_menu_gallery)
            .centerCrop()
            .into(b.imgProducto)

        b.tvNombreProducto.text = product.nombre
        b.tvDetallesProducto.text =
            "${Format.cop(product.precio)} · Stock: ${product.stock}\n${product.categoria} · ${product.vendedorNombre}"

        val (estado, color) = when {
            product.bloqueado -> "Bloqueado por moderación" to R.color.soft_red
            !product.activo -> "Oculto" to R.color.gray_text
            else -> "Publicado" to R.color.teal_confirm
        }
        b.tvEstadoProducto.text = estado
        b.tvEstadoProducto.setTextColor(ContextCompat.getColor(context, color))

        b.btnEditarItem.visibility = if (onEdit != null) View.VISIBLE else View.GONE
        b.btnBloquearItem.visibility = if (onToggleBlock != null) View.VISIBLE else View.GONE
        b.btnEliminarItem.visibility = if (onDelete != null) View.VISIBLE else View.GONE
        b.btnBloquearItem.text = if (product.bloqueado) "Desbloquear" else "Bloquear"

        b.btnEditarItem.setOnClickListener { onEdit?.invoke(product) }
        b.btnBloquearItem.setOnClickListener { onToggleBlock?.invoke(product) }
        b.btnEliminarItem.setOnClickListener { onDelete?.invoke(product) }
    }

    companion object {
        /** Calcula qué filas cambiaron para refrescar solo lo necesario. */
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(old: Product, new: Product) = old.id == new.id
            override fun areContentsTheSame(old: Product, new: Product) = old == new
        }
    }
}
