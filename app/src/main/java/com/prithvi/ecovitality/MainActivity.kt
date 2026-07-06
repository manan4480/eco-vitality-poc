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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
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
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(prefs.getString("app_theme", "System") ?: "System") }

            EcoVitalityTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val manager = remember { CarbonManager(this) }
                
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Scaffold(
                        bottomBar = { BottomNavigationBar(navController) }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            NavHost(navController = navController, startDestination = "dashboard") {
                                composable("dashboard") { DashboardScreen(manager) }
                                composable("track") { TrackScreen(manager) }
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
        Triple("dashboard", "Home", Icons.Default.Dashboard),
        Triple("track", "Track", Icons.Default.AddLocationAlt),
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
    val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
    
    var walkDist by remember { mutableDoubleStateOf(manager.getManualDistance("Walk")) }
    var carDist by remember { mutableDoubleStateOf(manager.getManualDistance("Car")) }
    var busDist by remember { mutableDoubleStateOf(manager.getManualDistance("Bus")) }
    var trainDist by remember { mutableDoubleStateOf(manager.getManualDistance("Train")) }
    var bikeDist by remember { mutableDoubleStateOf(manager.getManualDistance("Bike")) }
    var motorbikeDist by remember { mutableDoubleStateOf(manager.getManualDistance("Motorbike")) }
    var autoCarDist by remember { mutableDoubleStateOf(0.0) }
    
    var totalXp by remember { mutableIntStateOf(0) }
    var overallEcoScore by remember { mutableIntStateOf(100) }
    var co2Generated by remember { mutableDoubleStateOf(0.0) }
    var co2Saved by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(Unit) {
        while(true) {
            autoCarDist = prefs.getFloat("auto_car_km", 0f).toDouble()
            carDist = manager.getManualDistance("Car")
            busDist = manager.getManualDistance("Bus")
            trainDist = manager.getManualDistance("Train")
            bikeDist = manager.getManualDistance("Bike")
            walkDist = manager.getManualDistance("Walk")
            motorbikeDist = manager.getManualDistance("Motorbike")

            if (manager.hasAllPermissions()) {
                val liveWalk = manager.getLiveDistanceKm()
                totalXp = manager.getTotalXP(liveWalk + walkDist, bikeDist, motorbikeDist, carDist + autoCarDist, busDist, trainDist)
                overallEcoScore = manager.getOverallEcoScore(liveWalk + walkDist, bikeDist, motorbikeDist, carDist + autoCarDist, busDist, trainDist)
                co2Generated = manager.getTotalGeneratedCO2(motorbikeDist, carDist + autoCarDist, busDist, trainDist)
                co2Saved = manager.getTotalSavedCO2(liveWalk + walkDist, bikeDist, motorbikeDist, carDist + autoCarDist, busDist, trainDist)
            }
            delay(5000)
        }
    }

    Column(modifier = Modifier.padding(20.dp).fillMaxSize().verticalScroll(scrollState)) {
        Text("Overview", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Spacer(modifier = Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(300.dp),
            contentPadding = PaddingValues(bottom = 15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { MetricStatusCard("Total XP", "$totalXp", Icons.Default.Star, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
            item { MetricStatusCard("Eco Score", "$overallEcoScore/100", Icons.Default.Eco, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
            item { MetricStatusCard("CO2 Saved", "${co2Saved.format(2)} kg", Icons.Default.Nature, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
            item { MetricStatusCard("Produced", "${co2Generated.format(2)} kg", Icons.Default.Co2, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(220.dp)) {
            val total = manager.calculateCarCarbon(carDist + autoCarDist) + manager.calculateBusCarbon(busDist) + manager.calculateTrainCarbon(trainDist) + manager.calculateMotorBikeCarbon(motorbikeDist)
            CircularProgressIndicator(progress = { (total / 10.0).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.size(200.dp), color = if (total < 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, strokeWidth = 14.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Daily CO2", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Text("${total.format(2)} kg", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
        Text("Travel Breakdown", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(10.dp))
        
        MetricCard("Car", "${(carDist + autoCarDist).format(1)} km", "${manager.calculateCarCarbon(carDist + autoCarDist).format(2)} kg", MaterialTheme.colorScheme.error)
        MetricCard("Motorbike", "${motorbikeDist.format(1)} km", "${manager.calculateMotorBikeCarbon(motorbikeDist).format(2)} kg", Color(0xFF9C27B0))
        MetricCard("Bus", "${busDist.format(1)} km", "${manager.calculateBusCarbon(busDist).format(2)} kg", MaterialTheme.colorScheme.secondary)
        MetricCard("Train", "${trainDist.format(1)} km", "${manager.calculateTrainCarbon(trainDist).format(2)} kg", MaterialTheme.colorScheme.primary)
        MetricCard("Bike", "${bikeDist.format(1)} km", "Saved ${manager.calculateCarCarbon(bikeDist).format(2)} kg", MaterialTheme.colorScheme.tertiary)
        MetricCard("Walking", "${walkDist.format(1)} km", "Zero Carbon", Color(0xFF4CAF50))
    }
}

@Composable
fun TrackScreen(manager: CarbonManager) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)

    var walkDist by remember { mutableDoubleStateOf(manager.getManualDistance("Walk")) }
    var carDist by remember { mutableDoubleStateOf(manager.getManualDistance("Car")) }
    var busDist by remember { mutableDoubleStateOf(manager.getManualDistance("Bus")) }
    var trainDist by remember { mutableDoubleStateOf(manager.getManualDistance("Train")) }
    var bikeDist by remember { mutableDoubleStateOf(manager.getManualDistance("Bike")) }
    var motorbikeDist by remember { mutableDoubleStateOf(manager.getManualDistance("Motorbike")) }

    LaunchedEffect(Unit) {
        manager.fetchFromCloud()
    }

    Column(modifier = Modifier.padding(20.dp).fillMaxSize().verticalScroll(scrollState)) {
        val user = prefs.getString("currentUsername", "Eco User") ?: "Eco User"
        Text("Start Tracking", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Text("Welcome, $user", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
        
        Spacer(modifier = Modifier.height(30.dp))
        LiveTripTracker(manager)

        Spacer(modifier = Modifier.height(40.dp))
        Text("Manual Logging", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(15.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TransportInput("Car", carDist, Modifier.weight(1f)) { carDist = it; manager.saveManualDistance("Car", it); manager.saveToHistory("Car", it) }
                TransportInput("Motorbike", motorbikeDist, Modifier.weight(1f)) { motorbikeDist = it; manager.saveManualDistance("Motorbike", it); manager.saveToHistory("Motorbike", it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TransportInput("Bus", busDist, Modifier.weight(1f)) { busDist = it; manager.saveManualDistance("Bus", it); manager.saveToHistory("Bus", it) }
                TransportInput("Train", trainDist, Modifier.weight(1f)) { trainDist = it; manager.saveManualDistance("Train", it); manager.saveToHistory("Train", it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TransportInput("Bike", bikeDist, Modifier.weight(1f)) { bikeDist = it; manager.saveManualDistance("Bike", it); manager.saveToHistory("Bike", it) }
                TransportInput("Walk", walkDist, Modifier.weight(1f)) { walkDist = it; manager.saveManualDistance("Walk", it); manager.saveToHistory("Walk", it) }
            }
        }
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
            Text("Live Trip Tracker", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
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
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
        modifier = Modifier.aspectRatio(1f)
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
        Column(modifier = Modifier.padding(16.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, color = tint.copy(alpha = 0.8f))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tint, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TransportInput(label: String, value: Double, modifier: Modifier = Modifier, onValueChange: (Double) -> Unit) {
    OutlinedTextField(value = if(value == 0.0) "" else value.toString(), onValueChange = { onValueChange(it.toDoubleOrNull() ?: 0.0) }, label = { Text(label, fontSize = 12.sp) }, modifier = modifier, singleLine = true, shape = RoundedCornerShape(12.dp))
}

@Composable
fun MetricCard(mode: String, dist: String, co2: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) { 
                Text(mode, fontWeight = FontWeight.Bold, fontSize = 16.sp) 
                Text("Distance: $dist", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) 
            }
            Text(co2, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun HistoryScreen(manager: CarbonManager) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0) }
    var history by remember { mutableStateOf(manager.getHistory()) }
    var weeklyInsight by remember { mutableStateOf<CarbonInsight?>(null) }
    var selectedLog by remember { mutableStateOf<TransportLog?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) { history = manager.getHistory() }
    LaunchedEffect(Unit) { if (manager.hasAllPermissions()) weeklyInsight = manager.getWeeklyData() }

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

    Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
        Text("Activity History", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(20.dp))
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.outline); Text("No trips recorded.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) } }
            } else {
                history.forEach { log ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { selectedLog = log; showEditDialog = true }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val icon = when(log.type) { "Car" -> Icons.Default.DirectionsCar; "Motorbike" -> Icons.Default.TwoWheeler; "Bus" -> Icons.Default.DirectionsBus; "Train" -> Icons.Default.Train; "Bike" -> Icons.Default.DirectionsBike; "Walk" -> Icons.Default.DirectionsWalk; else -> Icons.Default.DirectionsRun }
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) { Text(log.type, fontWeight = FontWeight.Bold); Text(log.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
                            Column(horizontalAlignment = Alignment.End) { val label = if(log.type in listOf("Bike", "Walk")) "Saved" else "Produced"; Text("${log.distance.format(1)} km", fontWeight = FontWeight.Bold); Text("$label ${log.co2.format(2)} kg", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
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

    val galleryLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            profileImageUri = uri
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
        Box(modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).clip(CircleShape).clickable { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
            if (profileImageUri != null) AsyncImage(model = ImageRequest.Builder(context).data(profileImageUri).crossfade(true).build(), contentDescription = "Profile Picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(70.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                Text("Theme", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Light", "Dark", "AMOLED", "System").forEach { theme ->
                        FilterChip(selected = (prefs.getString("app_theme", "System") == theme), onClick = { prefs.edit().putString("app_theme", theme).apply(); (context as? Activity)?.recreate() }, label = { Text(theme, fontSize = 10.sp) }, modifier = Modifier.weight(1f))
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
