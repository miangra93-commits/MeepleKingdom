package com.example.meeplekingdom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meeplekingdom.data.OrderRepository
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.databinding.FragmentOrdersBinding
import com.example.meeplekingdom.ui.OrderAdapter
import com.google.firebase.firestore.ListenerRegistration

/** Historial de pedidos del comprador, actualizado en tiempo real desde Firestore. */
class OrdersFragment : Fragment() {
    private var _binding: FragmentOrdersBinding? = null
    private val binding get() = _binding!!
    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uid = Session.user?.uid ?: return

        // "Pagar ahora" reabre el pago de un pedido que quedó pendiente (mismo grupo de pago).
        val adapter = OrderAdapter(
            OrderAdapter.Mode.BUYER,
            onPay = { pedido ->
                startActivity(PaymentActivity.newIntent(requireContext(), pedido.grupoId))
            }
        )
        binding.rvPedidos.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPedidos.adapter = adapter

        listener = OrderRepository.listenByBuyer(
            uid,
            onChange = { pedidos ->
                adapter.submitList(pedidos)
                _binding?.let { b ->
                    b.tvVacioPedidos.visibility = if (pedidos.isEmpty()) View.VISIBLE else View.GONE
                    b.tvVacioPedidos.text = "Aún no has realizado pedidos"
                }
            },
            onError = { mensaje ->
                _binding?.tvVacioPedidos?.visibility = View.VISIBLE
                _binding?.tvVacioPedidos?.text = mensaje
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
