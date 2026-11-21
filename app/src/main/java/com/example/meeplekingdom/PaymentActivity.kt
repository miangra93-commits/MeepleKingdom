package com.example.meeplekingdom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.meeplekingdom.data.CartRepository
import com.example.meeplekingdom.data.OrderRepository
import com.example.meeplekingdom.data.PaymentRepository
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.databinding.ActivityPaymentBinding
import com.example.meeplekingdom.model.CartItem
import com.example.meeplekingdom.model.Order
import com.example.meeplekingdom.model.OrderStatus
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.util.Format
import com.example.meeplekingdom.util.LocationHelper
import com.example.meeplekingdom.util.Navigator
import com.google.firebase.firestore.ListenerRegistration

/**
 * Finalizar compra y pagar. Tiene dos modos:
 *  - Compra: muestra el carrito, captura la dirección (GPS opcional), registra los pedidos y abre ePayco.
 *  - Solo pago (recibe un grupoId): reintenta el pago de pedidos que quedaron pendientes.
 * Tras el checkout espera a que el webhook marque los pedidos como pagados (se lee en Firestore).
 */
class PaymentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentBinding

    private var items: List<CartItem> = emptyList()
    private var grupoId: String? = null
    private var modoSoloPago = false
    private var latitud = 0.0
    private var longitud = 0.0

    private var ordersListener: ListenerRegistration? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tiempoAgotado: Runnable? = null

    /** Se ejecuta al volver del WebView de ePayco (haya pagado, cerrado o cancelado). */
    private val checkoutLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { esperarConfirmacion() }

    /** Resultado de pedir permisos de ubicación. */
    private val permisoLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
            if (resultado.values.any { it }) obtenerUbicacion()
            else mostrar("Sin permiso de ubicación puedes escribir la dirección a mano.")
        }

    companion object {
        private const val EXTRA_GRUPO_ID = "grupoId"
        private const val ESPERA_CONFIRMACION_MS = 20_000L

        /** Intent para abrir la pantalla; con [grupoId] funciona en modo "solo pago". */
        fun newIntent(context: Context, grupoId: String? = null): Intent =
            Intent(context, PaymentActivity::class.java).apply {
                if (grupoId != null) putExtra(EXTRA_GRUPO_ID, grupoId)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.COMPRADOR)) return
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVolverPago.setOnClickListener { finish() }
        binding.btnUbicacion.setOnClickListener { pedirUbicacion() }

        grupoId = intent.getStringExtra(EXTRA_GRUPO_ID)
        modoSoloPago = grupoId != null
        if (modoSoloPago) configurarSoloPago() else cargarCarrito()

        binding.btnPagar.setOnClickListener {
            if (modoSoloPago) iniciarPago(desdeCompra = false) else confirmarPedido()
        }
    }

    // ---------------------------------------------------------------- Modo compra

    /** Descarga el carrito actual y arma el resumen con el total. */
    private fun cargarCarrito() {
        val uid = Session.user?.uid ?: return
        CartRepository.get(
            uid,
            onSuccess = { cart ->
                items = cart
                binding.tvResumenCompra.text =
                    cart.joinToString("\n") { "${it.cantidad} × ${it.nombre} — ${Format.cop(it.precio * it.cantidad)}" }
                binding.tvTotalCompra.text = "Total: ${Format.cop(cart.sumOf { it.precio * it.cantidad })}"
                binding.btnPagar.isEnabled = cart.isNotEmpty()
            },
            onError = { mostrar(it) }
        )
    }

    /** Valida la dirección, registra los pedidos (transacción) y abre la pasarela de pago. */
    private fun confirmarPedido() {
        val usuario = Session.user ?: return
        val direccion = binding.etDireccion.text.toString().trim()
        if (direccion.length < 8) {
            binding.etDireccion.error = "Escribe una dirección de entrega completa"
            return
        }
        setLoading(true, "Registrando tu pedido…")
        OrderRepository.placeOrder(
            usuario, items, direccion, latitud, longitud,
            onSuccess = { pedidos ->
                grupoId = pedidos.first().grupoId
                iniciarPago(desdeCompra = true)
            },
            onError = { mensaje ->
                setLoading(false)
                mostrar(mensaje)
            }
        )
    }

    // ---------------------------------------------------------------- Modo solo pago

    /** Oculta la dirección y muestra el resumen de los pedidos pendientes del grupo. */
    private fun configurarSoloPago() {
        binding.tvTituloPago.text = "Pagar pedido"
        binding.seccionDireccion.visibility = View.GONE
        binding.btnPagar.text = "Pagar con ePayco"
        val uid = Session.user?.uid ?: return
        OrderRepository.getByGroup(
            uid, grupoId.orEmpty(),
            onSuccess = { pedidos ->
                val pendientes = pedidos.filter { it.estado == OrderStatus.PENDIENTE_PAGO }
                if (pendientes.isEmpty()) {
                    mostrar("Estos pedidos ya no están pendientes de pago.")
                    finish()
                    return@getByGroup
                }
                binding.tvResumenCompra.text = pendientes.joinToString("\n") { pedido ->
                    pedido.items.joinToString("\n") { "${it.cantidad} × ${it.nombre}" }
                }
                binding.tvTotalCompra.text = "Total: ${Format.cop(pendientes.sumOf { it.total })}"
                binding.btnPagar.isEnabled = true
            },
            onError = { mostrar(it) }
        )
    }

    // ---------------------------------------------------------------- Pago con ePayco

    /**
     * Pide al servidor una sesión de pago y abre el checkout. Si falla justo después de registrar
     * el pedido, este queda pendiente y el usuario puede pagarlo luego desde la pestaña Pedidos.
     */
    private fun iniciarPago(desdeCompra: Boolean) {
        val grupo = grupoId ?: return
        setLoading(true, "Preparando el pago seguro…")
        PaymentRepository.createSession(
            grupo,
            onSuccess = { url ->
                setLoading(false)
                checkoutLauncher.launch(CheckoutWebActivity.newIntent(this, url))
            },
            onError = { mensaje ->
                setLoading(false)
                if (desdeCompra) {
                    AlertDialog.Builder(this)
                        .setTitle("Pedido registrado")
                        .setMessage("Tu pedido quedó guardado, pero no pudimos iniciar el pago:\n$mensaje\n\nPuedes pagarlo desde la pestaña Pedidos.")
                        .setCancelable(false)
                        .setPositiveButton("Entendido") { _, _ -> finish() }
                        .show()
                } else {
                    mostrar(mensaje)
                }
            }
        )
    }

    /**
     * Tras cerrar el checkout, escucha los pedidos del grupo en Firestore hasta que dejen de estar
     * "pendiente de pago" (lo cambia el webhook) o se agote la espera.
     */
    private fun esperarConfirmacion() {
        val grupo = grupoId ?: return
        val uid = Session.user?.uid ?: return
        setLoading(true, "Confirmando tu pago…")
        ordersListener?.remove()
        ordersListener = OrderRepository.listenByBuyer(
            uid,
            onChange = { pedidos ->
                val delGrupo = pedidos.filter { it.grupoId == grupo }
                if (delGrupo.isNotEmpty() && delGrupo.none { it.estado == OrderStatus.PENDIENTE_PAGO }) {
                    detenerEspera()
                    mostrarPagoConfirmado(delGrupo)
                }
            },
            onError = { }
        )
        val limite = Runnable {
            detenerEspera()
            mostrarSinConfirmacion()
        }
        tiempoAgotado = limite
        handler.postDelayed(limite, ESPERA_CONFIRMACION_MS)
    }

    /** Cancela el temporizador y el listener de espera. */
    private fun detenerEspera() {
        tiempoAgotado?.let { handler.removeCallbacks(it) }
        tiempoAgotado = null
        ordersListener?.remove()
        ordersListener = null
        setLoading(false)
    }

    /** Aviso de pago aprobado. */
    private fun mostrarPagoConfirmado(pedidos: List<Order>) {
        AlertDialog.Builder(this)
            .setTitle("¡Pago confirmado!")
            .setMessage("Recibimos tu pago de ${Format.cop(pedidos.sumOf { it.total })}. Sigue el estado en la pestaña Pedidos.")
            .setCancelable(false)
            .setPositiveButton("Aceptar") { _, _ -> finish() }
            .show()
    }

    /** Aviso cuando ePayco aún no confirmó: puede ser un pago pendiente, rechazado o cancelado. */
    private fun mostrarSinConfirmacion() {
        AlertDialog.Builder(this)
            .setTitle("Pago sin confirmar")
            .setMessage("Aún no recibimos la confirmación de ePayco. Si ya pagaste, se reflejará en Pedidos en unos minutos. Si no, puedes reintentar.")
            .setCancelable(false)
            .setPositiveButton("Reintentar pago") { _, _ -> iniciarPago(desdeCompra = false) }
            .setNegativeButton("Ver mis pedidos") { _, _ -> finish() }
            .show()
    }

    // ---------------------------------------------------------------- Geolocalización

    /** Pide el permiso de ubicación si falta; si ya existe, obtiene la ubicación directamente. */
    private fun pedirUbicacion() {
        if (LocationHelper.hasPermission(this)) obtenerUbicacion()
        else permisoLauncher.launch(LocationHelper.PERMISOS)
    }

    /** Captura las coordenadas del GPS y rellena la dirección de entrega con la geocodificación inversa. */
    private fun obtenerUbicacion() {
        binding.btnUbicacion.isEnabled = false
        binding.btnUbicacion.text = "Buscando tu ubicación…"
        LocationHelper.getCurrentLocation(
            this,
            onSuccess = { ubicacion ->
                latitud = ubicacion.latitude
                longitud = ubicacion.longitude
                binding.tvUbicacionInfo.visibility = View.VISIBLE
                binding.tvUbicacionInfo.text = "Ubicación capturada (%.5f, %.5f)".format(latitud, longitud)
                LocationHelper.reverseGeocode(this, latitud, longitud) { direccion ->
                    restaurarBotonUbicacion()
                    if (direccion != null) binding.etDireccion.setText(direccion)
                    else mostrar("Capturamos tu ubicación, pero escribe la dirección manualmente.")
                }
            },
            onError = { mensaje ->
                restaurarBotonUbicacion()
                mostrar(mensaje)
            }
        )
    }

    /** Devuelve el botón de ubicación a su estado normal. */
    private fun restaurarBotonUbicacion() {
        binding.btnUbicacion.isEnabled = true
        binding.btnUbicacion.text = "Usar mi ubicación actual"
    }

    // ---------------------------------------------------------------- Utilidades

    /** Muestra el progreso con un texto de estado y bloquea el botón principal. */
    private fun setLoading(loading: Boolean, texto: String = "") {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.tvEstadoPago.visibility = if (loading) View.VISIBLE else View.GONE
        binding.tvEstadoPago.text = texto
        binding.btnPagar.isEnabled = !loading
    }

    private fun mostrar(mensaje: String) = Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()

    /** Libera listeners y temporizadores al cerrar la pantalla. */
    override fun onDestroy() {
        tiempoAgotado?.let { handler.removeCallbacks(it) }
        ordersListener?.remove()
        super.onDestroy()
    }
}
