package com.aegis.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.aegis.mobile.data.MessageRepository
import com.aegis.mobile.mesh.NearbyMeshManager
import com.aegis.mobile.ui.screens.SosScreen
import com.aegis.mobile.ui.screens.TerminalScreen
import com.aegis.mobile.ui.theme.CrisisNetTheme
import com.aegis.mobile.ui.theme.TerminalBg

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
                repository.handleInput(msg.text)
            }
        }

        requestRequiredPermissions()

        setContent {
            CrisisNetTheme {
                var currentScreen by remember { mutableStateOf("sos") }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TerminalBg
                ) {
                    if (currentScreen == "sos") {
                        SosScreen(
                            onBack = { currentScreen = "terminal" },
                            onSendSos = { sosMsg ->
                                repository.handleInput("/sos $sosMsg")
                                currentScreen = "terminal"
                            }
                        )
                    } else {
                        TerminalScreen(
                            repository = repository,
                            onGeohashRequest = {
                                repository.userProfile.value = repository.userProfile.value.copy(currentChannel = "#9q8yy")
                                repository.addSystemNotice("GPS GEOHASH SECTOR LOCKED -> #9q8yy (37.7749°N, 122.4194°W)")
                            },
                            onNavigateToSos = {
                                currentScreen = "sos"
                            }
                        )
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
