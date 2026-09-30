package com.example.anjemgogogo.ui.main

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.anjemgogogo.R
import com.example.anjemgogogo.databinding.FragmentHomeBinding
import com.example.anjemgogogo.ui.booking.OrderFragment

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var currentBalance = 45000

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateBalanceDisplay()

        // Setup Bottom Navigation
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_orders -> {
                    findNavController().navigate(R.id.action_home_to_orders)
                    true
                }
                R.id.nav_chat -> {
                    findNavController().navigate(R.id.action_home_to_chat)
                    true
                }
                R.id.nav_profile -> {
                    findNavController().navigate(R.id.action_home_to_profile)
                    true
                }
                else -> false
            }
        }

        // Listener untuk tombol Anjem Motor
        binding.btnAnjemMotor.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_order)
        }

        // Listener untuk tombol Kurir / Titip Beli
        binding.btnKurir.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_order)
        }

        // Listener untuk Titik Populer
        val popularClickListener = View.OnClickListener {
            findNavController().navigate(R.id.action_home_to_order)
        }
        binding.btnGerbangUtama.setOnClickListener(popularClickListener)
        binding.btnFakultasTeknik.setOnClickListener(popularClickListener)
        binding.btnBanaranKos.setOnClickListener(popularClickListener)
        binding.btnRektorat.setOnClickListener(popularClickListener)

        binding.btnNotification.setOnClickListener {
            Toast.makeText(requireContext(), "🔔 Tidak ada notifikasi baru", Toast.LENGTH_SHORT).show()
        }

        // Listener untuk Top Up
        binding.btnTopUp.setOnClickListener {
            showTopUpDialog()
        }

        binding.btnOpenPostTripChat.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_chat)
        }
    }

    override fun onResume() {
        super.onResume()
        updateBalanceDisplay()

        // Check 60-second post-trip chat banner on Home
        if (OrderFragment.tripPhase == OrderFragment.TripPhase.TRIP_COMPLETED && OrderFragment.postTripSecondsLeft > 0) {
            binding.cardPostTripChat.visibility = View.VISIBLE
            binding.tvPostTripTimer.text = "Hubungi driver selama tersisa ${OrderFragment.postTripSecondsLeft} detik"
            binding.btnOpenPostTripChat.text = "Chat (${OrderFragment.postTripSecondsLeft}s)"
        } else {
            binding.cardPostTripChat.visibility = View.GONE
        }
    }

    private fun updateBalanceDisplay() {
        binding.tvSaldo.text = "Rp ${String.format(java.util.Locale.US, "%,d", currentBalance).replace(',', '.')}"
    }

    private fun showTopUpDialog() {
        val options = arrayOf("Rp 20.000", "Rp 50.000", "Rp 100.000", "Rp 200.000")
        val amounts = intArrayOf(20000, 50000, 100000, 200000)

        AlertDialog.Builder(requireContext())
            .setTitle("Top Up KampusPay")
            .setItems(options) { _, which ->
                val addedAmount = amounts[which]
                currentBalance += addedAmount
                updateBalanceDisplay()
                Toast.makeText(requireContext(), "Berhasil Top Up ${options[which]}!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
