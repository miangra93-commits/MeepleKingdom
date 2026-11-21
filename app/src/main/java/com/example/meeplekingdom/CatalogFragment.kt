package com.example.meeplekingdom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.meeplekingdom.data.ProductRepository
import com.example.meeplekingdom.databinding.FragmentCatalogBinding
import com.example.meeplekingdom.model.Categorias
import com.example.meeplekingdom.model.Product
import com.example.meeplekingdom.ui.CatalogAdapter
import com.google.firebase.firestore.ListenerRegistration

/** Catálogo del comprador: productos publicados con búsqueda por texto y filtro por categoría. */
class CatalogFragment : Fragment() {
    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!
    private var listener: ListenerRegistration? = null

    /** Lista completa recibida de Firestore; el filtrado se hace sobre ella en memoria. */
    private var todos: List<Product> = emptyList()
    private lateinit var adapter: CatalogAdapter
    private val opcionesCategoria = listOf("Todas las categorías") + Categorias.LISTA

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CatalogAdapter { product ->
            startActivity(ProductDetailActivity.newIntent(requireContext(), product.id))
        }
        binding.rvCatalogo.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvCatalogo.adapter = adapter

        binding.spFiltroCategoria.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, opcionesCategoria
        )
        binding.spFiltroCategoria.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                aplicarFiltros()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.etBuscar.doAfterTextChanged { aplicarFiltros() }

        listener = ProductRepository.listenCatalog(
            onChange = { products ->
                todos = products
                aplicarFiltros()
            },
            onError = { mensaje ->
                binding.tvVacioCatalogo.visibility = View.VISIBLE
                binding.tvVacioCatalogo.text = mensaje
            }
        )
    }

    /** Filtra la lista por texto (nombre, descripción o vendedor) y categoría, y actualiza la pantalla. */
    private fun aplicarFiltros() {
        val b = _binding ?: return
        val texto = b.etBuscar.text.toString().trim().lowercase()
        val posicion = b.spFiltroCategoria.selectedItemPosition
        val categoria = if (posicion > 0) opcionesCategoria[posicion] else null

        val filtrados = todos.filter { p ->
            (categoria == null || p.categoria == categoria) &&
                (texto.isEmpty() ||
                    p.nombre.lowercase().contains(texto) ||
                    p.descripcion.lowercase().contains(texto) ||
                    p.vendedorNombre.lowercase().contains(texto))
        }
        adapter.submitList(filtrados)
        b.tvVacioCatalogo.visibility = if (filtrados.isEmpty()) View.VISIBLE else View.GONE
        b.tvVacioCatalogo.text =
            if (todos.isEmpty()) "Aún no hay productos publicados" else "Sin resultados para tu búsqueda"
    }

    /** Deja de escuchar Firestore y libera el binding al destruir la vista. */
    override fun onDestroyView() {
        listener?.remove()
        listener = null
        _binding = null
        super.onDestroyView()
    }
}
