package com.aegis.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aegis.mobile.data.MessageRepository
import com.aegis.mobile.mesh.NearbyMeshManager
import com.aegis.mobile.ui.screens.MapScreen
import com.aegis.mobile.ui.screens.PeersScreen
import com.aegis.mobile.ui.screens.SosScreen
import com.aegis.mobile.ui.screens.TerminalScreen
import com.aegis.mobile.ui.theme.CrisisNetTheme
import com.aegis.mobile.ui.theme.TerminalBg
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: MessageRepository
    private lateinit var database: com.aegis.mobile.data.AppDatabase
    private var nearbyMeshManager: NearbyMeshManager? = null
    private var locationManager: LocationManager? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            repository.updateLocation(location.latitude, location.longitude)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        nearbyMeshManager?.startMesh()
        startLocationUpdates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        database = com.aegis.mobile.data.AppDatabase.getDatabase(this)
        repository = MessageRepository(this, database.messageDao())
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        val user = repository.userProfile.value
        nearbyMeshManager = NearbyMeshManager(this, user.callSign, user.nodeId).apply {
            onMessageReceived = { msg ->
                repository.receiveIncomingMessage(msg)
            }
            onPeerStatusChanged = { notice ->
                repository.addSystemNotice(notice)
            }
        }
        repository.meshManager = nearbyMeshManager

        lifecycleScope.launch {
            nearbyMeshManager?.connectedPeers?.collect { peerList ->
                repository.setPeers(peerList)
            }
        }

        requestRequiredPermissions()
        startLocationUpdates()

        setContent {
            CrisisNetTheme {
                var currentScreen by remember { mutableStateOf("terminal") }
                val peers by repository.peers.collectAsState()
                val userProfile by repository.userProfile.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TerminalBg
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Shared Tactical Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D0E12))
                                .border(1.dp, Color(0xFF202636))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NavButton(
                                text = "TERM",
                                icon = Icons.Default.Terminal,
                                isActive = currentScreen == "terminal",
                                activeColor = Color(0xFF00E5FF),
                                modifier = Modifier.weight(1f),
                                onClick = { currentScreen = "terminal" }
                            )
                            NavButton(
                                text = "SOS",
                                icon = Icons.Default.Warning,
                                isActive = currentScreen == "sos",
                                activeColor = Color(0xFFFF3D71),
                                modifier = Modifier.weight(1f),
                                onClick = { currentScreen = "sos" }
                            )
                            NavButton(
                                text = "MAP",
                                icon = Icons.Default.Map,
                                isActive = currentScreen == "map",
                                activeColor = Color(0xFF00E5FF),
                                modifier = Modifier.weight(1f),
                                onClick = { currentScreen = "map" }
                            )
                            NavButton(
                                text = "PEERS",
                                icon = Icons.Default.Radio,
                                isActive = currentScreen == "peers",
                                activeColor = Color(0xFF22C55E),
                                modifier = Modifier.weight(1f),
                                onClick = { currentScreen = "peers" }
                            )
                        }

                        // Screen Content
                        Box(modifier = Modifier.weight(1f)) {
                            when (currentScreen) {
                                "sos" -> SosScreen(
                                    repository = repository,
                                    onBack = { currentScreen = "terminal" },
                                    onSendSos = { sosMsg ->
                                        repository.sendSos(sosMsg)
                                        currentScreen = "terminal"
                                    }
                                )
                                "map" -> MapScreen(
                                    repository = repository,
                                    onBack = { currentScreen = "terminal" },
                                    onSelectGeohash = { hash ->
                                        repository.handleInput("/geohash $hash")
                                        currentScreen = "terminal"
                                    }
                                )
                                "terminal" -> TerminalScreen(
                                    repository = repository,
                                    onGeohashRequest = { currentScreen = "map" },
                                    onNavigateToSos = { currentScreen = "sos" }
                                )
                                "peers" -> PeersScreen(repository = repository)
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                locationManager?.let { lm ->
                    val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    val best = lastGps ?: lastNet
                    if (best != null) {
                        repository.updateLocation(best.latitude, best.longitude)
                    }

                    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 10f, locationListener)
                    } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 10f, locationListener)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Location provider init note: ${e.message}")
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            nearbyMeshManager?.startMesh()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            // Ignored
        }
        nearbyMeshManager?.stopMesh()
    }
}

@Composable
fun NavButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean, activeColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (isActive) activeColor else Color(0xFF15171E),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) activeColor else Color(0xFF2B3444)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = text, tint = if (isActive) Color.Black else activeColor, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isActive) Color.Black else activeColor, fontFamily = FontFamily.Monospace)
        }
    }
}
