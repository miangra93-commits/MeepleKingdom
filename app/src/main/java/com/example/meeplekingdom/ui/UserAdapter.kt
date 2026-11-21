package com.example.meeplekingdom.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meeplekingdom.R
import com.example.meeplekingdom.databinding.ItemUserBinding
import com.example.meeplekingdom.model.AppUser
import com.example.meeplekingdom.model.Roles

/**
 * Adaptador de la lista de usuarios del administrador.
 *
 * @param currentUid uid del administrador en sesión: no puede eliminarse a sí mismo.
 * @param onEdit     se invoca al pulsar "Editar".
 * @param onDelete   se invoca al pulsar "Eliminar".
 */
class UserAdapter(
    private val currentUid: String,
    private val onEdit: (AppUser) -> Unit,
    private val onDelete: (AppUser) -> Unit
) : ListAdapter<AppUser, UserAdapter.VH>(DIFF) {

    /** Contenedor de las vistas de una fila (ViewBinding de item_user). */
    class VH(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /** Pinta los datos de un usuario en su fila y conecta los botones. */
    override fun onBindViewHolder(holder: VH, position: Int) {
        val user = getItem(position)
        val b = holder.binding
        val context = b.root.context

        b.tvUserNombre.text = user.nombre.ifEmpty { "(sin nombre)" }
        b.tvUserEmail.text = user.email
        b.tvUserDetalle.text = "${Roles.etiqueta(user.rol)} · ${if (user.activo) "Activo" else "Desactivado"}"
        b.tvUserDetalle.setTextColor(
            ContextCompat.getColor(context, if (user.activo) R.color.teal_confirm else R.color.soft_red)
        )

        b.btnEditarUser.setOnClickListener { onEdit(user) }
        b.btnEliminarUser.setOnClickListener { onDelete(user) }
        b.btnEliminarUser.visibility = if (user.uid == currentUid) View.GONE else View.VISIBLE
    }

    companion object {
        /** Calcula qué filas cambiaron para animar y refrescar solo lo necesario. */
        private val DIFF = object : DiffUtil.ItemCallback<AppUser>() {
            override fun areItemsTheSame(old: AppUser, new: AppUser) = old.uid == new.uid
            override fun areContentsTheSame(old: AppUser, new: AppUser) = old == new
        }
    }
}
