package com.example.anjemgogogo.ui.orders

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.anjemgogogo.R
import com.example.anjemgogogo.databinding.FragmentOrdersBinding

class OrdersFragment : Fragment(R.layout.fragment_orders) {

    private lateinit var binding: FragmentOrdersBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentOrdersBinding.bind(view)

        // Setup Bottom Navigation
        binding.bottomNavigation.selectedItemId = R.id.nav_orders
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    findNavController().navigate(R.id.action_orders_to_home)
                    true
                }
                R.id.nav_orders -> true
                R.id.nav_chat -> {
                    findNavController().navigate(R.id.action_orders_to_chat)
                    true
                }
                R.id.nav_profile -> {
                    findNavController().navigate(R.id.action_orders_to_profile)
                    true
                }
                else -> false
            }
        }
    }
}
