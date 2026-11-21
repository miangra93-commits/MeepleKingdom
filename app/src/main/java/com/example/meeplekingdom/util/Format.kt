package com.example.meeplekingdom.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Utilidades de formato para mostrar datos al usuario. */
object Format {
    private val copFormat: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }

    /** Formatea un valor como pesos colombianos sin decimales, p. ej. "$ 150.000". */
    fun cop(value: Double): String = copFormat.format(value)

    /** Formatea una marca de tiempo (milisegundos) como "17/11/2026 14:30". */
    fun fecha(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CO")).format(Date(millis))
}
