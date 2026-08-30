package com.aegis.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegis.mobile.mesh.GeohashUtils
import com.aegis.mobile.ui.theme.PhosphorCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    onSelectGeohash: (String) -> Unit
) {
    var lat by remember { mutableDoubleStateOf(37.7749) }
    var lng by remember { mutableDoubleStateOf(-122.4194) }
    val currentGeohash = GeohashUtils.encodeGeohash(lat, lng, 6)
    
    // UI state for panning
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tactical Geohash Map", fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PhosphorCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0C11)
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF0A0C11),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232B3B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TARGET SECTOR:", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(currentGeohash.uppercase(), color = PhosphorCyan, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        Text("${String.format("%.4f", lat)}°N, ${String.format("%.4f", lng)}°W", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { onSelectGeohash(currentGeohash) },
                        colors = ButtonDefaults.buttonColors(containerColor = PhosphorCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("LOCK SECTOR", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        containerColor = Color(0xFF05060A)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF05060A))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panOffset += dragAmount
                        // Map 1 pixel to roughly 0.0001 degrees for simulated panning
                        lng -= dragAmount.x * 0.0001
                        lat += dragAmount.y * 0.0001
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                // Draw Radar Grid
                val gridSpacing = 50f
                for (i in 0..(canvasWidth / gridSpacing).toInt()) {
                    val x = (i * gridSpacing + panOffset.x % gridSpacing)
                    drawLine(
                        color = PhosphorCyan.copy(alpha = 0.15f),
                        start = Offset(x, 0f),
                        end = Offset(x, canvasHeight),
                        strokeWidth = 1f
                    )
                }
                for (i in 0..(canvasHeight / gridSpacing).toInt()) {
                    val y = (i * gridSpacing + panOffset.y % gridSpacing)
                    drawLine(
                        color = PhosphorCyan.copy(alpha = 0.15f),
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1f
                    )
                }
                
                // Draw Sector Rectangle (simulated bounding box in center)
                val rectSize = 150f
                val rectTopLeft = Offset(canvasWidth / 2 - rectSize / 2, canvasHeight / 2 - rectSize / 2)
                drawRect(
                    color = PhosphorCyan.copy(alpha = 0.1f),
                    topLeft = rectTopLeft,
                    size = Size(rectSize, rectSize)
                )
                drawRect(
                    color = PhosphorCyan,
                    topLeft = rectTopLeft,
                    size = Size(rectSize, rectSize),
                    style = Stroke(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )
                
                // Draw Crosshairs
                drawLine(
                    color = Color(0xFFFF3D71).copy(alpha = 0.7f),
                    start = Offset(canvasWidth / 2, canvasHeight / 2 - 20f),
                    end = Offset(canvasWidth / 2, canvasHeight / 2 + 20f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color(0xFFFF3D71).copy(alpha = 0.7f),
                    start = Offset(canvasWidth / 2 - 20f, canvasHeight / 2),
                    end = Offset(canvasWidth / 2 + 20f, canvasHeight / 2),
                    strokeWidth = 2f
                )
                
                drawCircle(
                    color = Color(0xFFFF3D71).copy(alpha = 0.3f),
                    radius = 30f,
                    center = Offset(canvasWidth / 2, canvasHeight / 2)
                )
            }
        }
    }
}
