package com.example.anjemgogogo.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.anjemgogogo.R
import com.example.anjemgogogo.databinding.FragmentProfileBinding

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var binding: FragmentProfileBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentProfileBinding.bind(view)

        // Setup Bottom Navigation
        binding.bottomNavigation.selectedItemId = R.id.nav_profile
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    findNavController().navigate(R.id.action_profile_to_home)
                    true
                }
                R.id.nav_orders -> {
                    findNavController().navigate(R.id.action_profile_to_orders)
                    true
                }
                R.id.nav_chat -> {
                    findNavController().navigate(R.id.action_profile_to_chat)
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }

        binding.btnWhatsapp.setOnClickListener {
            Toast.makeText(requireContext(), "Menghubungi Admin WhatsApp: 0812-3456-7890", Toast.LENGTH_SHORT).show()
        }

        binding.btnEditProfile.setOnClickListener {
            val input = EditText(requireContext())
            input.hint = "Masukkan nama baru..."
            input.setText(binding.tvProfileName.text)

            AlertDialog.Builder(requireContext())
                .setTitle("Edit Profil")
                .setView(input)
                .setPositiveButton("Simpan") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        binding.tvProfileName.text = newName
                        Toast.makeText(requireContext(), "Profil berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        binding.btnBantuan.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Pusat Bantuan Anjem UNNES")
                .setMessage("Layanan khusus ojek & kurir mahasiswa UNNES Sekaran.\nHubungi Admin Kampus: 0812-3456-7890 (WhatsApp)")
                .setPositiveButton("Tutup", null)
                .show()
        }

        binding.btnSyarat.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Syarat & Ketentuan")
                .setMessage("1. Layanan khusus mahasiswa & civitas akademika UNNES Sekaran.\n2. Pembayaran dapat menggunakan KampusPay atau Tunai.\n3. Harap menjaga sopan santun dan keselamatan bersama.")
                .setPositiveButton("Mengerti", null)
                .show()
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Keluar Akun")
                .setMessage("Apakah Anda yakin ingin keluar dari Anjem UNNES?")
                .setPositiveButton("Ya, Keluar") { _, _ ->
                    Toast.makeText(requireContext(), "Berhasil Keluar", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_profile_to_login)
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }
}
