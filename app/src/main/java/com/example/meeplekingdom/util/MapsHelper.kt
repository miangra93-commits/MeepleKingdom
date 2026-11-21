package com.example.meeplekingdom.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Abre una ubicación en la app de mapas del dispositivo. */
object MapsHelper {

    /** Muestra el punto ([latitud], [longitud]) con una etiqueta en Google Maps u otra app de mapas. */
    fun open(context: Context, latitud: Double, longitud: Double, etiqueta: String) {
        val uri = Uri.parse("geo:$latitud,$longitud?q=$latitud,$longitud(${Uri.encode(etiqueta)})")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No hay una app de mapas instalada", Toast.LENGTH_LONG).show()
        }
    }
}
