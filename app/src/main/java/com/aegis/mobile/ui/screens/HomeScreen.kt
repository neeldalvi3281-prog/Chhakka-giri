package com.aegis.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegis.mobile.ui.theme.NeutralGray
import com.aegis.mobile.ui.theme.RedPrimary

@Composable
fun HomeScreen(
    onSosClick: () -> Unit,
    onBroadcastClick: () -> Unit,
    onPrivateClick: () -> Unit,
    onMeshStatusClick: () -> Unit,
    deviceId: String,
    peerCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AEGIS",
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = RedPrimary,
            letterSpacing = 2.sp
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFE8F5E9),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = "OFFLINE MESH ACTIVE",
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Text(
            text = "$deviceId • $peerCount peers linked",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = NeutralGray,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp)
        )

        // SOS Button
        Button(
            onClick = onSosClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "SOS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Broadcast Button
        Button(
            onClick = onBroadcastClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1B1F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.CellTower, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Broadcast Message",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Private Message Button
        Button(
            onClick = onPrivateClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1B1F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Message, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Private Message",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mesh Status Button
        Button(
            onClick = onMeshStatusClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeutralGray),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Mesh Status",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
