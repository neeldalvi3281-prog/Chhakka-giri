package com.aegis.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegis.mobile.data.MessageRepository
import com.aegis.mobile.ui.theme.PhosphorCyan

@Composable
fun PeersScreen(repository: MessageRepository) {
    val peers by repository.peers.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Radio, contentDescription = "Radio", tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                Text("OFFLINE MESH RADIO TOPOLOGY", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Text("${peers.size} ACTIVE PEERS", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (peers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF252E40), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Radio, contentDescription = null, tint = Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Scanning Bluetooth & Wi-Fi Direct Mesh...", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text("0 physical nodes in immediate radio range.", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(peers) { peer ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF11141D),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF202738)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(peer.callSign, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                                Text(peer.nodeId, color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${peer.rssi} dBm", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                                Text("BATT Hops: ${peer.hopCount}", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF151924),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252E40)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tactical Node Commands:", color = PhosphorCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("• /join <channel> — Switch active mesh frequency", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("• /nick <callsign> — Change radio identifier", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("• /sos <text> — Force critical distress packet", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("• /zeroize — Instant volatile RAM wipe", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
