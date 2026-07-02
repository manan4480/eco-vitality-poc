package com.prithvi.ecovitality

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EcoVitalityTheme {
                val navController = rememberNavController()
                val manager = remember { CarbonManager(this) }
                
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Scaffold(
                        bottomBar = { BottomNavigationBar(navController) }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            NavHost(navController = navController, startDestination = "dashboard") {
                                composable("dashboard") { DashboardScreen(manager) }
                                composable("history") { HistoryScreen(manager) }
                                composable("profile") { ProfileScreen(manager) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Triple("dashboard", "Dashboard", Icons.Default.Dashboard),
        Triple("history", "History", Icons.Default.History),
        Triple("profile", "Profile", Icons.Default.Person)
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
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
fun DashboardScreen(manager: CarbonManager) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    var walkDist by remember { mutableDoubleStateOf(0.0) }
    var carDist by remember { mutableDoubleStateOf(manager.getManualDistance("Car")) }
    var busDist by remember { mutableDoubleStateOf(manager.getManualDistance("Bus")) }
    var trainDist by remember { mutableDoubleStateOf(manager.getManualDistance("Train")) }
    var bikeDist by remember { mutableDoubleStateOf(manager.getManualDistance("Bike")) }
    var autoCarDist by remember { mutableDoubleStateOf(0.0) }
    
    var totalXp by remember { mutableIntStateOf(0) }
    var overallEcoScore by remember { mutableIntStateOf(100) }
    var co2Generated by remember { mutableDoubleStateOf(0.0) }
    var co2Saved by remember { mutableDoubleStateOf(0.0) }
    var weeklyInsight by remember { mutableStateOf<CarbonInsight?>(null) }

    val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        // Initial Fetch
        manager.fetchFromCloud()
        
        while(true) {
            walkDist = manager.getLiveDistanceKm()
            autoCarDist = prefs.getFloat("auto_car_km", 0f).toDouble()
            carDist = manager.getManualDistance("Car")
            busDist = manager.getManualDistance("Bus")
            trainDist = manager.getManualDistance("Train")
            bikeDist = manager.getManualDistance("Bike")

            manager.updateTravelDistance()
            
            if (manager.hasAllPermissions()) {
                totalXp = manager.getTotalXP(walkDist, bikeDist, carDist + autoCarDist, busDist, trainDist)
                overallEcoScore = manager.getOverallEcoScore(walkDist, bikeDist, carDist + autoCarDist, busDist, trainDist)
                co2Generated = manager.getTotalGeneratedCO2(carDist + autoCarDist, busDist, trainDist)
                co2Saved = manager.getTotalSavedCO2(walkDist, bikeDist, busDist, trainDist)
                weeklyInsight = manager.getWeeklyData()
            }
            delay(5000)
        }
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions -> 
        if (permissions.entries.all { it.value }) {
            manager.startAutoTracking()
            scope.launch { if (!manager.hasAllPermissions()) healthPermissionLauncher.launch(manager.permissions) }
        }
    }

    LaunchedEffect(Unit) {
        val p = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (p.isNotEmpty()) locationPermissionLauncher.launch(p.toTypedArray()) else {
            manager.startAutoTracking()
            if (!manager.hasAllPermissions()) healthPermissionLauncher.launch(manager.permissions)
        }
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxSize().verticalScroll(scrollState)) {
        val user = prefs.getString("currentUsername", "Eco User") ?: "Eco User"
        val act = prefs.getString("last_activity", "STILL")
        Text("EcoVitality", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Welcome, $user", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
            if (act == "VEHICLE") {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Syncing Trip", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricStatusCard("Total XP", "$totalXp", Icons.Default.Star, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            MetricStatusCard("Eco Score", "$overallEcoScore/100", Icons.Default.Eco, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricStatusCard("CO2 Saved", "${co2Saved.format(2)} kg", Icons.Default.Nature, Color(0xFFE8F5E9), Color(0xFF2E7D32), Modifier.weight(1f))
            MetricStatusCard("CO2 Produced", "${co2Generated.format(2)} kg", Icons.Default.Co2, Color(0xFFFFF3E0), Color(0xFFE65100), Modifier.weight(1f))
        }

        LiveTripTracker(manager)
        Spacer(modifier = Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(220.dp)) {
            val total = manager.calculateCarCarbon(carDist + autoCarDist) + manager.calculateBusCarbon(busDist) + manager.calculateTrainCarbon(trainDist)
            CircularProgressIndicator(progress = { (total / 10.0).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.size(200.dp), color = if (total < 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, strokeWidth = 14.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Daily CO2", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Text("${total.format(2)} kg", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Manual Transport Log", fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportInput("Car", carDist, Modifier.weight(1f)) { carDist = it; manager.saveManualDistance("Car", it); manager.saveToHistory("Car", it) }
            TransportInput("Bus", busDist, Modifier.weight(1f)) { busDist = it; manager.saveManualDistance("Bus", it); manager.saveToHistory("Bus", it) }
            TransportInput("Train", trainDist, Modifier.weight(1f)) { trainDist = it; manager.saveManualDistance("Train", it); manager.saveToHistory("Train", it) }
            TransportInput("Bike", bikeDist, Modifier.weight(1f)) { bikeDist = it; manager.saveManualDistance("Bike", it); manager.saveToHistory("Bike", it) }
        }

        Spacer(modifier = Modifier.height(24.dp))
        MetricCard("Car Trip", "${(carDist + autoCarDist).format(1)} km", "${manager.calculateCarCarbon(carDist + autoCarDist).format(2)} kg CO2", MaterialTheme.colorScheme.error)
        MetricCard("Bus Trip", "${busDist.format(1)} km", "${manager.calculateBusCarbon(busDist).format(2)} kg CO2", MaterialTheme.colorScheme.secondary)
        MetricCard("Train Trip", "${trainDist.format(1)} km", "${manager.calculateTrainCarbon(trainDist).format(2)} kg CO2", MaterialTheme.colorScheme.primary)
        MetricCard("Bike Trip", "${bikeDist.format(1)} km", "Saved ${manager.calculateCarCarbon(bikeDist).format(2)} kg CO2", MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
fun LiveTripTracker(manager: CarbonManager) {
    val isActive by manager.isManualTripActive
    val type by manager.manualTripType
    val distance by manager.manualTripDistance
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Live Trip Tracker", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp)); Text("Tracking $type: ${distance.format(2)} km", fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { manager.stopManualTrip() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Stop") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Car", "Bus", "Train", "Bike").forEach { mode ->
                        Button(onClick = { manager.startManualTrip(mode) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text(mode, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricStatusCard(label: String, value: String, icon: ImageVector, bgColor: Color, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp)); Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)); Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TransportInput(label: String, value: Double, modifier: Modifier = Modifier, onValueChange: (Double) -> Unit) {
    OutlinedTextField(value = if(value == 0.0) "" else value.toString(), onValueChange = { onValueChange(it.toDoubleOrNull() ?: 0.0) }, label = { Text(label, fontSize = 10.sp) }, modifier = modifier, singleLine = true, shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline))
}

@Composable
fun MetricCard(mode: String, dist: String, co2: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(color, CircleShape)); Spacer(modifier = Modifier.width(16.dp))
            Column { Text(mode, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Dist: $dist | Carbon: $co2", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
fun HistoryScreen(manager: CarbonManager) {
    val history = manager.getHistory()
    var weeklyInsight by remember { mutableStateOf<CarbonInsight?>(null) }
    LaunchedEffect(Unit) { if (manager.hasAllPermissions()) weeklyInsight = manager.getWeeklyData() }
    Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
        Text("Activity History", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Your sustainable journeys", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            weeklyInsight?.let { if (it.weeklyDistance > 0) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) { Text("Health Sync", fontWeight = FontWeight.Bold); Text("Last 7 days walking", fontSize = 12.sp, color = Color.Gray) }
                        Column(horizontalAlignment = Alignment.End) { Text("${it.weeklyDistance.format(1)} km", fontWeight = FontWeight.Bold); Text("Saved ${it.totalCarbon.format(2)} kg", fontSize = 11.sp, color = Color.Gray) }
                    }
                }
            }}
            if (history.isEmpty() && (weeklyInsight == null || weeklyInsight?.weeklyDistance == 0.0)) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.outline); Text("No trips recorded.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) } }
            } else {
                history.forEach { log ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val icon = when(log.type) { "Car" -> Icons.Default.DirectionsCar; "Bus" -> Icons.Default.DirectionsBus; "Train" -> Icons.Default.Train; "Bike" -> Icons.Default.DirectionsBike; else -> Icons.Default.DirectionsRun }
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) { Text(log.type, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(log.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
                            Column(horizontalAlignment = Alignment.End) { val label = if(log.type == "Bike") "Saved" else "Produced"; Text("${log.distance.format(1)} km", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("$label ${log.co2.format(2)} kg", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun ProfileScreen(manager: CarbonManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
    var username by remember { mutableStateOf(prefs.getString("currentUsername", "") ?: "") }
    var email by remember { mutableStateOf(prefs.getString("currentUser", "") ?: "") }
    var profileImageUri by remember { mutableStateOf(prefs.getString("currentImage", null)?.let { Uri.parse(it) }) }
    var isEditing by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            profileImageUri = uri
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to take persistable URI permission", e)
            }
            prefs.edit().putString("currentImage", uri.toString()).apply()
            
            val currentUser = prefs.getString("currentUser", null)
            if (currentUser != null) {
                prefs.edit().putString("image_$currentUser", uri.toString()).apply()
            }
        }
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { }
    Column(modifier = Modifier.padding(20.dp).fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("My Profile", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { if (isEditing) { manager.updateProfile(username, email); Toast.makeText(context, "Profile Updated", Toast.LENGTH_SHORT).show() }; isEditing = !isEditing }) { Icon(if (isEditing) Icons.Default.Save else Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(modifier = Modifier.height(40.dp))
        
        Box(
            modifier = Modifier.size(120.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .clip(CircleShape)
                .clickable { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center
        ) {
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
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(70.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        if (isEditing) {
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        } else {
            Text(username, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(email, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(30.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sync & Data", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = { scope.launch { if (manager.hasAllPermissions()) Toast.makeText(context, "Data Synced!", Toast.LENGTH_SHORT).show() else healthPermissionLauncher.launch(manager.permissions) } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Default.Sync, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Sync Health Connect") }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileOption(Icons.Default.Settings, "App Settings"); HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                ProfileOption(Icons.Default.Help, "Help & Support"); HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                ProfileOption(Icons.Default.Info, "About EcoVitality")
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = { prefs.edit().putBoolean("isLoggedIn", false).apply(); context.startActivity(Intent(context, AuthActivity::class.java)); (context as ComponentActivity).finish() }, modifier = Modifier.fillMaxWidth().height(55.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Logout, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Logout", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun ProfileOption(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(16.dp)); Text(title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline) }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)
