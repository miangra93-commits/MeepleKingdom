package com.example.meeplekingdom.data

import android.content.Context
import com.example.meeplekingdom.model.AppUser
import com.example.meeplekingdom.util.ErrorMapper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** CRUD de usuarios sobre la colección `users` de Firestore (uso del rol Administrador). */
object UserRepository {
    private const val COLLECTION = "users"
    private const val SECONDARY_APP = "secondary"
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /**
     * READ: escucha en tiempo real todos los usuarios, ordenados por nombre.
     * Devuelve el registro del listener; hay que llamar a `remove()` al destruir la pantalla.
     */
    fun listen(onChange: (List<AppUser>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection(COLLECTION).addSnapshotListener { snapshot, e ->
            if (e != null) {
                onError(ErrorMapper.message(e))
                return@addSnapshotListener
            }
            val users = snapshot?.documents
                ?.mapNotNull { d -> d.toObject(AppUser::class.java)?.copy(uid = d.id) }
                .orEmpty()
                .sortedBy { it.nombre.lowercase() }
            onChange(users)
        }

    /**
     * CREATE: el administrador crea una cuenta nueva sin perder su propia sesión.
     * Truco: se usa una segunda instancia de FirebaseApp ("secondary") para crear el usuario en
     * Authentication; así la sesión principal (del admin) no cambia. Luego se guarda el perfil
     * con el Firestore principal, autenticado como administrador.
     */
    fun createByAdmin(
        context: Context, nombre: String, email: String, password: String, rol: String,
        telefono: String, onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        val secondaryApp = try {
            FirebaseApp.getInstance(SECONDARY_APP)
        } catch (e: IllegalStateException) {
            FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, SECONDARY_APP)
        }
        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

        secondaryAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    secondaryAuth.signOut()
                    onError("No se pudo crear la cuenta.")
                    return@addOnSuccessListener
                }
                val user = AppUser(
                    uid = uid, nombre = nombre, email = email, rol = rol, activo = true,
                    telefono = telefono, creadoEn = System.currentTimeMillis()
                )
                db.collection(COLLECTION).document(uid).set(user)
                    .addOnSuccessListener {
                        secondaryAuth.signOut()
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        result.user?.delete()
                        secondaryAuth.signOut()
                        onError(ErrorMapper.message(e))
                    }
            }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /** UPDATE: modifica nombre, teléfono, rol y estado (activo/desactivado) de un usuario. */
    fun update(user: AppUser, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection(COLLECTION).document(user.uid).update(
            mapOf(
                "nombre" to user.nombre,
                "telefono" to user.telefono,
                "rol" to user.rol,
                "activo" to user.activo
            )
        )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }

    /**
     * DELETE: elimina el perfil de Firestore. Desde la app no se puede borrar la cuenta de
     * Authentication de otra persona, pero sin perfil el login es rechazado (ver AuthRepository.loadProfile).
     */
    fun delete(uid: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection(COLLECTION).document(uid).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }
}
