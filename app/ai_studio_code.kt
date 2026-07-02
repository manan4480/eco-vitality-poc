import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

class CarbonManager(val context: Context) {
    
    // Formula from website logic
    private val CAR_FACTOR = 0.171 // kg CO2 per km
    private val BUS_FACTOR = 0.103 // kg CO2 per km

    suspend fun getWalkingData(): Double {
        val healthConnectClient = HealthConnectClient.getOrCreate(context)
        val startTime = Instant.now().minusSeconds(86400) // Last 24 hours
        val endTime = Instant.now()
        
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = DistanceRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records.sumOf { it.distance.inKilometers }
        } catch (e: Exception) {
            0.0 // Return 0 if user hasn't set up Health Connect
        }
    }

    fun calculateCarCarbon(distanceKm: Double): Double = distanceKm * CAR_FACTOR
}