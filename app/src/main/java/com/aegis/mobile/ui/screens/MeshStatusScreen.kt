package com.aegis.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegis.mobile.data.MeshStatusSummary
import com.aegis.mobile.ui.theme.GreenSuccess
import com.aegis.mobile.ui.theme.NeutralDark
import com.aegis.mobile.ui.theme.NeutralGray
import com.aegis.mobile.ui.theme.RedPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshStatusScreen(
    onBack: () -> Unit,
    status: MeshStatusSummary,
    deviceId: String
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh Status", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                text = "MESH STATUS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = NeutralDark
            )

            Text(
                text = "Local node: $deviceId",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = NeutralGray,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatusItem(
                        icon = Icons.Default.Info,
                        label = "Mesh",
                        value = if (status.meshActive) "Active" else "Disabled",
                        valueColor = if (status.meshActive) GreenSuccess else NeutralGray
                    )
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))

                    StatusItem(
                        icon = Icons.Default.Share,
                        label = "Nearby nodes",
                        value = "${status.nearbyNodeCount} node(s) in range"
                    )
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))

                    StatusItem(
                        icon = Icons.Default.Storage,
                        label = "Messages stored",
                        value = "${status.messagesStored}"
                    )
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))

                    StatusItem(
                        icon = Icons.Default.Schedule,
                        label = "Messages pending",
                        value = "${status.messagesPending}"
                    )
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))

                    StatusItem(
                        icon = Icons.Default.Wifi,
                        label = "Internet",
                        value = status.internetStatus,
                        valueColor = if (status.internetStatus == "Online") GreenSuccess else NeutralGray
                    )
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))

                    StatusItem(
                        icon = Icons.Default.CloudUpload,
                        label = "Gateway",
                        value = status.gatewayStatus
                    )
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))

                    StatusItem(
                        icon = Icons.Default.LocationOn,
                        label = "GPS",
                        value = status.gpsStatus,
                        iconTint = RedPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusItem(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = NeutralDark,
    iconTint: Color = NeutralGray
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, fontSize = 14.sp, color = NeutralGray, fontWeight = FontWeight.Medium)
        }
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
