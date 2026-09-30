package com.example.anjemgogogo.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.anjemgogogo.R
import com.example.anjemgogogo.databinding.FragmentLoginBinding

class LoginFragment : Fragment(R.layout.fragment_login) {

    private lateinit var binding: FragmentLoginBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentLoginBinding.bind(view)

        // Tombol Login: Apapun isinya (atau bahkan kosong), langsung masuk ke Home
        binding.btnLogin.setOnClickListener {
            Toast.makeText(requireContext(), "Login Berhasil!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_login_to_home)
        }

        // Jika ada tombol "Daftar" atau bypass lainnya, buat juga agar langsung ke Home
        binding.btnToRegister?.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_home)
        }
    }
}