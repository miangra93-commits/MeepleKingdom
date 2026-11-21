package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.meeplekingdom.data.AuthRepository
import com.example.meeplekingdom.databinding.ActivityLoginBinding
import com.example.meeplekingdom.model.AppUser
import com.example.meeplekingdom.util.BiometricAuthHelper
import com.example.meeplekingdom.util.Navigator

/**
 * Inicio de sesión con correo/contraseña (Firebase Auth) o con huella dactilar.
 * Tras entrar, redirige al panel que corresponde al rol del usuario.
 */
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnIngresar.setOnClickListener { iniciarSesionConCorreo() }
        binding.btnHuella.setOnClickListener { iniciarSesionConHuella() }
        binding.btnOlvideContrasena.setOnClickListener {
            startActivity(Intent(this, RecoveryActivity::class.java))
        }
        binding.btnIrRegistro.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    /** Valida los campos y autentica con Firebase; al terminar ofrece activar la huella. */
    private fun iniciarSesionConCorreo() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Escribe un correo válido"
            return
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Escribe tu contraseña"
            return
        }

        setLoading(true)
        AuthRepository.login(
            email, password,
            onSuccess = { user ->
                setLoading(false)
                ofrecerActivarHuella(user, email, password)
            },
            onError = { mensaje ->
                setLoading(false)
                mostrar(mensaje)
            }
        )
    }

    /**
     * Si el dispositivo soporta huella y esta cuenta aún no la tiene activada, pregunta si desea
     * activarla. En cualquier otro caso entra directamente al panel.
     */
    private fun ofrecerActivarHuella(user: AppUser, email: String, password: String) {
        val yaActivada = BiometricAuthHelper.storedEmail(this).equals(email, ignoreCase = true)
        if (!BiometricAuthHelper.isAvailable(this) || yaActivada) {
            Navigator.goHome(this, user)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Acceso con huella")
            .setMessage("¿Quieres entrar la próxima vez con tu huella dactilar?")
            .setCancelable(false)
            .setPositiveButton("Activar") { _, _ -> activarHuella(user, email, password) }
            .setNegativeButton("Ahora no") { _, _ -> Navigator.goHome(this, user) }
            .show()
    }

    /** Pide la huella y, si es correcta, guarda la contraseña cifrada con la clave del Keystore. */
    private fun activarHuella(user: AppUser, email: String, password: String) {
        val cipher = try {
            BiometricAuthHelper.encryptCipher()
        } catch (e: Exception) {
            mostrar("No se pudo activar la huella en este dispositivo.")
            Navigator.goHome(this, user)
            return
        }

        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authCipher = result.cryptoObject?.cipher
                    if (authCipher != null) {
                        BiometricAuthHelper.saveCredentials(this@LoginActivity, authCipher, email, password)
                        mostrar("Huella activada")
                    }
                    Navigator.goHome(this@LoginActivity, user)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // El usuario canceló: entra igual, sin huella.
                    Navigator.goHome(this@LoginActivity, user)
                }
            })
        prompt.authenticate(infoDeHuella("Activar acceso con huella"), BiometricPrompt.CryptoObject(cipher))
    }

    /** Inicia sesión usando la huella: descifra las credenciales guardadas y autentica en Firebase. */
    private fun iniciarSesionConHuella() {
        if (!BiometricAuthHelper.isAvailable(this)) {
            mostrar("Este dispositivo no tiene una huella configurada.")
            return
        }
        if (!BiometricAuthHelper.hasCredentials(this)) {
            mostrar("Primero inicia sesión con tu correo y activa la huella.")
            return
        }

        val cipher = try {
            BiometricAuthHelper.decryptCipher(this)
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Cambiaron las huellas registradas: la clave ya no sirve.
            BiometricAuthHelper.clear(this)
            mostrar("Tus huellas cambiaron. Inicia con tu contraseña y vuelve a activar la huella.")
            return
        } catch (e: Exception) {
            BiometricAuthHelper.clear(this)
            mostrar("No se pudo usar la huella. Inicia con tu contraseña.")
            return
        }

        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authCipher = result.cryptoObject?.cipher ?: return
                    val credenciales = try {
                        BiometricAuthHelper.readCredentials(this@LoginActivity, authCipher)
                    } catch (e: Exception) {
                        null
                    }
                    if (credenciales == null) {
                        mostrar("No se pudieron leer las credenciales. Inicia con tu contraseña.")
                        return
                    }
                    setLoading(true)
                    AuthRepository.login(
                        credenciales.first, credenciales.second,
                        onSuccess = { user ->
                            setLoading(false)
                            Navigator.goHome(this@LoginActivity, user)
                        },
                        onError = { mensaje ->
                            setLoading(false)
                            mostrar(mensaje)
                        }
                    )
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        mostrar("Huella: $errString")
                    }
                }
            })
        prompt.authenticate(infoDeHuella("Ingreso a Meeple Kingdom"), BiometricPrompt.CryptoObject(cipher))
    }

    /** Configuración común del diálogo del sistema que pide la huella (solo biometría fuerte). */
    private fun infoDeHuella(titulo: String): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle("Apoya tu dedo en el sensor")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Usar contraseña")
            .build()

    /** Muestra u oculta la barra de progreso y bloquea los botones mientras se espera a Firebase. */
    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnIngresar.isEnabled = !loading
        binding.btnHuella.isEnabled = !loading
    }

    /** Muestra un mensaje corto al usuario. */
    private fun mostrar(mensaje: String) = Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
}
