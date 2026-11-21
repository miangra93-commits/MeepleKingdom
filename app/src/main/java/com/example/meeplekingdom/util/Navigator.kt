package com.example.meeplekingdom.util

import android.app.Activity
import android.content.Intent
import com.example.meeplekingdom.AdminActivity
import com.example.meeplekingdom.LoginActivity
import com.example.meeplekingdom.MainActivity
import com.example.meeplekingdom.SellerActivity
import com.example.meeplekingdom.SplashActivity
import com.example.meeplekingdom.data.AuthRepository
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.model.AppUser
import com.example.meeplekingdom.model.Roles

/** Centraliza la navegación entre sesiones y paneles según el rol del usuario. */
object Navigator {

    /** Abre la pantalla principal que corresponde al rol del usuario y cierra la actual. */
    fun goHome(activity: Activity, user: AppUser) {
        val destino = when (user.rol) {
            Roles.ADMIN -> AdminActivity::class.java
            Roles.VENDEDOR -> SellerActivity::class.java
            else -> MainActivity::class.java
        }
        activity.startActivity(Intent(activity, destino).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    /** Cierra la sesión de Firebase, limpia la sesión local y vuelve al Login borrando el historial. */
    fun logout(activity: Activity) {
        AuthRepository.logout()
        activity.startActivity(Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    /**
     * Guardia de acceso: verifica que haya sesión y que el rol sea uno de [roles].
     * Si no se cumple, reinicia la app desde el Splash (que recarga el perfil) y devuelve false.
     * La seguridad real la imponen además las reglas de Firestore.
     */
    fun requireRole(activity: Activity, vararg roles: String): Boolean {
        val user = Session.user
        if (user == null || user.rol !in roles) {
            activity.startActivity(Intent(activity, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            return false
        }
        return true
    }
}
