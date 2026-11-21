package com.example.meeplekingdom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.meeplekingdom.data.ProductRepository
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.databinding.ActivityProductFormBinding
import com.example.meeplekingdom.model.Categorias
import com.example.meeplekingdom.model.Product
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.util.Navigator
import java.io.File

/**
 * Formulario del Vendedor para CREAR o EDITAR un producto, con foto tomada con la cámara
 * o elegida de la galería. La foto se comprime y se sube a Firebase Storage al guardar.
 * Si el Intent trae un id de producto, funciona en modo edición.
 */
class ProductFormActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductFormBinding

    /** Producto que se está editando (null en modo creación). */
    private var editing: Product? = null

    /** Foto nueva elegida por el usuario (null si conserva la actual). */
    private var selectedUri: Uri? = null

    /** Archivo temporal donde la cámara guarda la foto. */
    private var cameraUri: Uri? = null

    private val categorias = Categorias.LISTA

    /** Lanza la cámara del sistema (no requiere permiso de cámara en el manifest). */
    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { guardada ->
            if (guardada) cameraUri?.let { mostrarVistaPrevia(it) }
        }

    /** Lanza el selector de fotos del sistema (no requiere permisos de almacenamiento). */
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) mostrarVistaPrevia(uri)
        }

    companion object {
        private const val EXTRA_ID = "productId"
        private const val STATE_SELECTED = "selectedUri"
        private const val STATE_CAMERA = "cameraUri"

        /** Crea el Intent del formulario (con [productId] = edición, sin él = creación). */
        fun newIntent(context: Context, productId: String? = null): Intent =
            Intent(context, ProductFormActivity::class.java).apply {
                if (productId != null) putExtra(EXTRA_ID, productId)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Navigator.requireRole(this, Roles.VENDEDOR)) return
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.spCategoria.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias)

        // Recupera las fotos elegidas si la pantalla se recreó (rotación).
        savedInstanceState?.getString(STATE_CAMERA)?.let { cameraUri = Uri.parse(it) }
        savedInstanceState?.getString(STATE_SELECTED)?.let { mostrarVistaPrevia(Uri.parse(it)) }

        binding.btnVolverAdd.setOnClickListener { finish() }
        binding.btnCamara.setOnClickListener { abrirCamara() }
        binding.btnGaleria.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnGuardarProducto.setOnClickListener { guardar() }

        val productId = intent.getStringExtra(EXTRA_ID)
        if (productId != null) cargarProducto(productId)
    }

    /** Conserva las URIs de las fotos para sobrevivir a la rotación de pantalla. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED, selectedUri?.toString())
        outState.putString(STATE_CAMERA, cameraUri?.toString())
    }

    /** Modo edición: descarga el producto y rellena el formulario. El botón Guardar espera a que cargue. */
    private fun cargarProducto(id: String) {
        binding.tvTituloProducto.text = "Editar Producto"
        binding.btnGuardarProducto.text = "Guardar cambios"
        binding.btnGuardarProducto.isEnabled = false
        ProductRepository.get(
            id,
            onSuccess = { product ->
                if (product == null) {
                    Toast.makeText(this, "El producto ya no existe", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    rellenarFormulario(product)
                }
            },
            onError = { mensaje ->
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                finish()
            }
        )
    }

    /** Muestra en el formulario los datos del producto que se va a editar y habilita Guardar. */
    private fun rellenarFormulario(product: Product) {
        editing = product
        binding.etNombreProducto.setText(product.nombre)
        binding.etPrecioProducto.setText(product.precio.toLong().toString())
        binding.etStockProducto.setText(product.stock.toString())
        binding.etDescripcionProducto.setText(product.descripcion)
        binding.spCategoria.setSelection(categorias.indexOf(product.categoria).coerceAtLeast(0))
        binding.swVisible.isChecked = product.activo
        if (selectedUri == null && product.imagenUrl.isNotEmpty()) {
            Glide.with(this).load(product.imagenUrl).centerCrop().into(binding.imgPreview)
        }
        binding.btnGuardarProducto.isEnabled = true
    }

    /** Crea un archivo temporal en la caché y abre la cámara para que escriba la foto en él. */
    private fun abrirCamara() {
        val carpeta = File(cacheDir, "images").apply { mkdirs() }
        val archivo = File.createTempFile("producto_", ".jpg", carpeta)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", archivo)
        cameraUri = uri
        try {
            takePicture.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir la cámara en este dispositivo", Toast.LENGTH_LONG).show()
        }
    }

    /** Guarda la foto elegida y la muestra en la vista previa. */
    private fun mostrarVistaPrevia(uri: Uri) {
        selectedUri = uri
        Glide.with(this).load(uri).centerCrop().into(binding.imgPreview)
    }

    /** Valida el formulario y guarda el producto (y su foto) en Firestore/Storage. */
    private fun guardar() {
        val vendedor = Session.user ?: return
        val nombre = binding.etNombreProducto.text.toString().trim()
        val precio = binding.etPrecioProducto.text.toString().toDoubleOrNull()
        val stock = binding.etStockProducto.text.toString().toIntOrNull()
        val descripcion = binding.etDescripcionProducto.text.toString().trim()
        val categoria = categorias[binding.spCategoria.selectedItemPosition]

        when {
            nombre.length < 2 -> { binding.etNombreProducto.error = "Escribe el nombre del juego"; return }
            precio == null || precio <= 0 -> { binding.etPrecioProducto.error = "Precio inválido"; return }
            stock == null || stock < 0 -> { binding.etStockProducto.error = "Stock inválido"; return }
            descripcion.isEmpty() -> { binding.etDescripcionProducto.error = "Agrega una descripción"; return }
        }
        val sinFoto = editing?.imagenUrl.isNullOrEmpty() && selectedUri == null
        if (sinFoto) {
            Toast.makeText(this, "Agrega una foto con la cámara o la galería", Toast.LENGTH_LONG).show()
            return
        }

        val base = editing ?: Product(
            vendedorId = vendedor.uid,
            vendedorNombre = vendedor.nombre,
            creadoEn = System.currentTimeMillis()
        )
        val producto = base.copy(
            nombre = nombre,
            descripcion = descripcion,
            categoria = categoria,
            precio = precio ?: 0.0,
            stock = stock ?: 0,
            activo = binding.swVisible.isChecked
        )

        setLoading(true)
        ProductRepository.save(
            applicationContext, producto, selectedUri,
            onSuccess = {
                setLoading(false)
                Toast.makeText(this, "Producto guardado", Toast.LENGTH_SHORT).show()
                finish()
            },
            onError = { mensaje ->
                setLoading(false)
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
            }
        )
    }

    /** Muestra el progreso y bloquea el botón mientras se sube la foto y se guarda el producto. */
    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGuardarProducto.isEnabled = !loading
    }
}
