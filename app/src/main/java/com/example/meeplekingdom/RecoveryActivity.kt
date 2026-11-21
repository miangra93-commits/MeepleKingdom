package com.example.meeplekingdom

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meeplekingdom.data.AuthRepository

/** Recuperación de contraseña: Firebase envía al correo un enlace para restablecerla. */
class RecoveryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recovery)

        val etCorreo = findViewById<EditText>(R.id.etCorreoRecuperacion)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarEnlace)

        findViewById<TextView>(R.id.btnVolverLogin).setOnClickListener { finish() }

        btnEnviar.setOnClickListener {
            val email = etCorreo.text.toString().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etCorreo.error = "Escribe un correo válido"
                return@setOnClickListener
            }
            btnEnviar.isEnabled = false
            AuthRepository.sendPasswordReset(
                email,
                onSuccess = {
                    Toast.makeText(
                        this,
                        "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                },
                onError = { mensaje ->
                    btnEnviar.isEnabled = true
                    Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}
