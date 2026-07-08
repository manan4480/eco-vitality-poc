package com.prithvi.ecovitality

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class TransportLog(
    val date: String = "",
    val type: String = "",
    val distance: Double = 0.0,
    val co2: Double = 0.0,
    val id: String = ""
)

data class CarbonInsight(
    val dailyDistance: Double,
    val dailySteps: Int,
    val totalCarbon: Double,
    val xp: Int,
    val insightMessage: String = "",
    val needsPermissions: Boolean = false
)

class CarbonManager(val context: Context) : SensorEventListener {

    val CAR_FACTOR = 0.16691
    val BUS_FACTOR = 0.10846
    val TRAIN_FACTOR = 0.03549
    val MOTORBIKE_FACTOR = 0.11337
    val BIKE_FACTOR = 0.0 // Bike and Walk are zero emission
    val STEP_LENGTH_METERS = 0.75 

    private var sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    
    private val client = ActivityRecognition.getClient(context)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val pendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ActivityTransitionReceiver::class.java).apply {
            action = "com.prithvi.ecovitality.ACTION_PROCESS_ACTIVITY_TRANSITIONS"
        }, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun startAutoTracking() {
        val permissions = mutableListOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            permissions.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
        }
        
        val allGranted = permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) return

        val transitions = mutableListOf<ActivityTransition>()
        listOf(DetectedActivity.IN_VEHICLE, DetectedActivity.WALKING, DetectedActivity.ON_BICYCLE).forEach { activity ->
            transitions.add(ActivityTransition.Builder().setActivityType(activity).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build())
            transitions.add(ActivityTransition.Builder().setActivityType(activity).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build())
        }

        try {
            client.requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent)
        } catch (e: Exception) { Log.e("CarbonManager", "Failed to start auto tracking", e) }
    }

    var liveStepsSinceStart = 0.0
    private var initialStepCount = -1.0
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastKnownLocation: Location? = null
    
    var isManualTripActive = mutableStateOf(false)
    var manualTripType = mutableStateOf("Car")
    var manualTripDistance = mutableDoubleStateOf(0.0)

    init {
        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun syncToCloud() {
        val user = auth.currentUser ?: return
        val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
        val data = hashMapOf(
            "username" to prefs.getString("currentUsername", ""),
            "car_km" to prefs.getFloat("manual_car_km", 0f),
            "bus_km" to prefs.getFloat("manual_bus_km", 0f),
            "train_km" to prefs.getFloat("manual_train_km", 0f),
            "bike_km" to prefs.getFloat("manual_bike_km", 0f),
            "motorbike_km" to prefs.getFloat("manual_motorbike_km", 0f),
            "walk_km" to prefs.getFloat("manual_walk_km", 0f),
            "history" to getHistory().map { "${it.date}|${it.type}|${it.distance}|${it.co2}|${it.id}" }
        )
        db.collection("users").document(user.uid).set(data)
    }

    suspend fun fetchFromCloud() {
        val user = auth.currentUser ?: return
        try {
            val snapshot = db.collection("users").document(user.uid).get().await()
            if (snapshot.exists()) {
                val editor = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE).edit()
                snapshot.getString("username")?.let { editor.putString("currentUsername", it) }
                snapshot.getDouble("car_km")?.let { editor.putFloat("manual_car_km", it.toFloat()) }
                snapshot.getDouble("bus_km")?.let { editor.putFloat("manual_bus_km", it.toFloat()) }
                snapshot.getDouble("train_km")?.let { editor.putFloat("manual_train_km", it.toFloat()) }
                snapshot.getDouble("bike_km")?.let { editor.putFloat("manual_bike_km", it.toFloat()) }
                snapshot.getDouble("motorbike_km")?.let { editor.putFloat("manual_motorbike_km", it.toFloat()) }
                snapshot.getDouble("walk_km")?.let { editor.putFloat("manual_walk_km", it.toFloat()) }
                (snapshot.get("history") as? List<*>)?.let { list ->
                    editor.putStringSet("history_logs", list.filterIsInstance<String>().toSet())
                }
                editor.apply()
            }
        } catch (e: Exception) { Log.e("Cloud", "Fetch failed", e) }
    }

    suspend fun updateTravelDistance() {
        val currentActivity = if (isManualTripActive.value) "MANUAL" else context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE).getString("last_activity", "STILL")
        if (currentActivity == "VEHICLE" || currentActivity == "BICYCLE" || currentActivity == "MANUAL") {
            try {
                val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                if (location != null && lastKnownLocation != null) {
                    val distKm = lastKnownLocation!!.distanceTo(location) / 1000.0
                    if (distKm > 0.005) {
                        if (isManualTripActive.value) manualTripDistance.doubleValue += distKm
                        else {
                            val key = if (currentActivity == "VEHICLE") "auto_car_km" else "auto_bike_km"
                            val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
                            prefs.edit().putFloat(key, prefs.getFloat(key, 0f) + distKm.toFloat()).apply()
                            saveToHistory(if(currentActivity == "VEHICLE") "Car" else "Bike", distKm)
                        }
                    }
                }
                if (location != null) lastKnownLocation = location
            } catch (e: SecurityException) {}
        } else { lastKnownLocation = null }
    }

    fun startManualTrip(type: String) {
        isManualTripActive.value = true
        manualTripType.value = type
        manualTripDistance.doubleValue = 0.0
        lastKnownLocation = null
    }

    fun stopManualTrip() {
        if (isManualTripActive.value) {
            val type = manualTripType.value
            val dist = manualTripDistance.doubleValue
            val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
            val key = "manual_${type.lowercase()}_km"
            prefs.edit().putFloat(key, prefs.getFloat(key, 0f) + dist.toFloat()).apply()
            saveToHistory(type, dist)
            isManualTripActive.value = false
            syncToCloud()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val steps = event.values[0].toDouble()
            if (initialStepCount == -1.0) initialStepCount = steps
            liveStepsSinceStart = steps - initialStepCount
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun getLiveDistanceKm(): Double = (liveStepsSinceStart * STEP_LENGTH_METERS) / 1000.0

    val permissions = setOf(HealthPermission.getReadPermission(DistanceRecord::class), HealthPermission.getReadPermission(StepsRecord::class))

    suspend fun hasAllPermissions(): Boolean {
        return try {
            val availability = HealthConnectClient.getSdkStatus(context)
            if (availability != HealthConnectClient.SDK_AVAILABLE) return false
            val client = HealthConnectClient.getOrCreate(context)
            client.permissionController.getGrantedPermissions().containsAll(permissions)
        } catch (e: Exception) { false }
    }

    suspend fun getDailyHealthData(date: LocalDate): CarbonInsight {
        val availability = HealthConnectClient.getSdkStatus(context)
        if (availability != HealthConnectClient.SDK_AVAILABLE) {
            return CarbonInsight(0.0, 0, 0.0, 0, insightMessage = "Health Connect app required")
        }
        if (!hasAllPermissions()) return CarbonInsight(0.0, 0, 0.0, 0, needsPermissions = true)
        val client = HealthConnectClient.getOrCreate(context)
        val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endTime = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        
        return try {
            val dists = client.readRecords(ReadRecordsRequest(DistanceRecord::class, TimeRangeFilter.between(startTime, endTime)))
            val steps = client.readRecords(ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(startTime, endTime)))
            
            val totalDist = dists.records.sumOf { it.distance.inKilometers }
            val totalSteps = steps.records.sumOf { it.count }.toInt()
            
            // If distance is missing but steps are present, estimate distance
            val finalDist = if (totalDist == 0.0 && totalSteps > 0) (totalSteps * STEP_LENGTH_METERS) / 1000.0 else totalDist
            
            val co2Saved = finalDist * CAR_FACTOR // Assuming walking saves car emissions
            val xp = (finalDist * 15).toInt() + (totalSteps / 100) // 15 XP per km + 1 XP per 100 steps
            
            CarbonInsight(finalDist, totalSteps, co2Saved, xp)
        } catch (e: Exception) { 
            Log.e("HealthConnect", "Error reading data", e)
            CarbonInsight(0.0, 0, 0.0, 0) 
        }
    }

    fun calculateCarCarbon(km: Double) = km * CAR_FACTOR
    fun calculateBusCarbon(km: Double) = km * BUS_FACTOR
    fun calculateTrainCarbon(km: Double) = km * TRAIN_FACTOR
    fun calculateMotorBikeCarbon(km: Double) = km * MOTORBIKE_FACTOR

    fun saveToHistory(type: String, dist: Double) {
        val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
        val date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val history = prefs.getStringSet("history_logs", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val isNewTrip = prefs.getBoolean("is_new_trip", false)
        val existingEntry = if (isNewTrip) null else history.filter { it.startsWith("$date|$type|") }.lastOrNull()

        if (existingEntry != null) {
            val parts = existingEntry.split("|")
            val currentDist = parts[2].toDouble()
            history.remove(existingEntry)
            val newDist = currentDist + dist
            val co2 = when(type) { 
                "Car" -> calculateCarCarbon(newDist)
                "Bus" -> calculateBusCarbon(newDist)
                "Train" -> calculateTrainCarbon(newDist)
                "Motorbike" -> calculateMotorBikeCarbon(newDist)
                else -> 0.0 
            }
            val id = parts.getOrNull(4) ?: System.currentTimeMillis().toString()
            history.add("$date|$type|$newDist|$co2|$id")
        } else {
            val co2 = when(type) { 
                "Car" -> calculateCarCarbon(dist)
                "Bus" -> calculateBusCarbon(dist)
                "Train" -> calculateTrainCarbon(dist)
                "Motorbike" -> calculateMotorBikeCarbon(dist)
                else -> 0.0 
            }
            history.add("$date|$type|$dist|$co2|${System.currentTimeMillis()}")
            if (isNewTrip) prefs.edit().putBoolean("is_new_trip", false).apply()
        }
        
        prefs.edit().putStringSet("history_logs", history).apply()
        syncToCloud()
    }

    fun getHistory(): List<TransportLog> {
        return context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE).getStringSet("history_logs", emptySet())?.mapNotNull {
            try { 
                val p = it.split("|")
                TransportLog(p[0], p[1], p[2].toDouble(), p[3].toDouble(), p.getOrNull(4) ?: "") 
            } catch (e: Exception) { null }
        }?.sortedByDescending { it.id.ifEmpty { it.date } } ?: emptyList()
    }

    fun deleteHistoryItem(id: String) {
        val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
        val history = prefs.getStringSet("history_logs", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val toRemove = history.find { 
            val p = it.split("|")
            p.getOrNull(4) == id || (id.isEmpty() && p.size < 5) 
        }
        
        if (toRemove != null) {
            history.remove(toRemove)
            prefs.edit().putStringSet("history_logs", history).commit()
            syncToCloud()
        }
    }

    fun updateHistoryItem(id: String, newType: String) {
        val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
        val history = prefs.getStringSet("history_logs", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val entry = history.find { 
            val p = it.split("|")
            p.getOrNull(4) == id || (id.isEmpty() && p.size < 5)
        }
        
        if (entry != null) {
            val parts = entry.split("|")
            val dist = parts[2].toDouble()
            val co2 = when(newType) { 
                "Car" -> calculateCarCarbon(dist)
                "Bus" -> calculateBusCarbon(dist)
                "Train" -> calculateTrainCarbon(dist)
                "Motorbike" -> calculateMotorBikeCarbon(dist)
                "Bike", "Walk" -> 0.0
                else -> 0.0 
            }
            history.remove(entry)
            val finalId = id.ifEmpty { System.currentTimeMillis().toString() }
            history.add("${parts[0]}|$newType|$dist|$co2|$finalId")
            prefs.edit().putStringSet("history_logs", history).commit()
            syncToCloud()
        }
    }

    fun saveManualDistance(type: String, dist: Double) {
        val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("manual_${type.lowercase()}_km", dist.toFloat()).apply()
        syncToCloud()
    }

    fun getManualDistance(type: String) = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE).getFloat("manual_${type.lowercase()}_km", 0f).toDouble()

    fun updateProfile(name: String, email: String) {
        context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE).edit().putString("currentUsername", name).putString("currentUser", email).apply()
        syncToCloud()
    }

    fun correctTransportMode(from: String, to: String, dist: Double) {
        val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat(from, (prefs.getFloat(from, 0f) - dist.toFloat()).coerceAtLeast(0f)).putFloat(to, prefs.getFloat(to, 0f) + dist.toFloat()).apply()
        saveToHistory(if (to.contains("bus")) "Bus" else "Train", dist)
    }

    fun calculateEcoScore(walk: Double, bike: Double, motorbike: Double, car: Double, bus: Double, train: Double): Int {
        var s = 100.0
        s -= (calculateCarCarbon(car) * 5) + (calculateMotorBikeCarbon(motorbike) * 4) + (calculateBusCarbon(bus) * 2) + (calculateTrainCarbon(train) * 1)
        s += (walk * 2) + (bike * 4)
        return s.toInt().coerceIn(0, 100)
    }

    fun calculateXP(walk: Double, bike: Double, trans: Double, motorbike: Double, car: Double) = (walk * 15 + bike * 15 + trans * 5).toInt()

    suspend fun getTotalXP(sw: Double, sb: Double, smb: Double, sc: Double, sb2: Double, st: Double): Int {
        val historyXp = getHistory().sumOf { 
            when(it.type) {
                "Bike", "Walk" -> it.distance * 15
                "Bus", "Train" -> it.distance * 5
                else -> 0.0
            }
        }
        val currentDayHealth = getDailyHealthData(LocalDate.now())
        return (historyXp + currentDayHealth.xp + calculateXP(sw, sb, sb2 + st, smb, sc)).toInt()
    }

    suspend fun getOverallEcoScore(sw: Double, sb: Double, smb: Double, sc: Double, sb2: Double, st: Double): Int {
        val h = getHistory()
        val last7 = LocalDate.now().minusDays(7)
        val rh = h.filter { try { LocalDate.parse(it.date).isAfter(last7) } catch (e: Exception) { false } }
        val health7 = (0..6).sumOf { getDailyHealthData(LocalDate.now().minusDays(it.toLong())).dailyDistance }
        
        val tw = health7 + sw
        val tb = rh.filter { it.type == "Bike" }.sumOf { it.distance } + sb
        val tmb = rh.filter { it.type == "Motorbike" }.sumOf { it.distance } + smb
        val tc = rh.filter { it.type == "Car" }.sumOf { it.distance } + sc
        val tbus = rh.filter { it.type == "Bus" }.sumOf { it.distance } + sb2
        val ttrain = rh.filter { it.type == "Train" }.sumOf { it.distance } + st
        return calculateEcoScore(tw, tb, tmb, tc, tbus, ttrain)
    }

    suspend fun getTotalGeneratedCO2(smb: Double, sc: Double, sb: Double, st: Double) = 
        getHistory().sumOf { if (it.type !in listOf("Bike", "Walk")) it.co2 else 0.0 } + 
        calculateMotorBikeCarbon(smb) + calculateCarCarbon(sc) + calculateBusCarbon(sb) + calculateTrainCarbon(st)

    suspend fun getTotalSavedCO2(sw: Double, sb: Double, smb: Double, sc: Double, sb2: Double, st: Double): Double {
        val health7 = (0..6).sumOf { getDailyHealthData(LocalDate.now().minusDays(it.toLong())).dailyDistance } * CAR_FACTOR
        val h = getHistory().sumOf { 
            when(it.type) { 
                "Bike", "Walk" -> it.distance * CAR_FACTOR
                "Bus" -> it.distance * (CAR_FACTOR - BUS_FACTOR)
                "Train" -> it.distance * (CAR_FACTOR - TRAIN_FACTOR)
                "Motorbike" -> it.distance * (CAR_FACTOR - MOTORBIKE_FACTOR)
                else -> 0.0 
            } 
        }
        return health7 + h + (sw + sb) * CAR_FACTOR + sb2 * (CAR_FACTOR - BUS_FACTOR) + st * (CAR_FACTOR - TRAIN_FACTOR) + smb * (CAR_FACTOR - MOTORBIKE_FACTOR)
    }

    fun getInstallIntent() = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("market://details?id=com.google.android.apps.healthdata"); putExtra("overlay", true); putExtra("callerId", context.packageName) }
}
