package com.example.meeplekingdom

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meeplekingdom.data.CartRepository
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.databinding.FragmentCartBinding
import com.example.meeplekingdom.ui.CartAdapter
import com.example.meeplekingdom.util.Format
import com.google.firebase.firestore.ListenerRegistration

/** Carrito del comprador: cambia cantidades, quita productos y continúa al pago. */
class CartFragment : Fragment() {
    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uid = Session.user?.uid ?: return

        val adapter = CartAdapter(
            onMinus = { item ->
                if (item.cantidad > 1) CartRepository.setQuantity(uid, item.productId, item.cantidad - 1) { mostrarError(it) }
            },
            onPlus = { item ->
                if (item.cantidad >= item.stock) {
                    mostrarError("No hay más unidades disponibles de ${item.nombre}")
                } else {
                    CartRepository.setQuantity(uid, item.productId, item.cantidad + 1) { mostrarError(it) }
                }
            },
            onRemove = { item -> CartRepository.remove(uid, item.productId) { mostrarError(it) } }
        )
        binding.rvCarrito.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCarrito.adapter = adapter

        binding.btnIrAPagar.setOnClickListener {
            startActivity(Intent(requireContext(), PaymentActivity::class.java))
        }

        listener = CartRepository.listen(
            uid,
            onChange = { items ->
                adapter.submitList(items)
                _binding?.let { b ->
                    val total = items.sumOf { it.precio * it.cantidad }
                    b.tvTotalCarrito.text = "Total: ${Format.cop(total)}"
                    b.tvVacioCarrito.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    b.btnIrAPagar.isEnabled = items.isNotEmpty()
                }
            },
            onError = { mostrarError(it) }
        )
    }

    /** Muestra un mensaje de error si el fragmento sigue visible. */
    private fun mostrarError(mensaje: String) {
        context?.let { Toast.makeText(it, mensaje, Toast.LENGTH_LONG).show() }
    }

    /** Deja de escuchar Firestore y libera el binding al destruir la vista. */
    override fun onDestroyView() {
        listener?.remove()
        listener = null
        _binding = null
        super.onDestroyView()
    }
}
