package com.example.meeplekingdom.data

import com.example.meeplekingdom.model.AppUser

/**
 * Guarda en memoria el perfil del usuario con sesión activa.
 * Si el proceso se reinicia vale null y la app vuelve a cargarlo desde Firestore (ver SplashActivity).
 */
object Session {
    var user: AppUser? = null
}
