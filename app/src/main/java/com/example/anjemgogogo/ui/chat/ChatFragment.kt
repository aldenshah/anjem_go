package com.example.anjemgogogo.ui.chat

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.anjemgogogo.R
import com.example.anjemgogogo.databinding.FragmentChatBinding
import com.example.anjemgogogo.ui.booking.OrderFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatFragment : Fragment(R.layout.fragment_chat) {

    private lateinit var binding: FragmentChatBinding
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentChatBinding.bind(view)

        binding.btnBackChat.setOnClickListener {
            if (!findNavController().navigateUp()) {
                findNavController().navigate(R.id.action_chat_to_home)
            }
        }

        binding.btnCallDriver.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:081234567890"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Menghubungi Driver Ahmad Fauzi (0812-3456-7890)...", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSendChat.setOnClickListener {
            val text = binding.etChatMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                addUserMessageBubble(text)
                binding.etChatMessage.setText("")
                simulateDriverReply()
            }
        }

        binding.chipReply1.setOnClickListener {
            addUserMessageBubble(binding.chipReply1.text.toString())
            simulateDriverReply()
        }

        binding.chipReply2.setOnClickListener {
            addUserMessageBubble(binding.chipReply2.text.toString())
            simulateDriverReply()
        }

        binding.chipReply3.setOnClickListener {
            addUserMessageBubble(binding.chipReply3.text.toString())
            simulateDriverReply()
        }

        startPostTripTimer()
    }

    private fun startPostTripTimer() {
        if (OrderFragment.tripPhase == OrderFragment.TripPhase.TRIP_COMPLETED) {
            timerRunnable = object : Runnable {
                override fun run() {
                    if (OrderFragment.postTripSecondsLeft > 0 && isAdded) {
                        binding.tvDriverName.text = "${OrderFragment.activeDriverName} (⏱️ ${OrderFragment.postTripSecondsLeft}s tersisa)"
                        timerHandler.postDelayed(this, 1000)
                    } else if (isAdded) {
                        binding.tvDriverName.text = "${OrderFragment.activeDriverName} (Sesi Chat Berakhir)"
                        binding.etChatMessage.isEnabled = false
                        binding.etChatMessage.hint = "Sesi chat 1 menit telah berakhir"
                        binding.btnSendChat.isEnabled = false
                        Toast.makeText(requireContext(), "Sesi chat 1 menit telah berakhir.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            timerHandler.post(timerRunnable!!)
        }
    }

    private fun addUserMessageBubble(message: String) {
        val bubbleLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                bottomMargin = 24
            }
            layoutParams = params
            setBackgroundResource(R.drawable.bg_bubble_user)
            setPadding(32, 20, 32, 20)
        }

        val tvMessage = TextView(requireContext()).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 13f
        }

        val tvTime = TextView(requireContext()).apply {
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 10f
            gravity = Gravity.END
        }

        bubbleLayout.addView(tvMessage)
        bubbleLayout.addView(tvTime)

        binding.chatMessageContainer.addView(bubbleLayout)
        binding.chatScrollView.post {
            binding.chatScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun simulateDriverReply() {
        binding.root.postDelayed({
            val replies = listOf(
                "Siap kak! Saya meluncur ke titik lokasi sekarang.",
                "Baik kak, posisi saya sudah dekat sekitar 200 meter lagi.",
                "Siap, saya tunggu di titik jemput depan pos ya kak.",
                "Oke kak, siap laksanakan!"
            )
            val driverMessage = replies.random()

            val bubbleLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.START
                    bottomMargin = 24
                }
                layoutParams = params
                setBackgroundResource(R.drawable.bg_bubble_driver)
                setPadding(32, 20, 32, 20)
            }

            val tvMessage = TextView(requireContext()).apply {
                text = driverMessage
                setTextColor(Color.parseColor("#0B2530"))
                textSize = 13f
            }

            val tvTime = TextView(requireContext()).apply {
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                setTextColor(Color.parseColor("#8FA8B4"))
                textSize = 10f
                gravity = Gravity.END
            }

            bubbleLayout.addView(tvMessage)
            bubbleLayout.addView(tvTime)

            if (isAdded) {
                binding.chatMessageContainer.addView(bubbleLayout)
                binding.chatScrollView.post {
                    binding.chatScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }, 1200)
    }

    override fun onDestroyView() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        super.onDestroyView()
    }
}
