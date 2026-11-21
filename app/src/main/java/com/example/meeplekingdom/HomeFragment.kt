package com.example.meeplekingdom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.meeplekingdom.data.ProductRepository
import com.example.meeplekingdom.databinding.FragmentHomeBinding
import com.example.meeplekingdom.ui.CatalogAdapter
import com.google.firebase.firestore.ListenerRegistration

/** Pantalla de inicio del comprador: banners y los productos más recientes del catálogo. */
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CatalogAdapter { product ->
            startActivity(ProductDetailActivity.newIntent(requireContext(), product.id))
        }
        binding.rvDestacados.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvDestacados.adapter = adapter

        // Los 4 productos más recientes (la lista ya llega ordenada por fecha).
        listener = ProductRepository.listenCatalog(
            onChange = { products ->
                adapter.submitList(products.take(4))
                binding.tvVacioDestacados.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
                binding.tvVacioDestacados.text = "Aún no hay productos publicados"
            },
            onError = { mensaje ->
                binding.tvVacioDestacados.visibility = View.VISIBLE
                binding.tvVacioDestacados.text = mensaje
            }
        )
    }

    /** Deja de escuchar Firestore y libera el binding al destruir la vista. */
    override fun onDestroyView() {
        listener?.remove()
        listener = null
        _binding = null
        super.onDestroyView()
    }
}
