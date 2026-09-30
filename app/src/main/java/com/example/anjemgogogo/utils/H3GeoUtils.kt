package com.example.anjemgogogo.utils

import kotlin.math.*

object H3GeoUtils {
    // Simulasi H3 Spatial Grid Indexing (Resolusi 9: ~100m precision)
    fun getH3Index(lat: Double, lng: Double): String {
        val latCell = (lat * 1000).toInt()
        val lngCell = (lng * 1000).toInt()
        return "89${Math.abs(latCell)}${Math.abs(lngCell)}"
    }

    // Menghitung estimasi jarak berbasis Haversine (menyimulasikan H3 Grid Distance)
    fun calculateH3DistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = r * c
        val estimatedKm = if (distance < 0.1) 0.8 else distance
        return String.format(java.util.Locale.US, "%.1f", estimatedKm).toDouble()
    }

    // Hitung jumlah langkah heksagonal (grid steps)
    fun getH3GridSteps(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Long {
        val km = calculateH3DistanceKm(lat1, lon1, lat2, lon2)
        return (km * 4.5).toLong().coerceAtLeast(1L)
    }

    // Hitung tarif: Minimal Rp 6.000 untuk 1 km pertama, Rp 4.000 / km berikutnya
    fun calculateFare(distanceKm: Double): Double {
        val minFare = 6000.0 // Minimal 6.000 untuk <= 1 km
        return if (distanceKm <= 1.0) {
            minFare
        } else {
            minFare + ((distanceKm - 1.0) * 4000.0) // Rp 4.000 per km berikutnya
        }
    }
}
