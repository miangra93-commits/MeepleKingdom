package com.example.meeplekingdom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.meeplekingdom.data.Session
import com.example.meeplekingdom.databinding.FragmentProfileBinding
import com.example.meeplekingdom.model.Roles
import com.example.meeplekingdom.util.BiometricAuthHelper
import com.example.meeplekingdom.util.Navigator

/** Perfil del comprador: muestra sus datos, permite desactivar la huella y cerrar sesión. */
class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Session.user?.let { user ->
            binding.tvNombrePerfil.text = user.nombre
            binding.tvEmailPerfil.text = user.email
            binding.tvRolPerfil.text = Roles.etiqueta(user.rol)
        }

        actualizarBotonHuella()
        binding.btnDesactivarHuella.setOnClickListener {
            BiometricAuthHelper.clear(requireContext())
            Toast.makeText(requireContext(), "Acceso con huella desactivado", Toast.LENGTH_SHORT).show()
            actualizarBotonHuella()
        }
        binding.btnCerrarSesion.setOnClickListener { Navigator.logout(requireActivity()) }
    }

    /** Muestra el botón de desactivar huella solo si hay credenciales guardadas. */
    private fun actualizarBotonHuella() {
        binding.btnDesactivarHuella.visibility =
            if (BiometricAuthHelper.hasCredentials(requireContext())) View.VISIBLE else View.GONE
    }

    /** Libera el binding para evitar fugas de memoria cuando se destruye la vista. */
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
