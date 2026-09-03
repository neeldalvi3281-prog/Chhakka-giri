package com.aegis.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegis.mobile.data.DeliveryStatus
import com.aegis.mobile.data.MessageRepository
import com.aegis.mobile.data.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(
    repository: MessageRepository,
    onBack: () -> Unit,
    onSendSos: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isSent by remember { mutableStateOf(false) }

    val messages by repository.messages.collectAsState()
    val currentLocation by repository.currentLocation.collectAsState()

    val sosMessages = remember(messages) {
        messages.filter { it.type == MessageType.ALERT_CRITICAL }.reversed()
    }

    val emergencyTags = listOf(
        "Trapped in Debris",
        "Medical / Injured",
        "Search & Rescue",
        "Fire Outbreak",
        "Structural Collapse",
        "Flood / Water",
        "Evac Needed"
    )

    val lat = currentLocation?.first ?: 23.0301
    val lng = currentLocation?.second ?: 72.5852

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFFFF3D71), CircleShape)
                        )
                        Text(
                            "EMERGENCY SOS BEACON",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = Color(0xFFFF3D71)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { repository.triggerSosSync() }) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Sync Gateway", tint = Color(0xFF00E5FF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0E12)
                )
            )
        },
        containerColor = Color(0xFF08090C)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // TOP: Giant Emergency SOS Button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0B0E))
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = if (isSent) {
                                            listOf(Color(0xFF16A34A), Color(0xFF15803D))
                                        } else if (isListening) {
                                            listOf(Color(0xFFFF1744), Color(0xFFD50000))
                                        } else {
                                            listOf(Color(0xFFFF3D71), Color(0xFFB01441))
                                        }
                                    ),
                                    shape = CircleShape
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            val sosText = text.ifBlank { "EMERGENCY SOS DISTRESS SIGNAL: IMMEDIATE RESCUE NEEDED" }
                                            onSendSos(sosText)
                                            isSent = true
                                        },
                                        onPress = {
                                            isListening = true
                                            tryAwaitRelease()
                                            isListening = false
                                            val sosText = text.ifBlank { "Emergency assistance needed. Medical priority." }
                                            onSendSos(sosText)
                                            isSent = true
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSent) Icons.Default.Check else if (isListening) Icons.Default.Mic else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(46.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isSent) "SENT" else if (isListening) "REC" else "SOS",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    text = if (isSent) "SAVED & BROADCASTING" else "TAP TO BROADCAST",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isSent) "SOS registered in local Room DB & broadcasting over mesh." else "Tap to send emergency alert to mesh & gateway",
                            color = if (isSent) Color(0xFF22C55E) else Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // MIDDLE: Description Area and GPS
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1018))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "DISTRESS SITUATION DESCRIPTION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFF3D71)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            isSent = false
                        },
                        placeholder = { Text("Describe location & situation (e.g., trapped under debris near Shahpur)...", fontSize = 12.sp, color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF3D71),
                            unfocusedBorderColor = Color(0xFF232D3F),
                            focusedContainerColor = Color(0xFF06080D),
                            unfocusedContainerColor = Color(0xFF06080D),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10141E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232D3F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFFF3D71), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "GPS COORDINATES (OFFLINE CACHED)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${String.format("%.4f", lat)}° N, ${String.format("%.4f", lng)}° E (Shahpur Sector)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // SUGGESTION PILLS
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF07080C))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "QUICK DISTRESS TAGS:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        emergencyTags.forEach { tag ->
                            Button(
                                onClick = {
                                    text = if (text.isBlank()) tag else "$text • $tag"
                                    isSent = false
                                },
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .height(30.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF141822)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("+ $tag", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // RECENT SOS BEACONS HISTORY
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SOS BEACON STORE & FORWARD LOGS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                    Text(
                        text = "${sosMessages.size} BEACONS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            if (sosMessages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No SOS beacons registered yet. Terminal is ready.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            } else {
                items(sosMessages) { msg ->
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
                    
                    val (statusColor, statusBg, statusText) = when (msg.status) {
                        DeliveryStatus.QUEUED -> Triple(Color(0xFFFFB74D), Color(0xFF332005), "⏳ SAVED OFFLINE (ROOM DB)")
                        DeliveryStatus.SENT -> Triple(Color(0xFFFF5252), Color(0xFF330B0B), "📡 BROADCASTING ON MESH")
                        DeliveryStatus.RELAYED -> Triple(Color(0xFF40C4FF), Color(0xFF062B3D), "🔄 RELAYED VIA PEERS")
                        DeliveryStatus.UPLOADED -> Triple(Color(0xFF69F0AE), Color(0xFF0A331A), "☁️ UPLOADED TO COMMAND CENTER")
                        DeliveryStatus.DELIVERED -> Triple(Color(0xFFB388FF), Color(0xFF230D3A), "✓ DELIVERED")
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0E121B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E283A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = statusBg,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = statusText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = timeStr,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text(
                                text = msg.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ORIGIN: ${msg.senderHandle} • HOPS: ${msg.hopCount}/${msg.ttl}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFF8E9CAE)
                                )
                                Text(
                                    text = "ID: ${msg.id.take(8)}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFF5A687A)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
