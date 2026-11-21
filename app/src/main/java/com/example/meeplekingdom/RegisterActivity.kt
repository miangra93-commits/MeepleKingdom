package com.example.meeplekingdom

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meeplekingdom.data.AuthRepository
import com.example.meeplekingdom.databinding.ActivityRegisterBinding
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.util.Navigator

/**
 * Registro de cuentas nuevas. Solo se puede elegir Comprador o Vendedor;
 * las cuentas de Administrador las crea otro administrador.
 */
class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVolverRegistro.setOnClickListener { finish() }
        binding.btnRegistrar.setOnClickListener { registrar() }
    }

    /** Valida el formulario y crea la cuenta; al terminar entra directo al panel del rol elegido. */
    private fun registrar() {
        val nombre = binding.etNombre.text.toString().trim()
        val email = binding.etCorreo.text.toString().trim()
        val clave = binding.etClave.text.toString()
        val confirmar = binding.etClaveConfirmar.text.toString()
        val rol = if (binding.rbVendedor.isChecked) Roles.VENDEDOR else Roles.COMPRADOR

        when {
            nombre.length < 3 -> binding.etNombre.error = "Escribe tu nombre completo"
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> binding.etCorreo.error = "Correo inválido"
            clave.length < 6 -> binding.etClave.error = "Mínimo 6 caracteres"
            clave != confirmar -> binding.etClaveConfirmar.error = "Las contraseñas no coinciden"
            else -> {
                setLoading(true)
                AuthRepository.register(
                    nombre, email, clave, rol,
                    onSuccess = { user ->
                        setLoading(false)
                        Toast.makeText(this, "¡Bienvenido, ${user.nombre}!", Toast.LENGTH_SHORT).show()
                        Navigator.goHome(this, user)
                    },
                    onError = { mensaje ->
                        setLoading(false)
                        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    /** Muestra el progreso y bloquea el botón mientras Firebase responde. */
    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRegistrar.isEnabled = !loading
    }
}
