package com.example.meeplekingdom.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Soporte para iniciar sesión con huella dactilar.
 *
 * Diseño: al activar la huella, la contraseña se cifra con AES-GCM usando una clave que vive en el
 * Android Keystore y SOLO se puede usar tras autenticarse con la huella (setUserAuthenticationRequired).
 * En SharedPreferences se guarda únicamente el texto cifrado, nunca la contraseña en claro.
 * Si el usuario registra una huella nueva, Android invalida la clave y se debe reactivar.
 */
object BiometricAuthHelper {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "meeple_kingdom_biometric_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS = "meeple_biometric_prefs"
    private const val K_EMAIL = "email"
    private const val K_IV = "iv"
    private const val K_DATA = "data"

    /** Indica si el dispositivo tiene sensor de huella con al menos una huella registrada. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

    /** Indica si ya hay credenciales cifradas guardadas para entrar con huella. */
    fun hasCredentials(context: Context): Boolean =
        prefs(context).contains(K_DATA) && prefs(context).contains(K_IV)

    /** Correo de la cuenta que tiene la huella activada (o null si ninguna). */
    fun storedEmail(context: Context): String? = prefs(context).getString(K_EMAIL, null)

    /** Crea un Cipher en modo cifrado, listo para envolverse en un CryptoObject y pasar por la huella. */
    fun encryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher
    }

    /**
     * Crea un Cipher en modo descifrado con el IV guardado.
     * Lanza KeyPermanentlyInvalidatedException si cambiaron las huellas del dispositivo.
     */
    fun decryptCipher(context: Context): Cipher {
        val iv = Base64.decode(prefs(context).getString(K_IV, "") ?: "", Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher
    }

    /** Cifra la contraseña con el [cipher] ya autenticado por huella y la guarda junto al correo. */
    fun saveCredentials(context: Context, cipher: Cipher, email: String, password: String) {
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs(context).edit()
            .putString(K_EMAIL, email)
            .putString(K_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(K_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    /** Descifra la contraseña con el [cipher] ya autenticado. Devuelve (correo, contraseña) o null. */
    fun readCredentials(context: Context, cipher: Cipher): Pair<String, String>? {
        val email = storedEmail(context) ?: return null
        val data = prefs(context).getString(K_DATA, null) ?: return null
        val plain = cipher.doFinal(Base64.decode(data, Base64.NO_WRAP))
        return Pair(email, String(plain, Charsets.UTF_8))
    }

    /** Borra las credenciales guardadas y la clave del Keystore (desactiva la huella). */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            // Si el Keystore falla no hay nada más que limpiar.
        }
    }

    /** Obtiene la clave AES del Keystore; si no existe la genera exigiendo autenticación biométrica. */
    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Autenticación en cada uso, solo con biometría fuerte.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
