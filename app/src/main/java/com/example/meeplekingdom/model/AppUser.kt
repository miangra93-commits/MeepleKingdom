package com.example.meeplekingdom.model

/**
 * Perfil de un usuario guardado en la colección `users` de Firestore.
 * El id del documento coincide con el uid de Firebase Authentication.
 *
 * Todos los campos tienen valor por defecto: Firestore necesita un constructor
 * sin argumentos para poder convertir el documento en este objeto.
 */
data class AppUser(
    val uid: String = "",
    val nombre: String = "",
    val email: String = "",
    val rol: String = Roles.COMPRADOR,
    val activo: Boolean = true,
    val telefono: String = "",
    val creadoEn: Long = 0L
)
