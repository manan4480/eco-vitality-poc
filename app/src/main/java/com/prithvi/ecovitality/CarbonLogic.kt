package com.prithvi.ecovitality

import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTimeMillis: Long,
    val estimatedEnergyMah: Double,
    val icon: android.graphics.drawable.Drawable? = null
)

data class DigitalWellbeingSummary(
    val totalScreenTimeMillis: Long,
    val totalEnergyMah: Double,
    val appUsages: List<AppUsageInfo>,
    val digitalXpPenalty: Int
)

data class TransportLog(
    val date: String = "",
    val type: String = "",
    val distance: Double = 0.0,
    val co2: Double = 0.0,
    val id: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val durationMinutes: Long = 0,
    val xpEarned: Int = 0,
    val ecoScoreImpact: Int = 0
)

data class StatsSummary(
    val co2Saved: Double = 0.0,
    val distance: Double = 0.0,
    val trips: Int = 0,
    val xp: Int = 0,
    val steps: Int = 0
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

    private val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)

    fun getCarFactor(): Double {
        return when(prefs.getString("car_type", "Petrol")) {
            "Petrol" -> 0.170
            "Diesel" -> 0.171
            "Hybrid" -> 0.110
            "Electric" -> 0.045
            else -> 0.170
        }
    }

    val BUS_FACTOR = 0.108
    val TRAIN_FACTOR = 0.035
    val MOTORBIKE_FACTOR = 0.113
    val BIKE_FACTOR = 0.0
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
    private var manualTripStartTimeMillis = 0L

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
        manualTripStartTimeMillis = System.currentTimeMillis()
    }

    fun stopManualTrip() {
        if (isManualTripActive.value) {
            val type = manualTripType.value
            val dist = manualTripDistance.doubleValue
            val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
            val key = "manual_${type.lowercase()}_km"
            prefs.edit().putFloat(key, prefs.getFloat(key, 0f) + dist.toFloat()).apply()
            
            val durationMins = (System.currentTimeMillis() - manualTripStartTimeMillis) / 60000
            val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            val endTime = java.time.LocalTime.now()
            val startTime = endTime.minusMinutes(durationMins)
            
            saveToHistory(type, dist, startTime.format(timeFormatter), endTime.format(timeFormatter), durationMins)
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
            
            val co2Saved = finalDist * getCarFactor() // Assuming walking saves car emissions
            val xp = (finalDist * 25).toInt() + (totalSteps / 100) // 25 XP per km + 1 XP per 100 steps
            
            CarbonInsight(finalDist, totalSteps, co2Saved, xp)
        } catch (e: Exception) { 
            Log.e("HealthConnect", "Error reading data", e)
            CarbonInsight(0.0, 0, 0.0, 0) 
        }
    }

    fun calculateCarCarbon(km: Double) = km * getCarFactor()
    fun calculateBusCarbon(km: Double) = km * BUS_FACTOR
    fun calculateTrainCarbon(km: Double) = km * TRAIN_FACTOR
    fun calculateMotorBikeCarbon(km: Double) = km * MOTORBIKE_FACTOR

    fun calculateTripXP(type: String, dist: Double): Int {
        val baseXP = when(type) {
            "Walk", "Bike" -> dist * 20
            "Bus", "Train" -> dist * 10
            "Motorbike" -> dist * 2
            else -> dist * 1
        }
        val co2Saved = if (type in listOf("Walk", "Bike", "Bus", "Train")) {
            val em = when(type) {
                "Bus" -> (CAR_FACTOR - BUS_FACTOR) * dist
                "Train" -> (CAR_FACTOR - TRAIN_FACTOR) * dist
                else -> CAR_FACTOR * dist
            }
            em.coerceAtLeast(0.0)
        } else 0.0
        
        val co2Bonus = (co2Saved * 5).toInt()
        return (baseXP + co2Bonus).toInt()
    }

    fun calculateTripEcoScoreImpact(type: String, dist: Double): Int {
        return when(type) {
            "Walk", "Bike" -> (dist * 5).toInt().coerceIn(1, 10)
            "Bus", "Train" -> (dist * 2).toInt().coerceIn(1, 5)
            "Motorbike" -> -((dist * 2).toInt().coerceIn(1, 5))
            "Car" -> -((dist * 5).toInt().coerceIn(1, 10))
            else -> 0
        }
    }

    fun saveToHistory(
        type: String, 
        dist: Double, 
        startTime: String = "", 
        endTime: String = "", 
        durationMinutes: Long = 0
    ) {
        val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
        val date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val history = prefs.getStringSet("history_logs", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val co2 = when(type) { 
            "Car" -> calculateCarCarbon(dist)
            "Bus" -> calculateBusCarbon(dist)
            "Train" -> calculateTrainCarbon(dist)
            "Motorbike" -> calculateMotorBikeCarbon(dist)
            else -> 0.0 
        }
        
        val xp = calculateTripXP(type, dist)
        val ecoImpact = calculateTripEcoScoreImpact(type, dist)
        val id = System.currentTimeMillis().toString()
        
        history.add("$date|$type|$dist|$co2|$id|$startTime|$endTime|$durationMinutes|$xp|$ecoImpact")
        
        prefs.edit().putStringSet("history_logs", history).apply()
        syncToCloud()
    }

    fun getHistory(): List<TransportLog> {
        return context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE).getStringSet("history_logs", emptySet())?.mapNotNull {
            try { 
                val p = it.split("|")
                TransportLog(
                    date = p[0], 
                    type = p[1], 
                    distance = p[2].toDouble(), 
                    co2 = p[3].toDouble(), 
                    id = p[4],
                    startTime = p.getOrNull(5) ?: "",
                    endTime = p.getOrNull(6) ?: "",
                    durationMinutes = p.getOrNull(7)?.toLong() ?: 0L,
                    xpEarned = p.getOrNull(8)?.toInt() ?: 0,
                    ecoScoreImpact = p.getOrNull(9)?.toInt() ?: 0
                )
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
            
            val startTime = parts.getOrNull(5) ?: ""
            val endTime = parts.getOrNull(6) ?: ""
            val duration = parts.getOrNull(7) ?: "0"
            val xp = calculateTripXP(newType, dist)
            val ecoImpact = calculateTripEcoScoreImpact(newType, dist)

            history.add("${parts[0]}|$newType|$dist|$co2|$finalId|$startTime|$endTime|$duration|$xp|$ecoImpact")
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

    fun calculateEcoScore(walk: Double, bike: Double, motorbike: Double, car: Double, bus: Double, train: Double, digitalPenalty: Int = 0): Int {
        if (walk + bike + motorbike + car + bus + train == 0.0) return (100 - digitalPenalty).coerceAtLeast(0)
        var s = 100.0
        s -= (calculateCarCarbon(car) * 5) + (calculateMotorBikeCarbon(motorbike) * 4) + (calculateBusCarbon(bus) * 2) + (calculateTrainCarbon(train) * 1)
        s += (walk * 2) + (bike * 4)
        s -= digitalPenalty
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

    suspend fun getTodaySummary(): StatsSummary {
        val today = LocalDate.now()
        val history = getHistory().filter { it.date == today.format(DateTimeFormatter.ISO_DATE) }
        val health = getDailyHealthData(today)
        val digital = getDigitalWellbeingSummary()
        
        val totalDist = history.sumOf { it.distance } + health.dailyDistance
        val totalSaved = history.sumOf { 
            if (it.type in listOf("Bike", "Walk", "Bus", "Train")) {
                val factor = when(it.type) {
                    "Bus" -> getCarFactor() - BUS_FACTOR
                    "Train" -> getCarFactor() - TRAIN_FACTOR
                    else -> getCarFactor()
                }
                it.distance * factor
            } else 0.0
        } + health.totalCarbon
        
        val totalXP = (history.sumOf { it.xpEarned } + health.xp - digital.digitalXpPenalty).coerceAtLeast(0)
        
        return StatsSummary(
            co2Saved = totalSaved,
            distance = totalDist,
            trips = history.size,
            xp = totalXP,
            steps = health.dailySteps
        )
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun getDigitalWellbeingSummary(): DigitalWellbeingSummary {
        if (!hasUsageStatsPermission()) {
            return DigitalWellbeingSummary(0, 0.0, emptyList(), 0)
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Use queryAndAggregateUsageStats to get a clean map of package to usage
        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        
        if (statsMap.isEmpty()) {
            return DigitalWellbeingSummary(0, 0.0, emptyList(), 0)
        }

        val appUsages = mutableListOf<AppUsageInfo>()
        var totalTime: Long = 0
        val pm = context.packageManager

        statsMap.forEach { (pkgName, usage) ->
            if (usage.totalTimeInForeground > 60000) { // Only apps used for more than 1 minute
                val appLabel = try {
                    val ai = pm.getApplicationInfo(pkgName, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (e: Exception) { pkgName }
                
                val icon = try {
                    pm.getApplicationIcon(pkgName)
                } catch (e: Exception) { null }

                totalTime += usage.totalTimeInForeground
                val energy = (usage.totalTimeInForeground / 60000.0) * 3.0
                
                // Filter for apps that have a launcher (actual user-facing apps)
                val isUserApp = pm.getLaunchIntentForPackage(pkgName) != null || pkgName == context.packageName
                if (isUserApp) {
                    appUsages.add(AppUsageInfo(pkgName, appLabel, usage.totalTimeInForeground, energy, icon))
                }
            }
        }

        val sortedUsages = appUsages.sortedByDescending { it.usageTimeMillis }.take(15)
        val totalEnergy = sortedUsages.sumOf { it.estimatedEnergyMah }
        val hours = totalTime / 3600000.0
        val penalty = (hours * 10).toInt()

        return DigitalWellbeingSummary(totalTime, totalEnergy, sortedUsages, penalty)
    }

    suspend fun getLifetimeSummary(): StatsSummary {
        val history = getHistory()
        val today = LocalDate.now()
        
        var healthDist = 0.0
        var healthSaved = 0.0
        var healthXP = 0
        
        try {
            val availability = HealthConnectClient.getSdkStatus(context)
            if (availability == HealthConnectClient.SDK_AVAILABLE) {
                // Aggregate last 30 days for lifetime summary
                for (i in 0..30) {
                    val date = today.minusDays(i.toLong())
                    val data = getDailyHealthData(date)
                    healthDist += data.dailyDistance
                    healthSaved += data.totalCarbon
                    healthXP += data.xp
                }
            }
        } catch (e: Exception) {}

        val totalDist = history.sumOf { it.distance } + healthDist
        val totalSaved = history.sumOf { 
            if (it.type in listOf("Bike", "Walk", "Bus", "Train")) {
                val factor = when(it.type) {
                    "Bus" -> getCarFactor() - BUS_FACTOR
                    "Train" -> getCarFactor() - TRAIN_FACTOR
                    else -> getCarFactor()
                }
                it.distance * factor
            } else 0.0
        } + healthSaved
        
        val totalXP = history.sumOf { it.xpEarned } + healthXP
        
        return StatsSummary(
            co2Saved = totalSaved,
            distance = totalDist,
            trips = history.size,
            xp = totalXP
        )
    }
}
