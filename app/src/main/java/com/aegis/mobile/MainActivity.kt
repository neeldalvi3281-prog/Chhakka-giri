package com.aegis.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.draw.clip
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

    private val repository = MessageRepository()
    private var nearbyMeshManager: NearbyMeshManager? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        nearbyMeshManager?.startMesh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Logo & Info
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E293B))
                                        .border(1.dp, Color(0xFFFF3D71).copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("CN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFFFF3D71))
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("CRISIS NET", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White)
                                        Surface(
                                            color = if (peers.isNotEmpty()) Color(0xFF22C55E).copy(alpha = 0.2f) else Color(0xFFFF3D71).copy(alpha = 0.2f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (peers.isNotEmpty()) Color(0xFF22C55E).copy(alpha = 0.5f) else Color(0xFFFF3D71).copy(alpha = 0.4f)
                                            ),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (peers.isNotEmpty()) "MESH ACTIVE (${peers.size})" else "OFFLINE MESH",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (peers.isNotEmpty()) Color(0xFF22C55E) else Color(0xFFFF3D71),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text("${userProfile.callSign} • ${peers.size} PEERS", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Gray)
                                }
                            }

                            // Right: Navigation Buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                NavButton(
                                    text = "TERM",
                                    icon = Icons.Default.Terminal,
                                    isActive = currentScreen == "terminal",
                                    activeColor = Color(0xFF00E5FF),
                                    onClick = { currentScreen = "terminal" }
                                )
                                NavButton(
                                    text = "SOS",
                                    icon = Icons.Default.Warning,
                                    isActive = currentScreen == "sos",
                                    activeColor = Color(0xFFFF3D71),
                                    onClick = { currentScreen = "sos" }
                                )
                                NavButton(
                                    text = "MAP",
                                    icon = Icons.Default.Map,
                                    isActive = currentScreen == "map",
                                    activeColor = Color(0xFF00E5FF),
                                    onClick = { currentScreen = "map" }
                                )
                                NavButton(
                                    text = "PEERS",
                                    icon = Icons.Default.Radio,
                                    isActive = currentScreen == "peers",
                                    activeColor = Color(0xFF22C55E),
                                    onClick = { currentScreen = "peers" }
                                )
                            }
                        }

                        // Screen Content
                        Box(modifier = Modifier.weight(1f)) {
                            when (currentScreen) {
                                "sos" -> SosScreen(
                                    onBack = { currentScreen = "terminal" },
                                    onSendSos = { sosMsg ->
                                        repository.handleInput("/sos $sosMsg")
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
        nearbyMeshManager?.stopMesh()
    }
}

@Composable
fun NavButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean, activeColor: Color, onClick: () -> Unit) {
    Surface(
        color = if (isActive) activeColor else Color(0xFF15171E),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) activeColor else Color(0xFF2B3444)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = text, tint = if (isActive) Color.Black else activeColor, modifier = Modifier.size(12.dp))
            Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isActive) Color.Black else activeColor, fontFamily = FontFamily.Monospace)
        }
    }
}
