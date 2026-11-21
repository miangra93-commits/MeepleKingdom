package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meeplekingdom.data.AuthRepository
import com.example.meeplekingdom.util.Navigator

/**
 * Pantalla de bienvenida. Tras una breve pausa decide el destino:
 * si Firebase conserva la sesión, carga el perfil y abre el panel del rol; si no, abre el Login.
 */
class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val continuar = Runnable { decidirDestino() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        handler.postDelayed(continuar, 1500)
    }

    /** Evita que el temporizador dispare navegación si la pantalla ya se cerró. */
    override fun onDestroy() {
        handler.removeCallbacks(continuar)
        super.onDestroy()
    }

    /** Si hay sesión guardada abre el panel del rol, de lo contrario va al Login. */
    private fun decidirDestino() {
        if (!AuthRepository.hasSession()) {
            irAlLogin()
            return
        }
        AuthRepository.loadProfile(
            onSuccess = { user -> if (!isFinishing) Navigator.goHome(this, user) },
            onError = { mensaje ->
                if (!isFinishing) {
                    Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                    irAlLogin()
                }
            }
        )
    }

    /** Abre el Login y cierra el Splash para que no pueda volverse a él. */
    private fun irAlLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
