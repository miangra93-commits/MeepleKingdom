package com.example.meeplekingdom.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

/** Captura la ubicación del usuario (GPS) y la convierte en una dirección legible (geocodificación inversa). */
object LocationHelper {

    /** Permisos que se piden en tiempo de ejecución para la ubicación. */
    val PERMISOS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /** True si la app ya tiene permiso de ubicación precisa o aproximada. */
    fun hasPermission(context: Context): Boolean = PERMISOS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Pide al proveedor combinado de Google (Fused Location) la ubicación actual.
     * Debe llamarse solo si [hasPermission] es true.
     */
    @SuppressLint("MissingPermission")
    fun getCurrentLocation(context: Context, onSuccess: (Location) -> Unit, onError: (String) -> Unit) {
        val cancelacion = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancelacion.token)
            .addOnSuccessListener { location ->
                if (location != null) onSuccess(location)
                else onError("No se pudo obtener tu ubicación. Activa el GPS e inténtalo de nuevo.")
            }
            .addOnFailureListener { onError("No se pudo obtener tu ubicación: ${it.localizedMessage}") }
    }

    /**
     * Convierte coordenadas en una dirección de texto usando Geocoder. Corre en un hilo aparte
     * porque consulta la red, y devuelve el resultado en el hilo principal (null si no hay dirección).
     */
    fun reverseGeocode(context: Context, latitud: Double, longitud: Double, onResult: (String?) -> Unit) {
        val appContext = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val direccion: String? = try {
                if (!Geocoder.isPresent()) {
                    null
                } else {
                    @Suppress("DEPRECATION")
                    val resultados = Geocoder(appContext, Locale.forLanguageTag("es-CO"))
                        .getFromLocation(latitud, longitud, 1)
                    resultados?.firstOrNull()?.getAddressLine(0)
                }
            } catch (e: Exception) {
                null
            }
            main.post { onResult(direccion) }
        }.start()
    }
}
