package com.example.meeplekingdom.data

import com.example.meeplekingdom.model.AppUser
import com.example.meeplekingdom.util.ErrorMapper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Acceso a Firebase Authentication + perfil de usuario en Firestore.
 * Todas las operaciones son asíncronas y responden con callbacks [onSuccess] / [onError].
 */
object AuthRepository {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /**
     * Registra una cuenta nueva (Comprador o Vendedor): crea el usuario en Authentication
     * y su perfil en `users/{uid}`. Si falla guardar el perfil, elimina la cuenta recién creada
     * para que el correo pueda reutilizarse.
     */
    fun register(
        nombre: String, email: String, password: String, rol: String,
        onSuccess: (AppUser) -> Unit, onError: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user
                if (firebaseUser == null) {
                    onError("No se pudo crear la cuenta.")
                    return@addOnSuccessListener
                }
                val user = AppUser(
                    uid = firebaseUser.uid, nombre = nombre, email = email, rol = rol,
                    activo = true, creadoEn = System.currentTimeMillis()
                )
                db.collection("users").document(user.uid).set(user)
                    .addOnSuccessListener {
                        Session.user = user
                        onSuccess(user)
                    }
                    .addOnFailureListener { e ->
                        firebaseUser.delete()
                        onError(ErrorMapper.message(e))
                    }
            }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** Inicia sesión con correo y contraseña y carga el perfil (rol) del usuario. */
    fun login(
        email: String, password: String,
        onSuccess: (AppUser) -> Unit, onError: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { loadProfile(onSuccess, onError) }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /**
     * Carga el perfil del usuario con sesión abierta. Si el perfil fue eliminado o la cuenta está
     * desactivada por un administrador, cierra la sesión y devuelve un error.
     */
    fun loadProfile(onSuccess: (AppUser) -> Unit, onError: (String) -> Unit) {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            onError("No hay una sesión activa.")
            return
        }
        db.collection("users").document(firebaseUser.uid).get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(AppUser::class.java)?.copy(uid = doc.id)
                when {
                    user == null -> {
                        logout()
                        onError("Tu cuenta fue eliminada o no existe. Contacta al administrador.")
                    }
                    !user.activo -> {
                        logout()
                        onError("Tu cuenta está desactivada. Contacta al administrador.")
                    }
                    else -> {
                        Session.user = user
                        onSuccess(user)
                    }
                }
            }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** Envía al correo un enlace para restablecer la contraseña. */
    fun sendPasswordReset(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** Cierra la sesión de Firebase y limpia la sesión en memoria. */
    fun logout() {
        auth.signOut()
        Session.user = null
    }

    /** True si Firebase conserva una sesión abierta en el dispositivo. */
    fun hasSession(): Boolean = auth.currentUser != null
}
