package com.example.meeplekingdom.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException

/** Traduce las excepciones de Firebase a mensajes claros en español para el usuario. */
object ErrorMapper {

    /** Devuelve el mensaje que se mostrará al usuario para la excepción recibida. */
    fun message(e: Exception?): String = when (e) {
        is FirebaseNetworkException -> "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."
        is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera unos minutos."
        // WeakPassword hereda de InvalidCredentials, por eso se evalúa primero.
        is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil (mínimo 6 caracteres)."
        is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo."
        is FirebaseAuthInvalidUserException -> "No existe una cuenta con ese correo o está deshabilitada."
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
        is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No tienes permisos para realizar esta acción."
            // La app usa FAILED_PRECONDITION para sus propios mensajes de negocio (stock, estado...).
            FirebaseFirestoreException.Code.FAILED_PRECONDITION -> e.message ?: "No se pudo completar la operación."
            else -> "Error de base de datos: ${e.message}"
        }
        is FirebaseFunctionsException -> when (e.code) {
            FirebaseFunctionsException.Code.NOT_FOUND ->
                "El servicio de pagos no está disponible. Verifica que las Cloud Functions estén desplegadas."
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Tu sesión expiró. Inicia sesión de nuevo."
            // Los mensajes de nuestras funciones ya vienen en español y listos para mostrar.
            else -> e.message ?: "No se pudo contactar el servicio de pagos."
        }
        is StorageException -> "Error con la imagen en la nube: ${e.message}"
        else -> e?.localizedMessage ?: "Ocurrió un error inesperado."
    }
}
