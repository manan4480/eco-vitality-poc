package com.prithvi.ecovitality

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.prithvi.ecovitality.ui.theme.EcoVitalityTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(prefs.getString("app_theme", "System") ?: "System") }

            EcoVitalityTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val manager = remember { CarbonManager(this) }
                var profileImageUri by remember { 
                    mutableStateOf(
                        prefs.getString("currentImage", null)?.let { Uri.parse(it) } ?: 
                        prefs.getString("image_${prefs.getString("currentUser", "")}", null)?.let { Uri.parse(it) }
                    ) 
                }
                
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Scaffold(
                        bottomBar = { BottomNavigationBar(navController, profileImageUri) }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            NavHost(navController = navController, startDestination = "dashboard") {
                                composable("dashboard") { DashboardScreen(manager) }
                                composable("track") { TrackScreen(manager) }
                                composable("history") { HistoryScreen(manager) }
                                composable("rewards") { RewardsScreen(manager) }
                                composable("profile") { 
                                    ProfileScreen(manager, profileImageUri) { newUri -> 
                                        profileImageUri = newUri 
                                    } 
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController, profileImageUri: Uri?) {
    val context = LocalContext.current
    
    val items = listOf(
        Triple("dashboard", "Dashboard", Icons.Default.Dashboard),
        Triple("track", "Track", Icons.Default.AddLocationAlt),
        Triple("history", "History", Icons.Default.History),
        Triple("rewards", "Rewards", Icons.Default.CardGiftcard),
        Triple("profile", "Profile", Icons.Default.Person)
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                icon = { 
                    if (route == "profile" && profileImageUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(profileImageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = label,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = { Text(label, fontSize = 10.sp) },
                selected = currentRoute == route,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
fun VitalityRings(
    xp: Int,
    ecoScore: Int,
    saved: Double,
    produced: Double,
    size: Dp = 240.dp,
    strokeWidth: Dp = 14.dp,
    showCenterIcon: Boolean = true
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        // XP Ring (Outer)
        CircularProgressIndicator(
            progress = { (xp / 500f).coerceIn(0f, 1f) },
            modifier = Modifier.size(size),
            color = Color(0xFFFBC02D), // Gold
            strokeWidth = strokeWidth,
            trackColor = Color(0xFFFBC02D).copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round
        )
        // Eco Score Ring
        CircularProgressIndicator(
            progress = { (ecoScore / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.size(size * 0.83f),
            color = MaterialTheme.colorScheme.secondary,
            strokeWidth = strokeWidth,
            trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round
        )
        // CO2 Saved Ring
        CircularProgressIndicator(
            progress = { (saved / 10.0).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.size(size * 0.66f),
            color = Color(0xFF4CAF50), // Eco Green
            strokeWidth = strokeWidth,
            trackColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round
        )
        // CO2 Produced Ring (Inner)
        CircularProgressIndicator(
            progress = { (produced / 5.0).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.size(size * 0.5f),
            color = MaterialTheme.colorScheme.error,
            strokeWidth = strokeWidth,
            trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round
        )
        
        if (showCenterIcon) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Eco, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.13f))
            }
        }
    }
}

@Composable
fun DashboardScreen(manager: CarbonManager) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
    
    var stats by remember { mutableStateOf(mapOf<String, Double>()) }
    var totalXp by remember { mutableIntStateOf(0) }
    var overallEcoScore by remember { mutableIntStateOf(100) }
    var co2Saved by remember { mutableDoubleStateOf(0.0) }
    var co2Produced by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(Unit) {
        while(true) {
            val history = manager.getHistory()
            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            val todayTrips = history.filter { it.date == todayStr }
            
            val autoCar = prefs.getFloat("auto_car_km", 0f).toDouble()
            val autoBike = prefs.getFloat("auto_bike_km", 0f).toDouble()
            
            val newStats = mutableMapOf<String, Double>()
            listOf("Car", "Motorbike", "Bus", "Train", "Bike", "Walk").forEach { type ->
                newStats[type] = todayTrips.filter { it.type == type }.sumOf { it.distance }
            }
            
            newStats["Car"] = (newStats["Car"] ?: 0.0) + autoCar
            newStats["Bike"] = (newStats["Bike"] ?: 0.0) + autoBike

            if (manager.hasAllPermissions()) {
                val todayHealth = manager.getDailyHealthData(LocalDate.now())
                val liveWalk = manager.getLiveDistanceKm()
                val totalWalkValue = (newStats["Walk"] ?: 0.0) + todayHealth.dailyDistance + liveWalk
                
                // Map the internal "Walk" key to the UI "Walking" key
                newStats["Walking"] = totalWalkValue
                
                totalXp = manager.getTotalXP(totalWalkValue, newStats["Bike"] ?: 0.0, newStats["Motorbike"] ?: 0.0, newStats["Car"] ?: 0.0, newStats["Bus"] ?: 0.0, newStats["Train"] ?: 0.0)
                overallEcoScore = manager.getOverallEcoScore(totalWalkValue, newStats["Bike"] ?: 0.0, newStats["Motorbike"] ?: 0.0, newStats["Car"] ?: 0.0, newStats["Bus"] ?: 0.0, newStats["Train"] ?: 0.0)
                co2Produced = manager.getTotalGeneratedCO2(newStats["Motorbike"] ?: 0.0, newStats["Car"] ?: 0.0, newStats["Bus"] ?: 0.0, newStats["Train"] ?: 0.0)
                co2Saved = manager.getTotalSavedCO2(totalWalkValue, newStats["Bike"] ?: 0.0, newStats["Motorbike"] ?: 0.0, newStats["Car"] ?: 0.0, newStats["Bus"] ?: 0.0, newStats["Train"] ?: 0.0)
            } else {
                newStats["Walking"] = newStats["Walk"] ?: 0.0
            }
            
            stats = newStats
            delay(5000)
        }
    }

    Column(modifier = Modifier.padding(20.dp).fillMaxSize().verticalScroll(scrollState)) {
        Text("Overview", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Spacer(modifier = Modifier.height(30.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            VitalityRings(totalXp, overallEcoScore, co2Saved, co2Produced)
        }

        Spacer(modifier = Modifier.height(20.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(180.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false
        ) {
            item { LegendItem("Total XP", "$totalXp", Color(0xFFFBC02D), Icons.Default.Star) }
            item { LegendItem("Eco Score", "$overallEcoScore", MaterialTheme.colorScheme.secondary, Icons.Default.Eco) }
            item { LegendItem("CO2 Saved", "${co2Saved.format(2)}kg", Color(0xFF4CAF50), Icons.Default.Nature) }
            item { LegendItem("Produced", "${co2Produced.format(2)}kg", MaterialTheme.colorScheme.error, Icons.Default.Co2) }
        }

        Spacer(modifier = Modifier.height(30.dp))
        Text("Travel Breakdown (Today)", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(15.dp))
        
        val breakdownItems = listOf(
            Triple("Car", Icons.Default.DirectionsCar, MaterialTheme.colorScheme.error),
            Triple("Motorbike", Icons.Default.TwoWheeler, Color(0xFF9C27B0)),
            Triple("Bus", Icons.Default.DirectionsBus, MaterialTheme.colorScheme.secondary),
            Triple("Train", Icons.Default.Train, MaterialTheme.colorScheme.primary),
            Triple("Bike", Icons.Default.DirectionsBike, MaterialTheme.colorScheme.tertiary),
            Triple("Walking", Icons.Default.DirectionsWalk, Color(0xFF4CAF50))
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(320.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false
        ) {
            items(breakdownItems) { (mode, icon, color) ->
                val distance = stats[mode] ?: 0.0
                Card(modifier = Modifier.aspectRatio(0.85f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(8.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(icon, contentDescription = mode, tint = color, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(mode, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("${distance.format(1)} km", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun LegendItem(label: String, value: String, color: Color, icon: ImageVector) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
            }
        }
    }
}

@Composable
fun TrackScreen(manager: CarbonManager) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)

    var carDist by remember { mutableDoubleStateOf(manager.getManualDistance("Car")) }
    var busDist by remember { mutableDoubleStateOf(manager.getManualDistance("Bus")) }
    var trainDist by remember { mutableDoubleStateOf(manager.getManualDistance("Train")) }
    var bikeDist by remember { mutableDoubleStateOf(manager.getManualDistance("Bike")) }
    var walkDist by remember { mutableDoubleStateOf(manager.getManualDistance("Walk")) }
    var motorbikeDist by remember { mutableDoubleStateOf(manager.getManualDistance("Motorbike")) }

    Column(modifier = Modifier.padding(20.dp).fillMaxSize().verticalScroll(scrollState)) {
        Text("Track Activity", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Spacer(modifier = Modifier.height(30.dp))
        LiveTripTracker(manager)

        Spacer(modifier = Modifier.height(40.dp))
        Text("Manual Entry", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(15.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(380.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            item { TransportInput("Car", carDist) { carDist = it; manager.saveManualDistance("Car", it); manager.saveToHistory("Car", it) } }
            item { TransportInput("Motorbike", motorbikeDist) { motorbikeDist = it; manager.saveManualDistance("Motorbike", it); manager.saveToHistory("Motorbike", it) } }
            item { TransportInput("Bus", busDist) { busDist = it; manager.saveManualDistance("Bus", it); manager.saveToHistory("Bus", it) } }
            item { TransportInput("Train", trainDist) { trainDist = it; manager.saveManualDistance("Train", it); manager.saveToHistory("Train", it) } }
            item { TransportInput("Bike", bikeDist) { bikeDist = it; manager.saveManualDistance("Bike", it); manager.saveToHistory("Bike", it) } }
            item { TransportInput("Walk", walkDist) { walkDist = it; manager.saveManualDistance("Walk", it); manager.saveToHistory("Walk", it) } }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun LiveTripTracker(manager: CarbonManager) {
    val isActive by manager.isManualTripActive
    val type by manager.manualTripType
    val distance by manager.manualTripDistance
    
    val modes = listOf(
        Triple("Car", Icons.Default.DirectionsCar, MaterialTheme.colorScheme.error),
        Triple("Motorbike", Icons.Default.TwoWheeler, Color(0xFF9C27B0)),
        Triple("Bus", Icons.Default.DirectionsBus, MaterialTheme.colorScheme.secondary),
        Triple("Train", Icons.Default.Train, MaterialTheme.colorScheme.primary),
        Triple("Bike", Icons.Default.DirectionsBike, MaterialTheme.colorScheme.tertiary),
        Triple("Walk", Icons.Default.DirectionsWalk, Color(0xFF4CAF50))
    )

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Live Tracking", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(15.dp))
            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Tracking $type", fontWeight = FontWeight.Bold)
                        Text("${distance.format(2)} km covered", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { manager.stopManualTrip() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(12.dp)) { Text("Stop") }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false
                ) {
                    items(modes) { (mode, icon, color) ->
                        ModeButton(mode, icon, color) { manager.startManualTrip(mode) }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeButton(mode: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.aspectRatio(0.9f)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = mode, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(mode, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun MetricStatusCard(label: String, value: String, icon: ImageVector, bgColor: Color, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = tint.copy(alpha = 0.8f))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tint, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TransportInput(label: String, value: Double, modifier: Modifier = Modifier, onValueChange: (Double) -> Unit) {
    OutlinedTextField(value = if(value == 0.0) "" else value.toString(), onValueChange = { onValueChange(it.toDoubleOrNull() ?: 0.0) }, label = { Text(label, fontSize = 12.sp) }, modifier = modifier, singleLine = true, shape = RoundedCornerShape(12.dp))
}

@Composable
fun HistoryScreen(manager: CarbonManager) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var dailyInsight by remember { mutableStateOf<CarbonInsight?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var history by remember { mutableStateOf(manager.getHistory()) }
    var selectedLog by remember { mutableStateOf<TransportLog?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(true) }

    val days = remember { (0..13).map { LocalDate.now().minusDays(it.toLong()) }.reversed() }

    LaunchedEffect(selectedDate, refreshTrigger) {
        hasPermissions = manager.hasAllPermissions()
        if (hasPermissions) {
            dailyInsight = manager.getDailyHealthData(selectedDate)
        }
        history = manager.getHistory().filter { it.date == selectedDate.format(DateTimeFormatter.ISO_DATE) }
    }

    if (showEditDialog && selectedLog != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Trip") },
            text = {
                Column {
                    Text("Change transport type for ${selectedLog!!.distance.format(1)} km trip on ${selectedLog!!.date}")
                    Spacer(modifier = Modifier.height(16.dp))
                    listOf("Car", "Motorbike", "Bus", "Train", "Bike", "Walk").forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            manager.updateHistoryItem(selectedLog!!.id, type)
                            refreshTrigger++
                            showEditDialog = false
                            Toast.makeText(context, "Updated to $type", Toast.LENGTH_SHORT).show()
                        }.padding(vertical = 12.dp)) {
                            RadioButton(selected = selectedLog!!.type == type, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(type)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    manager.deleteHistoryItem(selectedLog!!.id)
                    refreshTrigger++
                    showEditDialog = false
                    Toast.makeText(context, "Trip Deleted", Toast.LENGTH_SHORT).show()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.padding(20.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Activity History", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(20.dp))

        // XP Day Picker
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(initial = 1000)), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            days.forEach { date ->
                var dayStats by remember(date, refreshTrigger) { 
                    mutableStateOf(Triple(0, 0, Triple(0.0, 0.0, 100))) 
                }
                
                LaunchedEffect(date, refreshTrigger) {
                    if (manager.hasAllPermissions()) {
                        val insight = manager.getDailyHealthData(date)
                        val dateStr = date.format(DateTimeFormatter.ISO_DATE)
                        val todayHistory = manager.getHistory().filter { it.date == dateStr }
                        
                        val tripXp = todayHistory.sumOf { 
                            if(it.type in listOf("Bike", "Walk")) it.distance * 15 else if(it.type in listOf("Bus", "Train")) it.distance * 5 else 0.0
                        }.toInt()
                        
                        val totalXp = insight.xp + tripXp
                        val totalSaved = insight.totalCarbon + todayHistory.filter { it.type in listOf("Bike", "Walk", "Bus", "Train") }.sumOf { 
                            when(it.type) {
                                "Bus" -> it.distance * (manager.CAR_FACTOR - manager.BUS_FACTOR)
                                "Train" -> it.distance * (manager.CAR_FACTOR - manager.TRAIN_FACTOR)
                                "Bike", "Walk" -> it.distance * manager.CAR_FACTOR
                                else -> 0.0
                            }
                        }
                        val totalProduced = todayHistory.filter { it.type in listOf("Car", "Motorbike", "Bus", "Train") }.sumOf { it.co2 }
                        
                        dayStats = Triple(totalXp, 100 /* Simplified score */, Triple(totalSaved, totalProduced, 100))
                    }
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selectedDate = date }) {
                    Text(date.dayOfWeek.name.take(1), fontSize = 12.sp, color = if(date == selectedDate) MaterialTheme.colorScheme.primary else Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.size(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date == selectedDate) {
                            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer, CircleShape).clip(CircleShape))
                        }
                        VitalityRings(
                            xp = dayStats.first,
                            ecoScore = dayStats.third.third,
                            saved = dayStats.third.first,
                            produced = dayStats.third.second,
                            size = 40.dp,
                            strokeWidth = 3.dp,
                            showCenterIcon = false
                        )
                        Text("${dayStats.first}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if(date == selectedDate) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        if (dailyInsight != null) {
            val dateStr = selectedDate.format(DateTimeFormatter.ISO_DATE)
            val todayHistory = manager.getHistory().filter { it.date == dateStr }
            val tripXp = todayHistory.sumOf { 
                if(it.type in listOf("Bike", "Walk")) it.distance * 15 else if(it.type in listOf("Bus", "Train")) it.distance * 5 else 0.0
            }.toInt()
            
            val totalXpForDay = dailyInsight!!.xp + tripXp
            val totalSavedForDay = dailyInsight!!.totalCarbon + todayHistory.filter { it.type in listOf("Bike", "Walk", "Bus", "Train") }.sumOf { 
                when(it.type) {
                    "Bus" -> it.distance * (manager.CAR_FACTOR - manager.BUS_FACTOR)
                    "Train" -> it.distance * (manager.CAR_FACTOR - manager.TRAIN_FACTOR)
                    "Bike", "Walk" -> it.distance * manager.CAR_FACTOR
                    else -> 0.0
                }
            }
            val totalProducedForDay = todayHistory.filter { it.type in listOf("Car", "Motorbike", "Bus", "Train") }.sumOf { it.co2 }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    VitalityRings(
                        xp = totalXpForDay,
                        ecoScore = 100, // Placeholder
                        saved = totalSavedForDay,
                        produced = totalProducedForDay,
                        size = 180.dp,
                        strokeWidth = 12.dp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActivityMetric("Steps", "${dailyInsight!!.dailySteps}", Icons.Default.DirectionsWalk)
                        ActivityMetric("Total XP", "${totalXpForDay}", Icons.Default.Bolt)
                        ActivityMetric("CO2 Saved", "${totalSavedForDay.format(2)} kg", Icons.Default.Nature)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
        Text("Logged Trips", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(10.dp))

        if (history.isEmpty()) {
            Text("No logged trips for this day.", color = Color.Gray, modifier = Modifier.padding(vertical = 20.dp), textAlign = TextAlign.Center)
        } else {
            history.forEach { log ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { selectedLog = log; showEditDialog = true }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon = when(log.type) { "Car" -> Icons.Default.DirectionsCar; "Motorbike" -> Icons.Default.TwoWheeler; "Bus" -> Icons.Default.DirectionsBus; "Train" -> Icons.Default.Train; "Bike" -> Icons.Default.DirectionsBike; "Walk" -> Icons.Default.DirectionsWalk; else -> Icons.Default.DirectionsRun }
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) { 
                            Text(log.type, fontWeight = FontWeight.Bold) 
                            Text("${log.distance.format(1)} km", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Column(horizontalAlignment = Alignment.End) { 
                            val label = if(log.type in listOf("Bike", "Walk")) "Saved" else "Produced"
                            Text("${log.co2.format(2)} kg", fontWeight = FontWeight.Bold)
                            Text(label, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

data class Reward(val id: String, val title: String, val description: String, val requiredXp: Int, val icon: ImageVector, val color: Color)

@Composable
fun RewardsScreen(manager: CarbonManager) {
    var cumulativeXp by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    val rewards = listOf(
        Reward("1", "Amazon Voucher", "$5 Shopping Credit", 1000, Icons.Default.ShoppingBag, Color(0xFFFF9900)),
        Reward("2", "Steam Voucher", "$10 Wallet Code", 2500, Icons.Default.Games, Color(0xFF171A21)),
        Reward("3", "Fitbit Air", "Premium Fitness Tracker", 10000, Icons.Default.Watch, Color(0xFF00B0B9)),
        Reward("4", "Eco Tablet", "10-inch Sustainable Tablet", 25000, Icons.Default.TabletAndroid, Color(0xFF4CAF50))
    )

    LaunchedEffect(Unit) {
        if (manager.hasAllPermissions()) {
            val days = (0..13).map { LocalDate.now().minusDays(it.toLong()) }
            var total = 0
            days.forEach { date ->
                val insight = manager.getDailyHealthData(date)
                val tripXp = manager.getHistory().filter { it.date == date.format(DateTimeFormatter.ISO_DATE) }.sumOf { 
                    if(it.type in listOf("Bike", "Walk")) it.distance * 15 else if(it.type in listOf("Bus", "Train")) it.distance * 5 else 0.0
                }.toInt()
                total += (insight.xp + tripXp)
            }
            cumulativeXp = total
        }
        isLoading = false
    }

    Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
        Text("Rewards", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Spacer(modifier = Modifier.height(10.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total Vitality XP", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
                    Text("$cumulativeXp XP", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFFBC02D), modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Available Rewards", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(15.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rewards) { reward ->
                    RewardCard(reward, cumulativeXp)
                }
            }
        }
    }
}

@Composable
fun RewardCard(reward: Reward, currentXp: Int) {
    val progress = (currentXp.toFloat() / reward.requiredXp).coerceIn(0f, 1f)
    val isUnlocked = currentXp >= reward.requiredXp
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if(isUnlocked) androidx.compose.foundation.BorderStroke(2.dp, reward.color) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(50.dp).background(reward.color.copy(alpha = 0.1f), CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(reward.icon, contentDescription = null, tint = reward.color, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(reward.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(reward.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                if (isUnlocked) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Unlocked", tint = Color(0xFF4CAF50))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if(isUnlocked) "Ready to Redeem!" else "${reward.requiredXp - currentXp} XP to go", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("${(progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (isUnlocked) reward.color else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
fun ActivityMetric(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun ProfileScreen(manager: CarbonManager, profileImageUri: Uri?, onProfileImageChange: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
    var username by remember { mutableStateOf(prefs.getString("currentUsername", "") ?: "") }
    var email by remember { mutableStateOf(prefs.getString("currentUser", "") ?: "") }
    var isEditing by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            onProfileImageChange(uri)
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { Log.e("MainActivity", "Failed to take permission", e) }
            prefs.edit().putString("currentImage", uri.toString()).apply()
            prefs.getString("currentUser", null)?.let { prefs.edit().putString("image_$it", uri.toString()).apply() }
        }
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { }
    Column(modifier = Modifier.padding(20.dp).fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("My Profile", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { if (isEditing) { manager.updateProfile(username, email); Toast.makeText(context, "Profile Updated", Toast.LENGTH_SHORT).show() }; isEditing = !isEditing }) { Icon(if (isEditing) Icons.Default.Save else Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Box(modifier = Modifier.size(140.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape).clip(CircleShape).clickable { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
            if (profileImageUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(profileImageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        if (isEditing) {
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        } else {
            Text(username, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(email, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(30.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("App Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(15.dp))
                Text("Theme", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    listOf("Light", "Dark", "AMOLED", "System").forEach { theme ->
                        FilterChip(
                            selected = (prefs.getString("app_theme", "System") == theme),
                            onClick = { 
                                prefs.edit().putString("app_theme", theme).apply()
                                (context as? Activity)?.recreate() 
                            },
                            label = { Text(theme, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Button(onClick = { scope.launch { if (manager.hasAllPermissions()) Toast.makeText(context, "Data Synced!", Toast.LENGTH_SHORT).show() else healthPermissionLauncher.launch(manager.permissions) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Sync, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Sync Health Connect") }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileOption(Icons.Default.Help, "Help & Support")
                ProfileOption(Icons.Default.Info, "About EcoVitality")
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = { 
                prefs.edit().putBoolean("isLoggedIn", false).apply()
                val intent = Intent(context, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(intent)
                (context as Activity).finish() 
            }, 
            modifier = Modifier.fillMaxWidth().height(55.dp), 
            shape = RoundedCornerShape(16.dp), 
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) { 
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout", fontSize = 16.sp, fontWeight = FontWeight.Bold) 
        }
    }
}

@Composable
fun ProfileOption(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(16.dp)); Text(title, fontWeight = FontWeight.Medium); Spacer(modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline) }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)
