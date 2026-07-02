package com.prithvi.ecovitality

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class TrackingService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLocation: Location? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(1, getNotification("EcoVitality is automatically tracking your trip..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTracking()
        return START_STICKY
    }

    private fun startTracking() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val prefs = getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
                    val currentActivity = prefs.getString("last_activity", "STILL")
                    
                    if (currentActivity == "VEHICLE" || currentActivity == "BICYCLE") {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { location ->
                                if (location != null && lastLocation != null) {
                                    val distance = lastLocation!!.distanceTo(location)
                                    if (distance > 10) {
                                        val distKm = distance / 1000.0
                                        val key = if (currentActivity == "VEHICLE") "auto_car_km" else "auto_bike_km"
                                        val current = prefs.getFloat(key, 0f)
                                        prefs.edit().putFloat(key, current + distKm.toFloat()).apply()
                                        
                                        // Update History
                                        val manager = CarbonManager(this@TrackingService)
                                        manager.saveToHistory(if(currentActivity == "VEHICLE") "Car" else "Bike", distKm)
                                    }
                                }
                                lastLocation = location
                            }
                    } else {
                        // Stop if we are still
                        stopSelf()
                        break
                    }
                } catch (e: SecurityException) {
                    Log.e("TrackingService", "No location permission")
                }
                delay(10000) // Track every 10 seconds
            }
        }
    }

    private fun getNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "tracking_channel")
            .setContentTitle("Auto-Sync Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("tracking_channel", "Trip Tracking", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
