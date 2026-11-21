package com.example.meeplekingdom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.data.UserRepository
import com.example.meeplekingdom.databinding.ActivityUserFormBinding
import com.example.meeplekingdom.model.AppUser
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.util.Navigator

/**
 * Formulario del Administrador para CREAR un usuario nuevo o EDITAR uno existente.
 * Si el Intent trae un uid, funciona en modo edición; si no, en modo creación.
 */
class UserFormActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserFormBinding
    private var editing: AppUser? = null

    companion object {
        private const val EXTRA_UID = "uid"
        private const val EXTRA_NOMBRE = "nombre"
        private const val EXTRA_EMAIL = "email"
        private const val EXTRA_ROL = "rol"
        private const val EXTRA_ACTIVO = "activo"
        private const val EXTRA_TELEFONO = "telefono"

        /** Crea el Intent para abrir el formulario (con [user] = edición, sin él = creación). */
        fun newIntent(context: Context, user: AppUser? = null): Intent =
            Intent(context, UserFormActivity::class.java).apply {
                if (user != null) {
                    putExtra(EXTRA_UID, user.uid)
                    putExtra(EXTRA_NOMBRE, user.nombre)
                    putExtra(EXTRA_EMAIL, user.email)
                    putExtra(EXTRA_ROL, user.rol)
                    putExtra(EXTRA_ACTIVO, user.activo)
                    putExtra(EXTRA_TELEFONO, user.telefono)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.ADMIN)) return
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.spRol.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Roles.TODOS.map { Roles.etiqueta(it) }
        )

        val uid = intent.getStringExtra(EXTRA_UID)
        if (uid != null) {
            editing = AppUser(
                uid = uid,
                nombre = intent.getStringExtra(EXTRA_NOMBRE).orEmpty(),
                email = intent.getStringExtra(EXTRA_EMAIL).orEmpty(),
                rol = intent.getStringExtra(EXTRA_ROL) ?: Roles.COMPRADOR,
                activo = intent.getBooleanExtra(EXTRA_ACTIVO, true),
                telefono = intent.getStringExtra(EXTRA_TELEFONO).orEmpty()
            )
        }
        configurarModo()

        binding.btnVolverUserForm.setOnClickListener { finish() }
        binding.btnGuardarUsuario.setOnClickListener { guardar() }
    }

    /** Ajusta títulos y campos según sea creación o edición. */
    private fun configurarModo() {
        val user = editing
        if (user == null) {
            binding.tvTituloUserForm.text = "Nuevo Usuario"
            binding.swActivo.visibility = View.GONE
            return
        }
        binding.tvTituloUserForm.text = "Editar Usuario"
        binding.etNombreUsuario.setText(user.nombre)
        binding.etCorreoUsuario.setText(user.email)
        binding.etTelefonoUsuario.setText(user.telefono)
        binding.spRol.setSelection(Roles.TODOS.indexOf(user.rol).coerceAtLeast(0))
        binding.swActivo.isChecked = user.activo
        // El correo y la contraseña no se cambian desde aquí.
        binding.etCorreoUsuario.isEnabled = false
        binding.etClaveUsuario.visibility = View.GONE
        binding.btnGuardarUsuario.text = "Guardar cambios"
        // Un administrador no puede quitarse a sí mismo el rol ni desactivarse.
        if (user.uid == Session.user?.uid) {
            binding.spRol.isEnabled = false
            binding.swActivo.isEnabled = false
        }
    }

    /** Valida el formulario y crea o actualiza el usuario en Firestore. */
    private fun guardar() {
        val nombre = binding.etNombreUsuario.text.toString().trim()
        val email = binding.etCorreoUsuario.text.toString().trim()
        val clave = binding.etClaveUsuario.text.toString()
        val telefono = binding.etTelefonoUsuario.text.toString().trim()
        val rol = Roles.TODOS[binding.spRol.selectedItemPosition]

        if (nombre.length < 3) {
            binding.etNombreUsuario.error = "Escribe el nombre completo"
            return
        }
        val current = editing
        if (current == null) {
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etCorreoUsuario.error = "Correo inválido"
                return
            }
            if (clave.length < 6) {
                binding.etClaveUsuario.error = "Mínimo 6 caracteres"
                return
            }
        }

        setLoading(true)
        val alTerminar = {
            setLoading(false)
            Toast.makeText(this, "Usuario guardado", Toast.LENGTH_SHORT).show()
            finish()
        }
        val alFallar = { mensaje: String ->
            setLoading(false)
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        }

        if (current == null) {
            UserRepository.createByAdmin(applicationContext, nombre, email, clave, rol, telefono, alTerminar, alFallar)
        } else {
            val actualizado = current.copy(
                nombre = nombre, telefono = telefono, rol = rol, activo = binding.swActivo.isChecked
            )
            UserRepository.update(actualizado, alTerminar, alFallar)
        }
    }

    /** Muestra el progreso y bloquea el botón mientras se guarda. */
    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGuardarUsuario.isEnabled = !loading
    }
}
