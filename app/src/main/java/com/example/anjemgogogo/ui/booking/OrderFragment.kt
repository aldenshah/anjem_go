package com.example.anjemgogogo.ui.booking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.anjemgogogo.R
import com.example.anjemgogogo.databinding.FragmentOrderBinding
import com.example.anjemgogogo.utils.H3GeoUtils
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class OrderFragment : Fragment(R.layout.fragment_order) {

    enum class TripPhase {
        IDLE,
        WAITING_CONFIRMATION,
        DRIVER_TO_PICKUP,
        TRIP_TO_DESTINATION,
        TRIP_COMPLETED
    }

    companion object {
        var tripPhase = TripPhase.IDLE
        var activeDriverName = "Ahmad Fauzi"
        var activePlateNumber = "H 2341 XY"
        var activeServiceName = "Anjem Motor"
        var activeJemput = ""
        var activeTujuan = ""
        var activeCatatan = ""
        var postTripSecondsLeft = 30
    }

    private lateinit var binding: FragmentOrderBinding
    private var selectedServiceType = "Motor" // "Motor" or "Kurir"
    private var isSatelliteMode = false // Toggle between Street & Satellite

    // Dynamic coordinates chosen by user (Defaults to GPS)
    private var pickupGeoPoint = GeoPoint(-7.0475, 110.4072) // Gerbang Utama UNNES
    private var destinationGeoPoint = GeoPoint(-7.0531, 110.4085) // Banaran

    private var targetSettingField = "jemput" // "jemput" or "tujuan"
    private var isProgrammaticTextUpdate = false

    private var markerPickup: Marker? = null
    private var markerDest: Marker? = null
    private var markerDriver: Marker? = null
    private var routeLine: Polyline? = null
    private var currentWaypoints: List<GeoPoint> = emptyList()

    // Live tracking animation state
    private var isTrackingActive = false
    private val trackingHandler = Handler(Looper.getMainLooper())
    private var trackingRunnable: Runnable? = null
    private var postTripHandler = Handler(Looper.getMainLooper())
    private var postTripRunnable: Runnable? = null

    // Debounce Handler to prevent map scrolling lag
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingDebounceRunnable: Runnable? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            moveToCurrentGpsLocation()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentOrderBinding.bind(view)

        // Initialize OSMDroid Configuration with valid package User-Agent to comply with tile policy
        val ctx = requireContext().applicationContext
        Configuration.getInstance().userAgentValue = "Mozilla/5.0 (Android; Mobile) AnjemUNNES/1.0 (" + ctx.packageName + ")"
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        setupMapView()
        setupServiceToggle()
        setupEditorActionListeners()
        setupOverlayChatListeners()

        // Activate GPS location for default pickup
        checkLocationPermissionAndMove()

        binding.btnToggleMapStyle.setOnClickListener {
            isSatelliteMode = !isSatelliteMode
            if (isSatelliteMode) {
                setSatelliteMapMode()
                Toast.makeText(requireContext(), "🛰️ Mode Peta Satelit HD Aktif", Toast.LENGTH_SHORT).show()
            } else {
                setStreetMapMode()
                Toast.makeText(requireContext(), "🗺️ Mode Peta Google Street HD Aktif", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnUseMapForJemput.setOnClickListener {
            targetSettingField = "jemput"
            Toast.makeText(requireContext(), "🟢 Geser peta untuk tentukan Titik Jemput", Toast.LENGTH_SHORT).show()
        }

        binding.btnUseMapForTujuan.setOnClickListener {
            targetSettingField = "tujuan"
            Toast.makeText(requireContext(), "🔴 Geser peta untuk tentukan Titik Tujuan", Toast.LENGTH_SHORT).show()
        }

        binding.fabMyLocation.setOnClickListener {
            checkLocationPermissionAndMove()
        }

        binding.btnBatal.setOnClickListener {
            resetTripState()
            findNavController().navigateUp()
        }

        binding.btnPesan.setOnClickListener {
            val jemput = binding.spJemput.text.toString().trim()
            val tujuan = binding.spTujuan.text.toString().trim()

            if (jemput.isEmpty() || tujuan.isEmpty()) {
                Toast.makeText(requireContext(), "Harap isi titik jemput dan tujuan terlebih dahulu!", Toast.LENGTH_SHORT).show()
            } else if (jemput.equals(tujuan, ignoreCase = true)) {
                Toast.makeText(requireContext(), "Titik jemput dan tujuan tidak boleh sama!", Toast.LENGTH_SHORT).show()
            } else {
                startOrderMatchingWorkflow()
            }
        }
    }

    private fun setupOverlayChatListeners() {
        binding.tvOverlayDriverName.text = "$activeDriverName (★ 4.9)"

        binding.btnBackOverlayChat.setOnClickListener {
            binding.chatOverlayContainer.visibility = View.GONE
            showInitialConfirmationDialog()
        }

        binding.btnOverlaySendChat.setOnClickListener {
            val text = binding.etOverlayChatMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                addOverlayUserMessageBubble(text)
                binding.etOverlayChatMessage.setText("")
                simulateOverlayDriverReply()
            }
        }

        binding.chipOverlayReply1.setOnClickListener {
            addOverlayUserMessageBubble(binding.chipOverlayReply1.text.toString())
            simulateOverlayDriverReply()
        }

        binding.chipOverlayReply2.setOnClickListener {
            addOverlayUserMessageBubble(binding.chipOverlayReply2.text.toString())
            simulateOverlayDriverReply()
        }
    }

    private fun addOverlayUserMessageBubble(message: String) {
        val bubbleLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                bottomMargin = 16
            }
            layoutParams = params
            setBackgroundResource(R.drawable.bg_bubble_user)
            setPadding(28, 16, 28, 16)
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

        binding.chatOverlayMessageContainer.addView(bubbleLayout)
        binding.chatOverlayScrollView.post {
            binding.chatOverlayScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun simulateOverlayDriverReply() {
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
                    bottomMargin = 16
                }
                layoutParams = params
                setBackgroundResource(R.drawable.bg_bubble_driver)
                setPadding(28, 16, 28, 16)
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
                binding.chatOverlayMessageContainer.addView(bubbleLayout)
                binding.chatOverlayScrollView.post {
                    binding.chatOverlayScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }, 1000)
    }

    override fun onResume() {
        super.onResume()
        if (isAdded && !isDetached) {
            binding.osmMapView.onResume()
            if (tripPhase == TripPhase.WAITING_CONFIRMATION && binding.chatOverlayContainer.visibility != View.VISIBLE) {
                view?.postDelayed({
                    if (isAdded && !isDetached && tripPhase == TripPhase.WAITING_CONFIRMATION) {
                        showInitialConfirmationDialog()
                    }
                }, 250)
            }
        }
    }

    private fun startOrderMatchingWorkflow() {
        activeJemput = binding.spJemput.text.toString().trim()
        activeTujuan = binding.spTujuan.text.toString().trim()
        activeCatatan = binding.etCatatan.text.toString().trim()
        activeServiceName = if (selectedServiceType == "Motor") "Anjem Motor" else "Kurir / Titip Beli"

        val driverNames = listOf("Ahmad Fauzi", "Bayu Pratama", "Rizki Mahendra", "Eko Prasetyo")
        val plates = listOf("H 2341 XY", "H 5432 AB", "H 1289 ZC", "H 9876 KL")
        val randomIdx = (0 until driverNames.size).random()
        activeDriverName = driverNames[randomIdx]
        activePlateNumber = plates[randomIdx]

        tripPhase = TripPhase.WAITING_CONFIRMATION
        showInitialConfirmationDialog()
    }

    private fun showInitialConfirmationDialog() {
        if (!isAdded || isDetached || binding.chatOverlayContainer.visibility == View.VISIBLE) return

        val message = """
            Driver $activeServiceName Ditemukan!
            
            👤 Driver: $activeDriverName (★ 4.9)
            🏍️ Kendaraan: Honda Beat ($activePlateNumber)
            
            📍 Jemput: $activeJemput
            🏁 Tujuan: $activeTujuan
            ${if (activeCatatan.isNotBlank()) "📝 Catatan: $activeCatatan" else ""}
            
            Silakan mulai perjalanan atau hubungi driver.
        """.trimIndent()

        try {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Konfirmasi Pesanan $activeServiceName")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("🚀 Mulai Perjalanan") { dialog, _ ->
                    dialog.dismiss()
                    tripPhase = TripPhase.DRIVER_TO_PICKUP
                    startRandomDriverToPickupAnimation()
                }
                .setNeutralButton("💬 Chat Driver") { dialog, _ ->
                    dialog.dismiss()
                    binding.chatOverlayContainer.visibility = View.VISIBLE
                }
                .setNegativeButton("❌ Batalkan Pesanan") { dialog, _ ->
                    resetTripState()
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Pesanan dibatalkan.", Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startRandomDriverToPickupAnimation() {
        val randomLatOffset = (Math.random() - 0.5) * 0.009
        val randomLngOffset = (Math.random() - 0.5) * 0.009
        val driverStartPoint = GeoPoint(pickupGeoPoint.latitude + randomLatOffset, pickupGeoPoint.longitude + randomLngOffset)

        val pickupWaypoints = mutableListOf<GeoPoint>()
        val steps = 8
        for (i in 0..steps) {
            val fraction = i.toDouble() / steps
            val lat = driverStartPoint.latitude + (pickupGeoPoint.latitude - driverStartPoint.latitude) * fraction
            val lng = driverStartPoint.longitude + (pickupGeoPoint.longitude - driverStartPoint.longitude) * fraction
            pickupWaypoints.add(GeoPoint(lat, lng))
        }

        isTrackingActive = true
        var stepIndex = 0

        if (markerDriver == null) {
            markerDriver = Marker(binding.osmMapView).apply {
                title = "🛵 Driver: $activeDriverName ($activePlateNumber)"
                snippet = "Menuju titik jemput Anda (maks 0.5 km)"
            }
        }

        if (!binding.osmMapView.overlays.contains(markerDriver)) {
            binding.osmMapView.overlays.add(markerDriver)
        }

        Toast.makeText(requireContext(), "🛵 Driver $activeDriverName meluncur ke titik jemput Anda...", Toast.LENGTH_LONG).show()

        trackingRunnable = object : Runnable {
            override fun run() {
                if (!isTrackingActive || !isAdded || stepIndex >= pickupWaypoints.size) {
                    startTripToDestinationAnimation()
                    return
                }

                val currentPos = pickupWaypoints[stepIndex]
                markerDriver?.position = currentPos
                binding.osmMapView.controller.animateTo(currentPos)
                binding.osmMapView.invalidate()

                binding.tvMapH3Info.text = "🛵 Driver $activeDriverName menuju titik jemput..."

                stepIndex++
                trackingHandler.postDelayed(this, 180)
            }
        }

        trackingHandler.post(trackingRunnable!!)
    }

    private fun startTripToDestinationAnimation() {
        tripPhase = TripPhase.TRIP_TO_DESTINATION
        if (currentWaypoints.isEmpty()) {
            currentWaypoints = listOf(pickupGeoPoint, destinationGeoPoint)
        }

        isTrackingActive = true
        var stepIndex = 0

        if (markerDriver == null) {
            markerDriver = Marker(binding.osmMapView).apply {
                title = "🛵 Driver: $activeDriverName ($activePlateNumber)"
                snippet = "Menuju tujuan"
            }
        }

        if (!binding.osmMapView.overlays.contains(markerDriver)) {
            binding.osmMapView.overlays.add(markerDriver)
        }

        trackingRunnable = object : Runnable {
            override fun run() {
                if (!isTrackingActive || !isAdded || stepIndex >= currentWaypoints.size) {
                    onTripCompleted()
                    return
                }

                val currentPos = currentWaypoints[stepIndex]
                markerDriver?.position = currentPos
                binding.osmMapView.controller.animateTo(currentPos)
                binding.osmMapView.invalidate()

                val percent = (stepIndex + 1) * 100 / currentWaypoints.size
                binding.tvMapH3Info.text = "🛵 Perjalanan ke Tujuan • $percent%"

                stepIndex++
                trackingHandler.postDelayed(this, 150)
            }
        }

        trackingHandler.post(trackingRunnable!!)
    }

    private fun onTripCompleted() {
        stopLiveDriverAnimation()
        tripPhase = TripPhase.TRIP_COMPLETED
        postTripSecondsLeft = 30

        binding.tvMapH3Info.text = "🎉 Perjalanan Selesai! Terima kasih."

        postTripRunnable = object : Runnable {
            override fun run() {
                if (postTripSecondsLeft > 0 && tripPhase == TripPhase.TRIP_COMPLETED) {
                    postTripSecondsLeft--
                    postTripHandler.postDelayed(this, 1000)
                } else {
                    tripPhase = TripPhase.IDLE
                }
            }
        }
        postTripHandler.post(postTripRunnable!!)

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Perjalanan Selesai! 🎉")
            .setMessage("Anda telah tiba di tujuan ($activeTujuan).\n\nTombol Chat Driver pos-perjalanan (30 dtk) tersedia di menu Home.")
            .setPositiveButton("Selesai & Beri Ulasan") { _, _ ->
                resetTripState()
                findNavController().navigate(R.id.action_order_to_chat)
            }
            .show()
    }

    private fun resetTripState() {
        tripPhase = TripPhase.IDLE
        stopLiveDriverAnimation()
        postTripRunnable?.let { postTripHandler.removeCallbacks(it) }
    }

    private fun stopLiveDriverAnimation() {
        isTrackingActive = false
        trackingRunnable?.let { trackingHandler.removeCallbacks(it) }
        markerDriver?.let { binding.osmMapView.overlays.remove(it) }
        binding.osmMapView.invalidate()
    }

    private fun setupEditorActionListeners() {
        binding.spJemput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val text = v.text.toString().trim()
                if (text.isNotEmpty()) {
                    geocodeTypedTextAndMoveMap(text, isJemput = true)
                }
                hideKeyboard(v)
                true
            } else {
                false
            }
        }

        binding.spTujuan.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val text = v.text.toString().trim()
                if (text.isNotEmpty()) {
                    geocodeTypedTextAndMoveMap(text, isJemput = false)
                }
                hideKeyboard(v)
                true
            } else {
                false
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun geocodeTypedTextAndMoveMap(text: String, isJemput: Boolean) {
        val campusMap = mapOf(
            "Gerbang Utama" to GeoPoint(-7.0475, 110.4072),
            "Banaran" to GeoPoint(-7.0531, 110.4085),
            "Fakultas Teknik" to GeoPoint(-7.0490, 110.4060),
            "Teknik" to GeoPoint(-7.0490, 110.4060),
            "Gedung Rektorat" to GeoPoint(-7.0482, 110.4080),
            "Rektorat" to GeoPoint(-7.0482, 110.4080),
            "Fakultas MIPA" to GeoPoint(-7.0465, 110.4050),
            "MIPA" to GeoPoint(-7.0465, 110.4050),
            "Fakultas Bahasa" to GeoPoint(-7.0460, 110.4088),
            "Asrama" to GeoPoint(-7.0515, 110.4110),
            "Perpustakaan" to GeoPoint(-7.0478, 110.4075)
        )

        val matchedKey = campusMap.keys.find { text.contains(it, ignoreCase = true) }
        if (matchedKey != null) {
            val point = campusMap[matchedKey]!!
            if (isJemput) {
                pickupGeoPoint = point
            } else {
                destinationGeoPoint = point
            }
            binding.osmMapView.controller.animateTo(point)
            updateMapOverlaysAndRoute()
            Toast.makeText(requireContext(), "📍 Peta diarahkan ke $matchedKey", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val query = if (text.contains("UNNES", true) || text.contains("Semarang", true)) text else "$text, UNNES, Semarang"
                val addresses = geocoder.getFromLocationName(query, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val point = GeoPoint(addr.latitude, addr.longitude)
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            if (isJemput) {
                                pickupGeoPoint = point
                            } else {
                                destinationGeoPoint = point
                            }
                            binding.osmMapView.controller.animateTo(point)
                            updateMapOverlaysAndRoute()
                            Toast.makeText(requireContext(), "📍 Peta diarahkan ke lokasi yang dicari", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun setupMapView() {
        setStreetMapMode()
        binding.osmMapView.setMultiTouchControls(true)
        binding.osmMapView.isTilesScaledToDpi = true
        binding.osmMapView.maxZoomLevel = 19.0
        binding.osmMapView.minZoomLevel = 4.0
        binding.osmMapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

        val controller = binding.osmMapView.controller
        controller.setZoom(16.5)
        controller.setCenter(pickupGeoPoint)

        binding.osmMapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                scheduleDebouncedMapUpdate()
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                scheduleDebouncedMapUpdate()
                return true
            }
        })

        updateMapOverlaysAndRoute()
    }

    private val googleMapsStreetSource = object : OnlineTileSourceBase(
        "GoogleMapsStreet",
        0, 20, 256, ".png",
        arrayOf(
            "https://mt0.google.com/vt/lyrs=m&x=",
            "https://mt1.google.com/vt/lyrs=m&x=",
            "https://mt2.google.com/vt/lyrs=m&x=",
            "https://mt3.google.com/vt/lyrs=m&x="
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return baseUrl + MapTileIndex.getX(pMapTileIndex) +
                    "&y=" + MapTileIndex.getY(pMapTileIndex) +
                    "&z=" + MapTileIndex.getZoom(pMapTileIndex)
        }
    }

    private val googleMapsSatelliteSource = object : OnlineTileSourceBase(
        "GoogleMapsSatellite",
        0, 20, 256, ".png",
        arrayOf(
            "https://mt0.google.com/vt/lyrs=y&x=",
            "https://mt1.google.com/vt/lyrs=y&x=",
            "https://mt2.google.com/vt/lyrs=y&x=",
            "https://mt3.google.com/vt/lyrs=y&x="
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return baseUrl + MapTileIndex.getX(pMapTileIndex) +
                    "&y=" + MapTileIndex.getY(pMapTileIndex) +
                    "&z=" + MapTileIndex.getZoom(pMapTileIndex)
        }
    }

    private fun setStreetMapMode() {
        binding.osmMapView.tileProvider.clearTileCache()
        binding.osmMapView.setTileSource(googleMapsStreetSource)
        binding.osmMapView.invalidate()
    }

    private fun setSatelliteMapMode() {
        binding.osmMapView.tileProvider.clearTileCache()
        binding.osmMapView.setTileSource(googleMapsSatelliteSource)
        binding.osmMapView.invalidate()
    }

    private fun scheduleDebouncedMapUpdate() {
        if (isTrackingActive || tripPhase != TripPhase.IDLE) return
        pendingDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDebounceRunnable = Runnable {
            onMapMoved()
        }
        mainHandler.postDelayed(pendingDebounceRunnable!!, 400)
    }

    private fun checkLocationPermissionAndMove() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            moveToCurrentGpsLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun moveToCurrentGpsLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentPoint = GeoPoint(it.latitude, it.longitude)
                    binding.osmMapView.controller.animateTo(currentPoint)
                    pickupGeoPoint = currentPoint
                    val address = getAddressFromGeoPoint(pickupGeoPoint)
                    setTextProgrammatically(isJemput = true, address)
                    updateMapOverlaysAndRoute()
                    Toast.makeText(requireContext(), "📍 Titik jemput default diatur dari lokasi GPS Anda", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun setTextProgrammatically(isJemput: Boolean, text: String) {
        isProgrammaticTextUpdate = true
        if (isJemput) {
            binding.spJemput.setText(text)
        } else {
            binding.spTujuan.setText(text)
        }
        isProgrammaticTextUpdate = false
    }

    private fun onMapMoved() {
        if (isTrackingActive || tripPhase != TripPhase.IDLE) return
        val center = binding.osmMapView.mapCenter as GeoPoint
        val address = getAddressFromGeoPoint(center)
        val h3Index = H3GeoUtils.getH3Index(center.latitude, center.longitude)

        if (targetSettingField == "jemput") {
            pickupGeoPoint = center
            setTextProgrammatically(isJemput = true, address)
        } else {
            destinationGeoPoint = center
            setTextProgrammatically(isJemput = false, address)
        }

        binding.tvMapH3Info.text = "⚡ H3 Index: $h3Index"
        updateMapOverlaysAndRoute()
    }

    private fun updateMapOverlaysAndRoute() {
        binding.osmMapView.overlays.clear()

        // 1. Add Pickup Marker (🟢 Green)
        markerPickup = Marker(binding.osmMapView)
        markerPickup?.position = pickupGeoPoint
        markerPickup?.title = "🟢 Jemput: ${binding.spJemput.text}"
        binding.osmMapView.overlays.add(markerPickup)

        // 2. Add Destination Marker (🔴 Red)
        markerDest = Marker(binding.osmMapView)
        markerDest?.position = destinationGeoPoint
        markerDest?.title = "🔴 Tujuan: ${binding.spTujuan.text}"
        binding.osmMapView.overlays.add(markerDest)

        // Re-add Driver Marker if tracking is active
        markerDriver?.let {
            if (!binding.osmMapView.overlays.contains(it)) {
                binding.osmMapView.overlays.add(it)
            }
        }

        // 3. Fetch Real Turn-by-Turn Road Route (OSRM Engine)
        fetchOSRMDriverRoute(pickupGeoPoint, destinationGeoPoint)
    }

    private fun fetchOSRMDriverRoute(start: GeoPoint, end: GeoPoint) {
        Thread {
            val waypoints = mutableListOf<GeoPoint>()
            var distanceMeters = 0.0
            var success = false

            val endpoints = listOf(
                "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson",
                "https://routing.openstreetmap.de/routed-car/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
            )

            for (urlString in endpoints) {
                try {
                    val url = URL(urlString)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; AnjemUNNES/1.0)")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000

                    if (conn.responseCode == 200) {
                        val stream = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(stream)
                        val routes = json.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            distanceMeters = route.getDouble("distance")
                            val geometry = route.getJSONObject("geometry")
                            val coordinates = geometry.getJSONArray("coordinates")

                            for (i in 0 until coordinates.length()) {
                                val coord = coordinates.getJSONArray(i)
                                val lng = coord.getDouble(0)
                                val lat = coord.getDouble(1)
                                waypoints.add(GeoPoint(lat, lng))
                            }
                            success = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (success && waypoints.isNotEmpty()) {
                currentWaypoints = waypoints
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        drawRoadRoutePolyline(waypoints, distanceMeters)
                    }
                }
            } else {
                val curvedWaypoints = generateRoadInterpolatedWaypoints(start, end)
                currentWaypoints = curvedWaypoints
                val results = FloatArray(1)
                android.location.Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        drawRoadRoutePolyline(curvedWaypoints, results[0].toDouble() * 1.22)
                    }
                }
            }
        }.start()
    }

    private fun generateRoadInterpolatedWaypoints(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        val list = mutableListOf<GeoPoint>()
        list.add(start)

        val steps = 10
        for (i in 1 until steps) {
            val fraction = i.toDouble() / steps
            val lat = start.latitude + (end.latitude - start.latitude) * fraction + (0.0005 * sin(fraction * Math.PI))
            val lng = start.longitude + (end.longitude - start.longitude) * fraction + (0.0005 * cos(fraction * Math.PI))
            list.add(GeoPoint(lat, lng))
        }

        list.add(end)
        return list
    }

    private fun drawRoadRoutePolyline(waypoints: List<GeoPoint>, roadDistanceMeters: Double) {
        routeLine = Polyline()
        routeLine?.setPoints(waypoints)
        routeLine?.outlinePaint?.color = Color.parseColor("#0087A8")
        routeLine?.outlinePaint?.strokeWidth = 14f
        routeLine?.outlinePaint?.strokeCap = Paint.Cap.ROUND
        routeLine?.outlinePaint?.strokeJoin = Paint.Join.ROUND

        binding.osmMapView.overlays.add(routeLine)
        binding.osmMapView.invalidate()

        calculateEstimateForDistance(roadDistanceMeters / 1000.0)
    }

    private fun getAddressFromGeoPoint(geoPoint: GeoPoint): String {
        return try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val street = addr.featureName ?: addr.thoroughfare ?: "Lokasi Sekaran"
                val subLocality = addr.subLocality ?: addr.locality ?: "UNNES"
                "$street, $subLocality"
            } else {
                "Area Sekaran (${String.format(Locale.US, "%.4f", geoPoint.latitude)}, ${String.format(Locale.US, "%.4f", geoPoint.longitude)})"
            }
        } catch (e: Exception) {
            "Kampus UNNES (${String.format(Locale.US, "%.4f", geoPoint.latitude)}, ${String.format(Locale.US, "%.4f", geoPoint.longitude)})"
        }
    }

    private fun setupServiceToggle() {
        val primaryColor = Color.parseColor("#0087A8")
        val grayColor = Color.parseColor("#557382")

        binding.btnTypeMotor.setOnClickListener {
            selectedServiceType = "Motor"
            binding.btnTypeMotor.setBackgroundResource(R.drawable.bg_selected_primary)
            binding.imgMotor.imageTintList = ColorStateList.valueOf(primaryColor)
            binding.tvMotor.setTextColor(primaryColor)

            binding.btnTypeKurir.setBackgroundResource(R.drawable.bg_border_card)
            binding.imgKurir.imageTintList = ColorStateList.valueOf(grayColor)
            binding.tvKurir.setTextColor(grayColor)

            binding.lblCatatan.text = "CATATAN UNTUK DRIVER (OPSIONAL)"
            binding.etCatatan.hint = "Misal: Saya pakai jaket hitam, tunggu di depan pos..."
            updateMapOverlaysAndRoute()
        }

        binding.btnTypeKurir.setOnClickListener {
            selectedServiceType = "Kurir"
            binding.btnTypeKurir.setBackgroundResource(R.drawable.bg_selected_primary)
            binding.imgKurir.imageTintList = ColorStateList.valueOf(primaryColor)
            binding.tvKurir.setTextColor(primaryColor)

            binding.btnTypeMotor.setBackgroundResource(R.drawable.bg_border_card)
            binding.imgMotor.imageTintList = ColorStateList.valueOf(grayColor)
            binding.tvMotor.setTextColor(grayColor)

            binding.lblCatatan.text = "DETAIL BARANG / TITIP BELI (CATATAN)"
            binding.etCatatan.hint = "Misal: Beli Nasi Padang di Banaran & Es Teh manis"
            updateMapOverlaysAndRoute()
        }
    }

    private fun calculateEstimateForDistance(distanceKmRaw: Double) {
        val distanceKm = String.format(Locale.US, "%.1f", distanceKmRaw.coerceAtLeast(0.8)).toDouble()

        val isSurgeZone = binding.spTujuan.text.toString().contains("Banaran", true)

        val baseFare = if (selectedServiceType == "Motor") {
            H3GeoUtils.calculateFare(distanceKm)
        } else {
            H3GeoUtils.calculateFare(distanceKm) + 3000.0
        }

        val finalFare = if (isSurgeZone) baseFare * 1.3 else baseFare
        val minutes = Math.round(distanceKm * 3.5).coerceAtLeast(2)

        val surgeTag = if (isSurgeZone) " (⚡ Surge 1.3x)" else ""
        binding.tvJarak.text = "Estimasi Jarak: $distanceKm km (Real Map)"
        binding.tvWaktu.text = "Waktu tempuh sekitar $minutes menit"
        binding.tvHarga.text = "Rp ${String.format(Locale.US, "%,.0f", finalFare)}$surgeTag"
    }

    override fun onDestroyView() {
        resetTripState()
        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()
        binding.osmMapView.onPause()
    }
}
