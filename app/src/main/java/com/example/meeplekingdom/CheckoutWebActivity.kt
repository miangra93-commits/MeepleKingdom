package com.example.meeplekingdom

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.meeplekingdom.databinding.ActivityCheckoutWebBinding
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.util.Navigator

/**
 * Muestra dentro de la app el Smart Checkout de ePayco (página servida por nuestra Cloud Function).
 * Esta pantalla NO decide si el pago fue exitoso: solo se cierra cuando el checkout termina o el
 * usuario cancela. El resultado real lo confirma el webhook en el servidor y la app lo lee de Firestore.
 */
class CheckoutWebActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCheckoutWebBinding

    companion object {
        private const val EXTRA_URL = "checkoutUrl"

        /** Crea el Intent para abrir el checkout en [url] (devuelta por createPaymentSession). */
        fun newIntent(context: Context, url: String): Intent =
            Intent(context, CheckoutWebActivity::class.java).putExtra(EXTRA_URL, url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.COMPRADOR)) return
        binding = ActivityCheckoutWebBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null || !url.startsWith("https://")) {
            finish()
            return
        }

        binding.btnCerrarPago.setOnClickListener { confirmarSalida() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmarSalida()
        })

        val web = binding.webCheckout
        web.settings.javaScriptEnabled = true       // ePayco lo necesita para dibujar el checkout
        web.settings.domStorageEnabled = true
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.addJavascriptInterface(Bridge(), "MeepleBridge")
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressWeb.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressWeb.visibility = View.GONE
            }

            /** Solo se navega por https; esquemas raros (intent://, http://) se bloquean. */
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                request?.url?.scheme != "https"
        }
        web.loadUrl(url)
    }

    /** Pregunta antes de abandonar el pago a medias. */
    private fun confirmarSalida() {
        AlertDialog.Builder(this)
            .setTitle("Cancelar pago")
            .setMessage("¿Quieres salir? Tu pedido quedará pendiente y podrás pagarlo desde la pestaña Pedidos.")
            .setPositiveButton("Salir") { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .setNegativeButton("Seguir pagando", null)
            .show()
    }

    /** Libera el WebView para evitar fugas de memoria. */
    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.webCheckout.removeJavascriptInterface("MeepleBridge")
            binding.webCheckout.destroy()
        }
        super.onDestroy()
    }

    /** Puente JavaScript → Android. Solo expone una acción inofensiva: avisar que el checkout terminó. */
    private inner class Bridge {
        @JavascriptInterface
        fun onFinished() {
            runOnUiThread {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }
}
