package com.example.meeplekingdom.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** Herramientas para preparar las fotos de productos antes de subirlas a Firebase Storage. */
object ImageUtils {

    /**
     * Lee una imagen (cámara o galería), corrige su rotación EXIF, la reduce a [maxSide] píxeles
     * en su lado mayor y la comprime a JPEG. Así se ahorra ancho de banda y espacio en Storage.
     *
     * @throws IllegalArgumentException si el archivo no es una imagen válida.
     */
    fun compressToJpeg(context: Context, uri: Uri, maxSide: Int = 1024, quality: Int = 80): ByteArray {
        val resolver = context.contentResolver

        // 1) Leer solo las dimensiones para decidir cuánto submuestrear al decodificar.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) {
            sample *= 2
        }

        // 2) Decodificar la imagen ya reducida.
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("El archivo no es una imagen válida")

        // 3) Leer la orientación EXIF (las fotos de cámara suelen venir rotadas).
        val rotation = resolver.openInputStream(uri).use { stream ->
            if (stream == null) 0 else when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }

        // 4) Escalar al tamaño máximo y rotar en una sola operación.
        val scale = min(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postRotate(rotation.toFloat())
            postScale(scale, scale)
        }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        // 5) Comprimir a JPEG.
        val out = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
