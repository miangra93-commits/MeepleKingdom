package com.example.meeplekingdom

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.data.UserRepository
import com.example.meeplekingdom.databinding.ActivityAdminUsersBinding
import com.example.meeplekingdom.model.AppUser
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.ui.UserAdapter
import com.example.meeplekingdom.util.Navigator
import com.google.firebase.firestore.ListenerRegistration

/** Lista de usuarios para el Administrador (READ en tiempo real) con acciones de editar y eliminar. */
class AdminUsersActivity : AppCompatActivity() {
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.ADMIN)) return
        val binding = ActivityAdminUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = UserAdapter(
            currentUid = Session.user?.uid.orEmpty(),
            onEdit = { user -> startActivity(UserFormActivity.newIntent(this, user)) },
            onDelete = { user -> confirmarEliminar(user) }
        )
        binding.rvUsuarios.layoutManager = LinearLayoutManager(this)
        binding.rvUsuarios.adapter = adapter

        binding.btnVolverUsers.setOnClickListener { finish() }
        binding.btnCrearUsuario.setOnClickListener {
            startActivity(UserFormActivity.newIntent(this))
        }

        // La lista se actualiza sola cuando cambia Firestore (crear, editar o eliminar).
        listener = UserRepository.listen(
            onChange = { users ->
                adapter.submitList(users)
                binding.tvVacioUsuarios.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
                binding.tvVacioUsuarios.text = "No hay usuarios registrados"
            },
            onError = { mensaje ->
                binding.tvVacioUsuarios.visibility = View.VISIBLE
                binding.tvVacioUsuarios.text = mensaje
            }
        )
    }

    /** Pide confirmación antes de eliminar el perfil de un usuario. */
    private fun confirmarEliminar(user: AppUser) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar usuario")
            .setMessage("¿Eliminar a ${user.nombre}? Perderá el acceso a la aplicación.")
            .setPositiveButton("Eliminar") { _, _ ->
                UserRepository.delete(
                    user.uid,
                    onSuccess = { Toast.makeText(this, "Usuario eliminado", Toast.LENGTH_SHORT).show() },
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
