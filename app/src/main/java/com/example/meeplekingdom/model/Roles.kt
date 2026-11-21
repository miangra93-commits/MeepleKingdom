package com.example.meeplekingdom.model

/**
 * Constantes de los tres roles de la aplicación.
 * El valor se guarda en Firestore en el campo `users/{uid}.rol`.
 */
object Roles {
    const val ADMIN = "admin"
    const val VENDEDOR = "vendedor"
    const val COMPRADOR = "comprador"

    /** Lista ordenada de roles (se usa para llenar el Spinner del administrador). */
    val TODOS = listOf(COMPRADOR, VENDEDOR, ADMIN)

    /** Devuelve el nombre legible de un rol para mostrarlo en pantalla. */
    fun etiqueta(rol: String): String = when (rol) {
        ADMIN -> "Administrador"
        VENDEDOR -> "Vendedor"
        else -> "Comprador"
    }
}
