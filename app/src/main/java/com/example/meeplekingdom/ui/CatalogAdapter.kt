package com.example.meeplekingdom.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.meeplekingdom.databinding.ItemCatalogoBinding
import com.example.meeplekingdom.model.Product
import com.example.meeplekingdom.util.Format

/** Tarjetas de producto del catálogo y de la pantalla de inicio (se usa con una cuadrícula de 2 columnas). */
class CatalogAdapter(
    private val onClick: (Product) -> Unit
) : ListAdapter<Product, CatalogAdapter.VH>(DIFF) {

    /** Contenedor de las vistas de una tarjeta (ViewBinding de item_catalogo). */
    class VH(val binding: ItemCatalogoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCatalogoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /** Pinta foto, nombre, precio y disponibilidad; al tocar la tarjeta se abre el detalle. */
    override fun onBindViewHolder(holder: VH, position: Int) {
        val product = getItem(position)
        val b = holder.binding

        Glide.with(b.imgCatalogo)
            .load(product.imagenUrl.ifEmpty { null })
            .placeholder(android.R.drawable.ic_menu_gallery)
            .centerCrop()
            .into(b.imgCatalogo)

        b.tvCatalogoNombre.text = product.nombre
        b.tvCatalogoPrecio.text = Format.cop(product.precio)
        b.tvCatalogoInfo.text =
            if (product.stock > 0) "${product.categoria} · ${product.stock} disponibles" else "Agotado"
        b.root.setOnClickListener { onClick(product) }
    }

    companion object {
        /** Calcula qué tarjetas cambiaron para refrescar solo lo necesario. */
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(old: Product, new: Product) = old.id == new.id
            override fun areContentsTheSame(old: Product, new: Product) = old == new
        }
    }
}
