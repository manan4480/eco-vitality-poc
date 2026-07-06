package com.prithvi.ecovitality

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)
            result?.let {
                for (event in it.transitionEvents) {
                    val activityType = when (event.activityType) {
                        DetectedActivity.IN_VEHICLE -> "VEHICLE"
                        DetectedActivity.ON_BICYCLE -> "BICYCLE"
                        DetectedActivity.WALKING -> "WALKING"
                        DetectedActivity.RUNNING -> "RUNNING"
                        else -> "STILL"
                    }
                    val transitionType = if (event.transitionType == 0) "ENTER" else "EXIT"
                    
                    Log.d("ActivityReceiver", "Transition: $activityType $transitionType")
                    
                    // Store state in SharedPreferences
                    val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("last_activity", activityType).apply()
                    
                    if (transitionType == "ENTER") {
                        prefs.edit().putBoolean("is_new_trip", true).apply()
                        if (activityType == "VEHICLE" || activityType == "BICYCLE") {
                            val serviceIntent = Intent(context, TrackingService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }
                    }
                }
            }
        }
    }
}
