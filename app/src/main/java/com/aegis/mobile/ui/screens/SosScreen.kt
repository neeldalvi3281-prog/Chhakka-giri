package com.aegis.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import com.aegis.mobile.ui.theme.GreenSuccess
import com.aegis.mobile.ui.theme.NeutralGray
import com.aegis.mobile.ui.theme.RedLight
import com.aegis.mobile.ui.theme.RedPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(
    onBack: () -> Unit,
    onSendSos: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isSent by remember { mutableStateOf(false) }

    val emergencyTags = listOf(
        "Medical / Injured",
        "Search & Rescue",
        "Fire Outbreak",
        "Flood / Water",
        "Evac Needed",
        "Collapse Hazard"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency SOS", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0E12)
                )
            )
        },
        containerColor = Color(0xFF08090C)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // TOP 60%: Giant Emergency SOS / Full Stop Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.60f)
                    .background(Color(0xFF0A0B0E)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
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
                                        if (!isSent) {
                                            val sosText = text.ifBlank { "EMERGENCY SOS DISTRESS SIGNAL" }
                                            onSendSos(sosText)
                                            isSent = true
                                        }
                                    },
                                    onPress = {
                                        if (!isSent) {
                                            isListening = true
                                            tryAwaitRelease()
                                            isListening = false
                                            val sosText = text.ifBlank { "Emergency assistance needed. Medical priority." }
                                            onSendSos(sosText)
                                            isSent = true
                                        }
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
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isSent) "SENT" else if (isListening) "RECORDING" else "SOS",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = if (isSent) "BROADCAST ACTIVE" else "TAP / HOLD TO SEND",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isListening) "Transcribing voice input..." else "Press once to broadcast or hold to speak",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // MIDDLE 20%: Description Area and GPS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.20f)
                    .background(Color(0xFF0D1018))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Describe situation (e.g., Medical, trapped, fire)...", fontSize = 12.sp, color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSent,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF3D71),
                            unfocusedBorderColor = Color(0xFF232D3F),
                            focusedContainerColor = Color(0xFF06080D),
                            unfocusedContainerColor = Color(0xFF06080D),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10141E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GPS: 37.7749° N, 122.4194° W (±8m)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // BOTTOM 10%: Suggestions & Distress Tag Pills
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.10f)
                    .background(Color(0xFF07080C))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SUGGESTIONS:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    emergencyTags.forEach { tag ->
                        Button(
                            onClick = {
                                text = if (text.isBlank()) tag else "$text • $tag"
                            },
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .height(32.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF141822)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Text("+ $tag", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
