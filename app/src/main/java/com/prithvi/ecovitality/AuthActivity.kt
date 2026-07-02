package com.prithvi.ecovitality

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.prithvi.ecovitality.ui.theme.EcoVitalityTheme
import kotlinx.coroutines.launch

class AuthActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(this, "Some permissions were denied. Certain features may not work.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestInitialPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestInitialPermissions()

        val sharedPref = getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
        if (sharedPref.getBoolean("isLoggedIn", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            EcoVitalityTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LoginScreen(
                        onSuccess = { email, password, username, isSignUp, imageUri ->
                            val editor = sharedPref.edit()
                            if (isSignUp) {
                                editor.putString("user_$email", password)
                                editor.putString("username_$email", username)
                                imageUri?.let { uri ->
                                    // Take persistable permission if possible
                                    try {
                                        contentResolver.takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    } catch (e: Exception) {
                                        Log.e("AuthActivity", "Failed to take persistable URI permission", e)
                                    }
                                    editor.putString("image_$email", uri.toString())
                                }
                            } else {
                                val savedPassword = sharedPref.getString("user_$email", null)
                                if (savedPassword == null) {
                                    Toast.makeText(this, "Email not found. Please sign up.", Toast.LENGTH_LONG).show()
                                    return@LoginScreen
                                } else if (savedPassword != password) {
                                    Toast.makeText(this, "Incorrect password.", Toast.LENGTH_LONG).show()
                                    return@LoginScreen
                                }
                            }
                            
                            val finalUsername = if (isSignUp) username else sharedPref.getString("username_$email", email.split("@")[0])
                            val finalImage = if (isSignUp) imageUri?.toString() else sharedPref.getString("image_$email", null)

                            editor.putBoolean("isLoggedIn", true)
                            editor.putString("currentUser", email)
                            editor.putString("currentUsername", finalUsername)
                            finalImage?.let { editor.putString("currentImage", it) }
                            editor.apply()
                            
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onSuccess: (String, String, String, Boolean, Uri?) -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            Toast.makeText(context, "Permission denied. Cannot select photo.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.padding(30.dp).fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("EcoVitality", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(if (isSignUp) "Create Your Account" else "Welcome Back", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp)

        Spacer(modifier = Modifier.height(30.dp))

        if (isSignUp) {
            Box(
                modifier = Modifier.size(100.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .clip(CircleShape)
                    .clickable {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(selectedImageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(50.dp))
                }
            }
            Text("Tap to change photo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.height(15.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                emailError = if (Patterns.EMAIL_ADDRESS.matcher(it).matches() || it.isEmpty()) null else "Invalid Email"
            },
            label = { Text("Email Address") },
            isError = emailError != null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        emailError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

        Spacer(modifier = Modifier.height(15.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(25.dp))

        Button(
            onClick = { 
                if (email.isNotEmpty() && password.isNotEmpty() && emailError == null && (!isSignUp || username.isNotEmpty())) {
                    onSuccess(email, password, username, isSignUp, selectedImageUri)
                } else {
                    Toast.makeText(context, "Please fill in all fields correctly", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(if (isSignUp) "Sign Up" else "Login", fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimary)
        }

        Spacer(modifier = Modifier.height(15.dp))

        OutlinedButton(
            onClick = {
                val credentialManager = CredentialManager.create(context)
                val webClientId = "591202898848-02slgo8o0ulppd2dp2792dilt83lune4.apps.googleusercontent.com" 

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                scope.launch {
                    try {
                        val result = credentialManager.getCredential(context = context, request = request)
                        val credential = result.credential
                        
                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val userEmail = googleIdTokenCredential.id
                            val displayName = googleIdTokenCredential.displayName ?: userEmail.split("@")[0]
                            val profilePictureUri = googleIdTokenCredential.profilePictureUri
                            
                            val sharedPref = context.getSharedPreferences("EcoVitalityPrefs", Context.MODE_PRIVATE)
                            sharedPref.edit()
                                .putBoolean("isLoggedIn", true)
                                .putString("currentUser", userEmail)
                                .putString("currentUsername", displayName)
                                .putString("currentImage", profilePictureUri?.toString())
                                .apply()
                            
                            context.startActivity(Intent(context, MainActivity::class.java))
                            (context as AuthActivity).finish()
                        }
                    } catch (e: GetCredentialException) {
                        Log.e("AuthActivity", "Login failed", e)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Login with Google", color = MaterialTheme.colorScheme.primary)
        }

        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(if (isSignUp) "Already have an account? Login" else "New here? Create account", color = MaterialTheme.colorScheme.primary)
        }
    }
}
