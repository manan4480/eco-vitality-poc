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
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.automirrored.filled.*
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
            var showOnboarding by remember { mutableStateOf(prefs.getBoolean("show_onboarding_v2", true)) }

            EcoVitalityTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val manager = remember { CarbonManager(this) }
                
                if (showOnboarding) {
                    OnboardingDialog(
                        manager = manager,
                        onDismiss = { 
                            showOnboarding = false
                            prefs.edit().putBoolean("show_onboarding_v2", false).apply()
                        }
                    )
                }
                var profileImageUri by remember { 
                    mutableStateOf(
                        prefs.getString("currentImage", null)?.let { Uri.parse(it) } ?: 
                        prefs.getString("image_${prefs.getString("currentUser", "")}", null)?.let { Uri.parse(it) }
                    ) 
                }
                
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Scaffold(
                        topBar = { TopBar(navController, profileImageUri) },
                        bottomBar = { BottomNavigationBar(navController) }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            NavHost(navController = navController, startDestination = "home") {
                                composable("home") { HomeScreen(manager, navController) }
                                composable("dashboard") { DashboardScreen(manager) }
                                composable("track") { TrackScreen(manager) }
                                composable("history") { HistoryScreen(manager) }
                                composable("rewards") { RewardsScreen(manager) }
                                composable("digital_wellbeing") { DigitalWellbeingScreen(manager) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(navController: NavController, profileImageUri: Uri?) {
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isProfilePage = currentRoute == "profile"

    TopAppBar(
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isProfilePage) {
                    Icon(Icons.Default.Eco, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isProfilePage) "My Profile" else "EcoVitality", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        navigationIcon = {
            if (isProfilePage) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (!isProfilePage) {
                IconButton(onClick = { navController.navigate("profile") }) {
                    if (profileImageUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(profileImageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Triple("home", "Home", Icons.Default.Home),
        Triple("dashboard", "Impact", Icons.Default.Public),
        Triple("track", "Track", Icons.Default.AddLocationAlt),
        Triple("history", "History", Icons.Default.History),
        Triple("rewards", "Rewards", Icons.Default.CardGiftcard)
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
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
    saved: Double,
    produced: Double,
    size: Dp = 240.dp,
    strokeWidth: Dp = 14.dp,
    showCenterIcon: Boolean = true,
    xpTarget: Float = 500f,
    savedTarget: Double = 10.0,
    producedTarget: Double = 5.0
) {
    val animatedXp by animateFloatAsState(targetValue = (xp / xpTarget).coerceIn(0f, 1f), label = "xp")
    val animatedSaved by animateFloatAsState(targetValue = (saved / savedTarget).coerceIn(0.0, 1.0).toFloat(), label = "saved")
    val animatedProduced by animateFloatAsState(targetValue = (produced / producedTarget).coerceIn(0.0, 1.0).toFloat(), label = "produced")

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        // XP Ring (Outer)
        CircularProgressIndicator(
            progress = { animatedXp },
            modifier = Modifier.size(size),
            color = Color(0xFFFBC02D), // Gold
            strokeWidth = strokeWidth,
            trackColor = Color(0xFFFBC02D).copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round
        )
        // CO2 Saved Ring
        CircularProgressIndicator(
            progress = { animatedSaved },
            modifier = Modifier.size(size * 0.75f),
            color = Color(0xFF4CAF50), // Eco Green
            strokeWidth = strokeWidth,
            trackColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round
        )
        // CO2 Produced Ring (Inner)
        CircularProgressIndicator(
            progress = { animatedProduced },
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
fun HomeScreen(manager: CarbonManager, navController: NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
    var summary by remember { mutableStateOf(StatsSummary()) }
    var todayHistory by remember { mutableStateOf(emptyList<TransportLog>()) }
    
    val greeting = remember {
        val hour = java.time.LocalTime.now().hour
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            summary = manager.getTodaySummary()
            todayHistory = manager.getHistory().filter { it.date == LocalDate.now().format(DateTimeFormatter.ISO_DATE) }
            kotlinx.coroutines.delay(5000L)
        }
    }

    Column(modifier = Modifier.padding(20.dp).fillMaxSize().verticalScroll(scrollState)) {
        Text(greeting, style = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.primary))
        Text("Your Day Today", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth().clickable { navController.navigate("digital_wellbeing") },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM")), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(16.dp))
                
                VitalityRings(
                    xp = summary.xp,
                    saved = summary.co2Saved,
                    produced = 0.0,
                    size = 200.dp,
                    strokeWidth = 14.dp,
                    xpTarget = prefs.getFloat("target_xp", 500f),
                    savedTarget = prefs.getFloat("target_saved", 5f).toDouble(),
                    producedTarget = prefs.getFloat("target_produced", 2f).toDouble()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActivityMetric("Steps", summary.steps.toString(), Icons.AutoMirrored.Filled.DirectionsWalk)
                    ActivityMetric("Total XP", summary.xp.toString(), Icons.Default.Bolt, Color(0xFFFBC02D))
                    ActivityMetric("CO2 Saved", "${summary.co2Saved.format(2)} kg", Icons.Default.Nature)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Tap for Digital Wellbeing Insights", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
        
        Text("Encouragement", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Park, contentDescription = null, tint = Color(0xFF4CAF50))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    if (summary.steps < 5000) "You're doing great! Try to reach 5,000 steps today to save even more CO2."
                    else "Excellent work! Every extra step helps our planet breathe easier.",
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
        Text("Logged Today", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(10.dp))

        if (todayHistory.isEmpty()) {
            Text("No trips logged yet today. Start tracking to see your impact!", color = Color.Gray, modifier = Modifier.padding(vertical = 10.dp))
        } else {
            todayHistory.forEach { log ->
                HistoryItem(log)
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun HistoryItem(log: TransportLog) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), 
        shape = RoundedCornerShape(16.dp), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when(log.type) { 
                    "Car" -> Icons.Default.DirectionsCar
                    "Motorbike" -> Icons.Default.TwoWheeler
                    "Bus" -> Icons.Default.DirectionsBus
                    "Train" -> Icons.Default.Train
                    "Bike" -> Icons.AutoMirrored.Filled.DirectionsBike
                    "Walk" -> Icons.AutoMirrored.Filled.DirectionsWalk
                    else -> Icons.AutoMirrored.Filled.DirectionsRun 
                }
                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) { 
                    Text(log.type, fontWeight = FontWeight.Bold, fontSize = 16.sp) 
                    if (log.startTime.isNotEmpty()) {
                        Text("${log.startTime} - ${log.endTime}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                Column(horizontalAlignment = Alignment.End) { 
                    Text("${log.distance.format(1)} km", fontWeight = FontWeight.Bold)
                    Text("${log.xpEarned} XP", fontSize = 12.sp, color = Color(0xFFFBC02D), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(manager: CarbonManager) {
    val scrollState = rememberScrollState()
    var summary by remember { mutableStateOf(StatsSummary()) }

    LaunchedEffect(Unit) {
        summary = manager.getLifetimeSummary()
    }

    Column(modifier = Modifier.padding(20.dp).fillMaxSize().verticalScroll(scrollState)) {
        Text("Lifetime Impact", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Text("Your journey since joining EcoVitality", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        
        Spacer(modifier = Modifier.height(30.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Total Carbon Saved", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
                        Text("${summary.co2Saved.format(1)} kg", fontWeight = FontWeight.Bold, fontSize = 36.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Park, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Equivalent to ${(summary.co2Saved / 20.0).format(1)} tree years of absorption", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
        Text("Cumulative Metrics", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ImpactCard(Modifier.weight(1f), "Lifetime XP", summary.xp.toString(), Icons.Default.Stars, Color(0xFFFBC02D))
            ImpactCard(Modifier.weight(1f), "Total Distance", "${summary.distance.format(0)} km", Icons.Default.Route, MaterialTheme.colorScheme.secondary)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ImpactCard(Modifier.weight(1f), "Total Trips", summary.trips.toString(), Icons.Default.History, MaterialTheme.colorScheme.primary)
            ImpactCard(Modifier.weight(1f), "Overall Rank", if(summary.xp > 10000) "Eco Legend" else if(summary.xp > 5000) "Green Warrior" else "Earth Guardian", Icons.Default.MilitaryTech, Color(0xFFFBC02D))
        }

        Spacer(modifier = Modifier.height(40.dp))
        Text("Detailed History", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Browse your activity day-by-day in the History tab.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        
        Spacer(modifier = Modifier.height(20.dp))
        BenchmarkSection()
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun ImpactCard(modifier: Modifier, label: String, value: String, icon: ImageVector, color: Color) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
fun DigitalWellbeingScreen(manager: CarbonManager) {
    val context = LocalContext.current
    var summary by remember { mutableStateOf(manager.getDigitalWellbeingSummary()) }
    var hasPermission by remember { mutableStateOf(manager.hasUsageStatsPermission()) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        while(true) {
            hasPermission = manager.hasUsageStatsPermission()
            if (hasPermission) {
                summary = manager.getDigitalWellbeingSummary()
            }
            delay(5000L)
        }
    }

    Column(modifier = Modifier.padding(20.dp).fillMaxSize().verticalScroll(scrollState)) {
        Text("Digital Wellbeing", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Text("Phone Energy & Usage Insights", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        
        Spacer(modifier = Modifier.height(30.dp))

        if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Permission Required", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("We need 'Usage Access' permission to show which apps use the most energy.", textAlign = TextAlign.Center, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFBC02D), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Total Phone Energy", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
                            Text("${summary.totalEnergyMah.format(1)} mAh", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Total Screen Time: ${summary.totalScreenTimeMillis / 3600000}h ${(summary.totalScreenTimeMillis % 3600000) / 60000}m", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("App Energy Breakdown", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))

            summary.appUsages.forEach { app ->
                AppUsageItem(app)
            }
            
            Spacer(modifier = Modifier.height(30.dp))
            Text("Impact on Score", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Digital Eco Score: ${summary.digitalEcoScore}/100", fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Excessive phone use decreases your daily sustainability rating and applies an XP penalty of ${summary.digitalXpPenalty} XP.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun AppUsageItem(app: AppUsageInfo) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app.icon != null) {
                AsyncImage(
                    model = app.icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                )
            } else {
                Box(modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp)))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val hours = app.usageTimeMillis / 3600000
                val mins = (app.usageTimeMillis % 3600000) / 60000
                Text(if(hours > 0) "${hours}h ${mins}m" else "${mins}m", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            Text("${app.estimatedEnergyMah.format(1)} mAh", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun TrackScreen(manager: CarbonManager) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    var selectedMode by remember { mutableStateOf("Car") }
    var distanceInput by remember { mutableStateOf("") }
    
    val modes = listOf(
        Triple("Car", Icons.Default.DirectionsCar, MaterialTheme.colorScheme.error),
        Triple("Motorbike", Icons.Default.TwoWheeler, Color(0xFF9C27B0)),
        Triple("Bus", Icons.Default.DirectionsBus, MaterialTheme.colorScheme.secondary),
        Triple("Train", Icons.Default.Train, MaterialTheme.colorScheme.primary),
        Triple("Bike", Icons.AutoMirrored.Filled.DirectionsBike, MaterialTheme.colorScheme.tertiary),
        Triple("Walk", Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFF4CAF50))
    )

    Column(modifier = Modifier.padding(20.dp).fillMaxSize().verticalScroll(scrollState)) {
        Text("Track Activity", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Spacer(modifier = Modifier.height(30.dp))
        
        LiveTripTracker(manager)

        Spacer(modifier = Modifier.height(40.dp))
        Text("Manual Log", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Log a completed trip by entering the details below.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(20.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Select Transport Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    modes.forEach { (mode, icon, color) ->
                        val isSelected = selectedMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMode = mode },
                            label = { Text(mode) },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if(isSelected) MaterialTheme.colorScheme.onPrimaryContainer else color) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Distance (km)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = distanceInput,
                    onValueChange = { if(it.isEmpty() || it.toDoubleOrNull() != null) distanceInput = it },
                    placeholder = { Text("e.g. 5.5") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        val dist = distanceInput.toDoubleOrNull() ?: 0.0
                        if (dist > 0) {
                            manager.saveToHistory(selectedMode, dist)
                            distanceInput = ""
                            Toast.makeText(context, "Trip logged to history!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enter a valid distance", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Trip to History", fontWeight = FontWeight.Bold)
                }
            }
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
        Triple("Bike", Icons.AutoMirrored.Filled.DirectionsBike, MaterialTheme.colorScheme.tertiary),
        Triple("Walk", Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFF4CAF50))
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
fun HistoryScreen(manager: CarbonManager) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var dailyInsight by remember { mutableStateOf<CarbonInsight?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(manager.getHistory()) }
    var selectedLog by remember { mutableStateOf<TransportLog?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(true) }
    
    // Dynamic Targets
    val xpTarget = prefs.getFloat("target_xp", 500f)
    val savedTarget = prefs.getFloat("target_saved", 5.0f).toDouble()
    val producedTarget = prefs.getFloat("target_produced", 2.0f).toDouble()

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
                                "Bus" -> it.distance * (manager.getCarFactor() - manager.BUS_FACTOR)
                                "Train" -> it.distance * (manager.getCarFactor() - manager.TRAIN_FACTOR)
                                "Bike", "Walk" -> it.distance * manager.getCarFactor()
                                else -> 0.0
                            }
                        }
                        val totalProduced = todayHistory.filter { it.type in listOf("Car", "Motorbike", "Bus", "Train") }.sumOf { it.co2 }
                        
                        dayStats = Triple(totalXp, 0, Triple(totalSaved, totalProduced, 0))
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
                            saved = dayStats.third.first,
                            produced = dayStats.third.second,
                            size = 40.dp,
                            strokeWidth = 3.dp,
                            showCenterIcon = false,
                            xpTarget = xpTarget,
                            savedTarget = savedTarget,
                            producedTarget = producedTarget
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
            val dailyEcoScore = if (totalXpForDay == 0 && totalProducedForDay == 0.0) 0 
                               else manager.calculateEcoScore(
                                   walk = dailyInsight!!.dailyDistance + todayHistory.filter { it.type == "Walk" }.sumOf { it.distance },
                                   bike = todayHistory.filter { it.type == "Bike" }.sumOf { it.distance },
                                   motorbike = todayHistory.filter { it.type == "Motorbike" }.sumOf { it.distance },
                                   car = todayHistory.filter { it.type == "Car" }.sumOf { it.distance },
                                   bus = todayHistory.filter { it.type == "Bus" }.sumOf { it.distance },
                                   train = todayHistory.filter { it.type == "Train" }.sumOf { it.distance }
                               )

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    VitalityRings(
                        xp = totalXpForDay,
                        saved = totalSavedForDay,
                        produced = totalProducedForDay,
                        size = 180.dp,
                        strokeWidth = 12.dp,
                        xpTarget = xpTarget,
                        savedTarget = savedTarget,
                        producedTarget = producedTarget
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActivityMetric("Steps", "${dailyInsight!!.dailySteps}", Icons.AutoMirrored.Filled.DirectionsWalk)
                        ActivityMetric("Total XP", "${totalXpForDay}", Icons.Default.Bolt, Color(0xFFFBC02D))
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
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { selectedLog = log; showEditDialog = true }, 
                    shape = RoundedCornerShape(16.dp), 
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when(log.type) { 
                                "Car" -> Icons.Default.DirectionsCar
                                "Motorbike" -> Icons.Default.TwoWheeler
                                "Bus" -> Icons.Default.DirectionsBus
                                "Train" -> Icons.Default.Train
                                "Bike" -> Icons.AutoMirrored.Filled.DirectionsBike
                                "Walk" -> Icons.AutoMirrored.Filled.DirectionsWalk
                                else -> Icons.AutoMirrored.Filled.DirectionsRun 
                            }
                            Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) { 
                                Text(log.type, fontWeight = FontWeight.Bold, fontSize = 16.sp) 
                                if (log.startTime.isNotEmpty()) {
                                    Text("${log.startTime} - ${log.endTime} (${log.durationMinutes} mins)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) { 
                                Text("${log.distance.format(1)} km", fontWeight = FontWeight.Bold)
                                Text("${log.xpEarned} XP", fontSize = 12.sp, color = Color(0xFFFBC02D), fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val isEco = log.type in listOf("Bike", "Walk", "Bus", "Train")
                            val impactColor = if (log.ecoScoreImpact >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Eco, contentDescription = null, tint = impactColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Eco Impact: ${if(log.ecoScoreImpact > 0) "+" else ""}${log.ecoScoreImpact}", fontSize = 12.sp, color = impactColor, fontWeight = FontWeight.Medium)
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Co2, contentDescription = null, tint = if(isEco) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${if(isEco) "Saved" else "Produced"}: ${log.co2.format(2)} kg", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
        Text("Reference Benchmarks", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        BenchmarkSection()
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun BenchmarkSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BenchmarkItem("Avg. Daily Commute CO2", "12.6 kg", "Global Average", MaterialTheme.colorScheme.error)
        BenchmarkItem("Avg. Daily Walking", "1.5 km", "Health Recommendation", Color(0xFF4CAF50))
        BenchmarkItem("Avg. Transport Footprint", "25%", "of Individual Total", MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun BenchmarkItem(label: String, value: String, source: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(source, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        }
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
fun ActivityMetric(label: String, value: String, icon: ImageVector, tint: Color = Color(0xFF4CAF50)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color.Gray)
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
    
    // Dynamic Goals State
    var showGoalsMenu by remember { mutableStateOf(false) }
    var xpTarget by remember { mutableFloatStateOf(prefs.getFloat("target_xp", 500f)) }
    var savedTarget by remember { mutableFloatStateOf(prefs.getFloat("target_saved", 5.0f)) }
    var producedLimit by remember { mutableFloatStateOf(prefs.getFloat("target_produced", 2.0f)) }

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
        
        // Goals Expansion Menu
        Card(
            modifier = Modifier.fillMaxWidth(), 
            shape = RoundedCornerShape(20.dp), 
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showGoalsMenu = !showGoalsMenu }) {
                    Icon(Icons.Default.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Daily Goal Targets", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(if(showGoalsMenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
                
                AnimatedVisibility(visible = showGoalsMenu) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        GoalSlider("Daily XP Target", xpTarget, 100f, 2000f, "XP") { xpTarget = it; prefs.edit().putFloat("target_xp", it).apply() }
                        GoalSlider("Daily CO2 Saved (kg)", savedTarget, 1f, 20f, "kg") { savedTarget = it; prefs.edit().putFloat("target_saved", it).apply() }
                        GoalSlider("Daily Produced Limit (kg)", producedLimit, 0.5f, 10f, "kg") { producedLimit = it; prefs.edit().putFloat("target_produced", it).apply() }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { 
                        prefs.edit().putBoolean("show_onboarding_v2", true).apply()
                        (context as Activity).recreate() 
                    }, 
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(12.dp)
                ) { 
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Show Tutorial") 
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileOption(Icons.AutoMirrored.Filled.Help, "Help & Support")
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
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout", fontSize = 16.sp, fontWeight = FontWeight.Bold) 
        }
    }
}

@Composable
fun GoalSlider(label: String, value: Float, min: Float, max: Float, unit: String, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${value.toInt()} $unit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ProfileOption(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(16.dp)); Text(title, fontWeight = FontWeight.Medium); Spacer(modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline) }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)

@Composable
fun OnboardingDialog(manager: CarbonManager, onDismiss: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    
    val steps = listOf(
        "Welcome to EcoVitality! Track your carbon footprint and earn rewards for sustainable travel.",
        "Dashboard: View your real-time stats, XP, and Eco Score rings.",
        "Track: Log trips manually or use Auto-Tracking for seamless updates.",
        "History: Every trip is saved independently with details like duration and CO2 impact.",
        "XP & Rewards: Earn XP for eco-friendly choices and unlock real-world rewards!",
        "Health Connect: We use Health Connect to sync your steps and walking distance automatically."
    )
    
    val icons = listOf(
        Icons.Default.Eco,
        Icons.Default.Dashboard,
        Icons.Default.AddLocationAlt,
        Icons.Default.History,
        Icons.Default.CardGiftcard,
        Icons.Default.Favorite
    )

    AlertDialog(
        onDismissRequest = { },
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icons[currentStep], contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Step ${currentStep + 1} of ${steps.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(100.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icons[currentStep], contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(steps[currentStep], textAlign = TextAlign.Center, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                if (currentStep == steps.size - 1) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { 
                            val status = androidx.health.connect.client.HealthConnectClient.getSdkStatus(context)
                            if (status == androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED || 
                                status == androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE) {
                                context.startActivity(manager.getInstallIntent())
                            } else {
                                Toast.makeText(context, "Health Connect is ready!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Check/Install Health Connect", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (currentStep < steps.size - 1) currentStep++
                else onDismiss()
            }, shape = RoundedCornerShape(12.dp)) {
                Text(if (currentStep < steps.size - 1) "Next" else "Get Started")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
