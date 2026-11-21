package com.example.meeplekingdom.data

import com.example.meeplekingdom.util.ErrorMapper
import com.google.firebase.functions.FirebaseFunctions


object PaymentRepository {

    private const val REGION = "us-central1"

    fun createSession(grupoId: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        FirebaseFunctions.getInstance(REGION)
            .getHttpsCallable("createPaymentSession")
            .call(hashMapOf("grupoId" to grupoId))
            .addOnSuccessListener { result ->
                val url = (result.getData() as? Map<*, *>)?.get("checkoutUrl") as? String
                if (url.isNullOrEmpty()) onError("El servicio de pagos no devolvió una sesión válida.")
                else onSuccess(url)
            }
            .addOnFailureListener { onError(ErrorMapper.message(it)) }
    }
}
