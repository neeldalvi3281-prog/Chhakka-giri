package com.aegis.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegis.mobile.data.*
import com.aegis.mobile.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    repository: MessageRepository,
    onGeohashRequest: () -> Unit,
    onNavigateToSos: () -> Unit = {}
) {
    val messages by repository.messages.collectAsState()
    val peers by repository.peers.collectAsState()
    val channels by repository.channels.collectAsState()
    val userProfile by repository.userProfile.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isMeshHubOpen by remember { mutableStateOf(false) }
    var isCallsignDialogOpen by remember { mutableStateOf(false) }
    var logoTapCount by remember { mutableStateOf(0) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val visibleMessages = remember(messages, userProfile.currentChannel) {
        messages.filter { msg ->
            when (msg.type) {
                MessageType.SYSTEM_NOTICE,
                MessageType.COMMAND_ECHO,
                MessageType.ALERT_CRITICAL,
                MessageType.DIRECT_MESSAGE -> true
                MessageType.CHANNEL_BROADCAST -> {
                    msg.channel == null || msg.channel.equals(userProfile.currentChannel, ignoreCase = true)
                }
            }
        }
    }

    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.size - 1)
        }
    }

    Scaffold(
        containerColor = TerminalBg,
        topBar = {
            // 1. Header Bar from Screenshot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: CN Logo Badge & Identity
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Blue CN Badge (Triple-tap = Zeroize)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2A669F))
                            .clickable {
                                logoTapCount++
                                if (logoTapCount >= 3) {
                                    logoTapCount = 0
                                    repository.emergencyZeroize()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CN",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = userProfile.currentChannel.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable { isCallsignDialogOpen = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PhosphorAlertRed)
                            )
                            Text(
                                text = userProfile.callSign,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFF6B8B)
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = Color(0xFFFF6B8B),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                // Right: Pill + Refresh + Mesh Hub Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // SOS Quick Switch Button
                    IconButton(
                        onClick = onNavigateToSos,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFF3D71).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Color(0xFFFF3D71), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "SOS",
                            tint = Color(0xFFFF3D71),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Active Users Pill (👥 1)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E232E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B3444)),
                        modifier = Modifier.clickable { isMeshHubOpen = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Peers",
                                tint = Color(0xFF8E9CAE),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${Math.max(1, peers.size)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Refresh / Rescan Button
                    IconButton(
                        onClick = {
                            repository.addSystemNotice("Scanning mesh frequencies...")
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1E232E), CircleShape)
                            .border(1.dp, Color(0xFF2B3444), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Mesh Hub Asterisk / Settings Button
                    IconButton(
                        onClick = { isMeshHubOpen = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1E232E), CircleShape)
                            .border(1.dp, Color(0xFF2B3444), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Hub",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // 4. Sleek Rounded Bottom Input Bar from Screenshot
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0C1017),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232B3B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = ">",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E9CAE),
                            fontSize = 14.sp
                        )

                        androidx.compose.foundation.text.BasicTextField(
                            value = inputText,
                            onValueChange = { if (it.length <= 200) inputText = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = Color.White
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(PhosphorCyan),
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "${inputText.length}/200",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF6B7280)
                        )

                        // Blue circular Send button with white arrow
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val text = inputText
                                    inputText = ""
                                    repository.handleInput(text)
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (inputText.isNotBlank()) Color(0xFF3B729E) else Color(0xFF232B3B),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 2. Sector / Channel Pills Row from Screenshot
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(channels) { channel ->
                    val isSelected = channel.id.equals(userProfile.currentChannel, ignoreCase = true)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFFBCE5FF) else Color(0xFF1E232E),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFFBCE5FF) else Color(0xFF2B3444)
                        ),
                        modifier = Modifier.clickable {
                            repository.userProfile.value = userProfile.copy(currentChannel = channel.id)
                            repository.addSystemNotice("SWITCHED FREQUENCY -> ${channel.id} [AES-GCM LOCKED]")
                        }
                    ) {
                        Text(
                            text = channel.id,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF0A2240) else Color(0xFF8E9CAE),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Central Terminal Container Box from Screenshot
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF050608))
                    .border(1.dp, Color(0xFF232936), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Terminal Top Bar: NODE: @user_c2ac#6163    🔒 E2EE MESH
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A0C11))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PhosphorAlertRed)
                            )
                            Text(
                                text = "NODE:",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFFFF6B8B)
                            )
                            Text(
                                text = userProfile.callSign,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = Color(0xFFFF6B8B)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PhosphorGreen.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PhosphorGreen.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = PhosphorGreen,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "E2EE MESH",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PhosphorGreen
                                )
                            }
                        }
                    }

                    // Inner Scrollable Log Stream
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleMessages) { msg ->
                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))

                            when (msg.type) {
                                MessageType.SYSTEM_NOTICE -> {
                                    val isSecure = msg.text.contains("Crisis Net") || msg.text.contains("SECURE")
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSecure) PhosphorGreen.copy(alpha = 0.2f) else Color(0xFF1A202C),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSecure) PhosphorGreen.copy(alpha = 0.4f) else Color(0xFF2D3748)
                                            )
                                        ) {
                                            Text(
                                                text = if (isSecure) "SECURE" else "SYS",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSecure) PhosphorGreen else Color(0xFF8E9CAE),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = msg.text,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }

                                MessageType.COMMAND_ECHO -> {
                                    Text(
                                        text = msg.text,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFFBCE5FF),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                MessageType.ALERT_CRITICAL -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF241016),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, PhosphorAlertRed),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "⚠️ EMERGENCY SOS ALERT",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = PhosphorAlertRed
                                                )
                                                Text(
                                                    text = timeStr,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = TextMuted
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = msg.text,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFFFB3C7),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                            Text(
                                                text = "${msg.senderHandle} • HOP: ${msg.hopCount}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                }

                                MessageType.DIRECT_MESSAGE -> {
                                    if (msg.isOutgoing) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFF1B2A3D),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(10.dp))
                                                        Text(
                                                            text = "DIRECT MESSAGE -> ${msg.recipientHandle ?: "PEER"}",
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp,
                                                            color = Color(0xFF00E5FF)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = msg.text,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "E2EE • $timeStr",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 9.sp,
                                                        color = TextMuted,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFF1E172E),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.6f))
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(10.dp))
                                                        Text(
                                                            text = "DIRECT MESSAGE FROM ${msg.senderHandle}",
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp,
                                                            color = Color(0xFFA855F7)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = msg.text,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "$timeStr • E2EE UNICAST",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 9.sp,
                                                        color = TextMuted,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                else -> {
                                    if (msg.isOutgoing) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFF1A2638),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B4060))
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = msg.text,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp,
                                                        color = Color.White
                                                    )
                                                    Row(
                                                        modifier = Modifier.padding(top = 4.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "AES-256",
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 9.sp,
                                                            color = PhosphorGreen
                                                        )
                                                        Text(
                                                            text = "• $timeStr",
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 9.sp,
                                                            color = TextMuted
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFF121620),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222A3A))
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = msg.senderHandle,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFFBCE5FF)
                                                    )
                                                    Text(
                                                        text = msg.text,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(vertical = 2.dp)
                                                    )
                                                    Text(
                                                        text = "$timeStr • E2EE",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 9.sp,
                                                        color = TextMuted
                                                    )
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
        }
    }

    // Callsign Dialog
    if (isCallsignDialogOpen) {
        var newNick by remember { mutableStateOf(userProfile.callSign.removePrefix("@")) }
        AlertDialog(
            onDismissRequest = { isCallsignDialogOpen = false },
            containerColor = TerminalCardBg,
            title = {
                Text(
                    text = "CONFIGURE CALLSIGN",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PhosphorCyan
                )
            },
            text = {
                OutlinedTextField(
                    value = newNick,
                    onValueChange = { newNick = it },
                    leadingIcon = { Text("@", fontFamily = FontFamily.Monospace, color = PhosphorCyan) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color.White)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.userProfile.value = userProfile.copy(callSign = "@$newNick")
                        isCallsignDialogOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhosphorCyan, contentColor = TerminalBg)
                ) {
                    Text("SAVE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Mesh Hub Bottom Sheet
    if (isMeshHubOpen) {
        ModalBottomSheet(
            onDismissRequest = { isMeshHubOpen = false },
            containerColor = TerminalCardBg,
            dragHandle = { BottomSheetDefaults.DragHandle(color = PhosphorCyan) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TACTICAL MESH HUB",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "M-TO-N CLUSTER",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = PhosphorCyan
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                peers.forEach { peer ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TerminalBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderTactical),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(PhosphorGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = peer.callSign,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "PUBKEY: ${peer.keyFingerprint} | ${peer.rssi} dBm",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }

                            Button(
                                onClick = {
                                    inputText = "/msg ${peer.callSign} "
                                    isMeshHubOpen = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PhosphorCyan.copy(alpha = 0.15f),
                                    contentColor = PhosphorCyan
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("DIRECT MSG", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        repository.emergencyZeroize()
                        isMeshHubOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PhosphorAlertRed.copy(alpha = 0.2f),
                        contentColor = PhosphorAlertRed
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "EMERGENCY ZEROIZATION (WIPE RAM)",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
