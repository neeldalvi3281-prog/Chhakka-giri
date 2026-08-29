package com.aegis.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegis.mobile.ui.theme.GreenSuccess
import com.aegis.mobile.ui.theme.NeutralDark
import com.aegis.mobile.ui.theme.NeutralGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateMessageScreen(
    onBack: () -> Unit,
    onSendPrivate: (destination: String, text: String) -> Unit
) {
    var destination by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var isSent by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Private Message", fontWeight = FontWeight.Bold) },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Message, contentDescription = null, tint = NeutralDark)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PRIVATE MESSAGE",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = NeutralDark
                )
            }

            Text(
                text = "Targeted peer-to-peer unicast through the multi-hop mesh",
                color = NeutralGray,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it.uppercase() },
                label = { Text("Destination device ID") },
                placeholder = { Text("NODE-E4A1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                enabled = !isSent
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Message") },
                placeholder = { Text("Enter confidential message...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                shape = RoundedCornerShape(12.dp),
                enabled = !isSent
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (destination.isNotBlank() && text.isNotBlank() && !isSent) {
                        onSendPrivate(destination, text)
                        isSent = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSent) GreenSuccess else NeutralDark
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = (destination.isNotBlank() && text.isNotBlank()) || isSent
            ) {
                if (isSent) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SENT", fontWeight = FontWeight.Bold, color = Color.White)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SEND MESSAGE", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            if (isSent) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Message stored. Routing to $destination through mesh.",
                        color = GreenSuccess,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
