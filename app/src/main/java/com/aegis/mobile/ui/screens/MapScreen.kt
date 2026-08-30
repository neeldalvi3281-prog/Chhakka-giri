package com.aegis.mobile.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aegis.mobile.data.MessageRepository
import com.aegis.mobile.data.MessageType
import com.aegis.mobile.mesh.GeohashUtils
import com.aegis.mobile.mesh.TacticalSectorCell
import com.aegis.mobile.ui.theme.PhosphorCyan
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    repository: MessageRepository,
    onBack: () -> Unit,
    onSelectGeohash: (String) -> Unit
) {
    val context = LocalContext.current
    val userProfile by repository.userProfile.collectAsState()
    val peers by repository.peers.collectAsState()
    val messages by repository.messages.collectAsState()

    // Location State
    var gpsLat by remember { mutableDoubleStateOf(37.7749) }
    var gpsLng by remember { mutableDoubleStateOf(-122.4194) }
    var gpsAltitude by remember { mutableDoubleStateOf(16.0) }
    var gpsAccuracy by remember { mutableFloatStateOf(8.0f) }
    var isGpsActive by remember { mutableStateOf(false) }
    var lastFixTime by remember { mutableStateOf("ACQUIRING...") }

    // Map Navigation State
    var mapCenterLat by remember { mutableDoubleStateOf(37.7749) }
    var mapCenterLng by remember { mutableDoubleStateOf(-122.4194) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var precision by remember { mutableIntStateOf(6) }
    var selectedSector by remember { mutableStateOf<String?>(null) }
    var isSearchDialogOpen by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }

    // Current encoded geohash for map center
    val currentGeohash = remember(mapCenterLat, mapCenterLng, precision) {
        "#" + GeohashUtils.encodeGeohash(mapCenterLat, mapCenterLng, precision)
    }

    // Surrounding neighbor cells
    val neighborCells = remember(mapCenterLat, mapCenterLng, precision) {
        GeohashUtils.generateNeighborGrid(mapCenterLat, mapCenterLng, precision)
    }

    // Active SOS Beacons from messages
    val activeSosList = remember(messages) {
        messages.filter { it.type == MessageType.ALERT_CRITICAL }
    }

    // Initialize & Register GPS Listener
    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val locationListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                gpsLat = loc.latitude
                gpsLng = loc.longitude
                gpsAltitude = loc.altitude
                gpsAccuracy = if (loc.hasAccuracy()) loc.accuracy else 10f
                isGpsActive = true
                lastFixTime = "GPS LIVE FIX"
                
                // If map is at default SF or uninitialized, align with real GPS
                if (mapCenterLat == 37.7749 && mapCenterLng == -122.4194) {
                    mapCenterLat = loc.latitude
                    mapCenterLng = loc.longitude
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) { isGpsActive = true }
            override fun onProviderDisabled(provider: String) { isGpsActive = false }
        }

        if (locationManager != null && (hasFine || hasCoarse)) {
            try {
                // Get last known location for instant display
                val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

                lastGps?.let {
                    gpsLat = it.latitude
                    gpsLng = it.longitude
                    gpsAltitude = it.altitude
                    gpsAccuracy = if (it.hasAccuracy()) it.accuracy else 12f
                    isGpsActive = true
                    lastFixTime = "LAST CACHED FIX"
                    mapCenterLat = it.latitude
                    mapCenterLng = it.longitude
                }

                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2.0f, locationListener)
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5.0f, locationListener)
                }
            } catch (e: SecurityException) {
                // Permission not granted
            } catch (e: Exception) {
                // Fallback
            }
        }

        onDispose {
            try {
                locationManager?.removeUpdates(locationListener)
            } catch (e: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "TACTICAL GEOHASH RADAR",
                                fontWeight = FontWeight.Black,
                                color = PhosphorCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isGpsActive) Color(0xFF22C55E).copy(alpha = 0.2f) else Color(0xFFFFB800).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isGpsActive) Color(0xFF22C55E).copy(alpha = 0.6f) else Color(0xFFFFB800).copy(alpha = 0.6f)
                                )
                            ) {
                                Text(
                                    text = if (isGpsActive) "GPS LOCKED (±${gpsAccuracy.roundToInt()}m)" else "SIMULATED FIX",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGpsActive) Color(0xFF22C55E) else Color(0xFFFFB800),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${String.format("%.4f", mapCenterLat)}°N, ${String.format("%.4f", mapCenterLng)}°W • Alt: ${gpsAltitude.roundToInt()}m",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PhosphorCyan)
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchDialogOpen = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search Geohash", tint = Color.White)
                    }
                    IconButton(onClick = {
                        // Re-center on real device GPS
                        mapCenterLat = gpsLat
                        mapCenterLng = gpsLng
                        zoomScale = 1.0f
                        selectedSector = null
                    }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Center GPS", tint = if (isGpsActive) Color(0xFF22C55E) else PhosphorCyan)
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
                Column(modifier = Modifier.padding(12.dp)) {
                    // Precision chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PRECISION:", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        listOf(4, 5, 6, 7, 8).forEach { p ->
                            FilterChip(
                                selected = precision == p,
                                onClick = { precision = p },
                                label = { Text("P$p", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PhosphorCyan,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF141824),
                                    labelColor = Color.LightGray
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val activeSectorTarget = selectedSector ?: currentGeohash

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TARGET SECTOR FREQUENCY:", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            Text(
                                activeSectorTarget.uppercase(),
                                color = PhosphorCyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                GeohashUtils.getPrecisionDescriptor(precision),
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Button(
                            onClick = { onSelectGeohash(activeSectorTarget) },
                            colors = ButtonDefaults.buttonColors(containerColor = PhosphorCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("LOCK SECTOR", color = Color.Black, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
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
        ) {
            // Interactive Vector Map Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.5f, 3.5f)
                            // Map drag to lat/lng degrees
                            val deltaLat = (pan.y / (1000f * zoomScale)) * 0.015
                            val deltaLng = (pan.x / (1000f * zoomScale)) * 0.015
                            mapCenterLat += deltaLat
                            mapCenterLng -= deltaLng
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f

                // 1. Draw Radar Background Conic / Concentric Range Rings
                val ringRadii = listOf(80f, 160f, 240f, 320f, 400f).map { it * zoomScale }
                val ringLabels = listOf("100m", "500m", "1km", "2.5km", "5km")

                ringRadii.forEachIndexed { index, r ->
                    drawCircle(
                        color = PhosphorCyan.copy(alpha = 0.12f),
                        radius = r,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                    )
                }

                // 2. Crosshair Cardinal lines (N/S/E/W)
                drawLine(
                    color = PhosphorCyan.copy(alpha = 0.25f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, canvasHeight),
                    strokeWidth = 1f
                )
                drawLine(
                    color = PhosphorCyan.copy(alpha = 0.25f),
                    start = Offset(0f, centerY),
                    end = Offset(canvasWidth, centerY),
                    strokeWidth = 1f
                )

                // 3. Draw 3x3 Geohash Sector Matrix
                val cellSize = 120f * zoomScale
                neighborCells.forEach { cell ->
                    val cellCenterX = centerX + (cell.colOffset * cellSize)
                    val cellCenterY = centerY - (cell.rowOffset * cellSize)
                    val topLeft = Offset(cellCenterX - cellSize / 2f, cellCenterY - cellSize / 2f)

                    val isCenter = cell.rowOffset == 0 && cell.colOffset == 0
                    val isSelected = selectedSector == "#${cell.geohash}"

                    // Background fill
                    drawRect(
                        color = when {
                            isSelected -> PhosphorCyan.copy(alpha = 0.25f)
                            isCenter -> PhosphorCyan.copy(alpha = 0.12f)
                            else -> Color(0xFF0F172A).copy(alpha = 0.4f)
                        },
                        topLeft = topLeft,
                        size = Size(cellSize, cellSize)
                    )

                    // Border
                    drawRect(
                        color = when {
                            isSelected -> PhosphorCyan
                            isCenter -> PhosphorCyan.copy(alpha = 0.8f)
                            else -> PhosphorCyan.copy(alpha = 0.25f)
                        },
                        topLeft = topLeft,
                        size = Size(cellSize, cellSize),
                        style = Stroke(
                            width = if (isSelected || isCenter) 2f else 1f,
                            pathEffect = if (isCenter) null else PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )
                    )

                    // Sector text label via Android Native Paint
                    val paint = android.graphics.Paint().apply {
                        color = if (isSelected || isCenter) android.graphics.Color.CYAN else android.graphics.Color.LTGRAY
                        textSize = 24f * (zoomScale.coerceIn(0.7f, 1.3f))
                        typeface = android.graphics.Typeface.MONOSPACE
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }

                    val subPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 18f * (zoomScale.coerceIn(0.7f, 1.3f))
                        typeface = android.graphics.Typeface.MONOSPACE
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        "#${cell.geohash.uppercase()}",
                        cellCenterX,
                        cellCenterY - 6f,
                        paint
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        cell.label,
                        cellCenterX,
                        cellCenterY + 22f,
                        subPaint
                    )
                }

                // 4. Draw Peer Node Markers (if any connected)
                peers.forEachIndexed { i, peer ->
                    val angle = (i * (360f / peers.size.coerceAtLeast(1))) * (Math.PI / 180f)
                    val dist = (140f + (i * 20f)) * zoomScale
                    val px = centerX + (dist * kotlin.math.cos(angle)).toFloat()
                    val py = centerY + (dist * kotlin.math.sin(angle)).toFloat()

                    drawCircle(
                        color = Color(0xFF22C55E),
                        radius = 8f,
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = Color(0xFF22C55E).copy(alpha = 0.3f),
                        radius = 16f,
                        center = Offset(px, py)
                    )

                    val peerPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GREEN
                        textSize = 20f
                        typeface = android.graphics.Typeface.MONOSPACE
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        peer.callSign,
                        px,
                        py - 12f,
                        peerPaint
                    )
                }

                // 5. Draw SOS Distress Beacon Alert (if any active)
                if (activeSosList.isNotEmpty()) {
                    val sosX = centerX + 90f * zoomScale
                    val sosY = centerY - 110f * zoomScale

                    drawCircle(
                        color = Color(0xFFFF3D71),
                        radius = 10f,
                        center = Offset(sosX, sosY)
                    )
                    drawCircle(
                        color = Color(0xFFFF3D71).copy(alpha = 0.35f),
                        radius = 24f,
                        center = Offset(sosX, sosY)
                    )

                    val sosPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.RED
                        textSize = 22f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "🚨 SOS BEACON",
                        sosX,
                        sosY - 14f,
                        sosPaint
                    )
                }

                // 6. Draw Center User Reticle / GPS Lock
                drawCircle(
                    color = PhosphorCyan.copy(alpha = 0.4f),
                    radius = 20f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = PhosphorCyan,
                    radius = 5f,
                    center = Offset(centerX, centerY)
                )
            }

            // Top Quick Sector Matrix Selector HUD
            Surface(
                color = Color(0xFF090B10).copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2638)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.TopCenter)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "TAP ADJACENT 3×3 SECTORS TO INSPECT:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        neighborCells.forEach { cell ->
                            val isSelected = selectedSector == "#${cell.geohash}"
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) PhosphorCyan else Color(0xFF151926),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) PhosphorCyan else Color(0xFF2E384D)
                                ),
                                modifier = Modifier.clickable {
                                    selectedSector = "#${cell.geohash}"
                                }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "#${cell.geohash.uppercase()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) Color.Black else PhosphorCyan
                                    )
                                    Text(
                                        cell.label,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) Color.Black else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Floating Zoom and Re-center Controls (Right side)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(3.5f) },
                    containerColor = Color(0xFF141926),
                    contentColor = PhosphorCyan,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }
                FloatingActionButton(
                    onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.5f) },
                    containerColor = Color(0xFF141926),
                    contentColor = PhosphorCyan,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }
                FloatingActionButton(
                    onClick = {
                        mapCenterLat = gpsLat
                        mapCenterLng = gpsLng
                        zoomScale = 1.0f
                        selectedSector = null
                    },
                    containerColor = if (isGpsActive) Color(0xFF1E3A2F) else Color(0xFF141926),
                    contentColor = if (isGpsActive) Color(0xFF22C55E) else PhosphorCyan,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.GpsFixed, contentDescription = "Lock GPS")
                }
            }
        }
    }

    // Geohash Search & Custom Coordinate Dialog
    if (isSearchDialogOpen) {
        AlertDialog(
            onDismissRequest = { isSearchDialogOpen = false },
            containerColor = Color(0xFF0F131D),
            title = {
                Text("JUMP TO SECTOR / GEOHASH", fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PhosphorCyan)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter Geohash code (e.g. 9q8yy, dr5reg, u4pru) or pick a preset:", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        placeholder = { Text("e.g. 9q8yyk", fontSize = 12.sp, color = Color.DarkGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PhosphorCyan,
                            unfocusedBorderColor = Color(0xFF232D3F),
                            focusedContainerColor = Color(0xFF06080D),
                            unfocusedContainerColor = Color(0xFF06080D),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Text("TACTICAL PRESETS:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    val presets = listOf(
                        "Current GPS" to Pair(gpsLat, gpsLng),
                        "SF Ops Base (#9q8yy)" to Pair(37.7749, -122.4194),
                        "NYC Command (#dr5reg)" to Pair(40.7128, -74.0060),
                        "London Sector (#gcpvj0)" to Pair(51.5074, -0.1278),
                        "Tokyo Hub (#xn774c)" to Pair(35.6762, 139.6503)
                    )

                    presets.forEach { (name, coords) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    mapCenterLat = coords.first
                                    mapCenterLng = coords.second
                                    isSearchDialogOpen = false
                                },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF141926)
                        ) {
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = searchInput.trim().lowercase().removePrefix("#")
                        if (clean.isNotBlank()) {
                            val (targetLat, targetLng) = GeohashUtils.decodeCoordinates(clean)
                            mapCenterLat = targetLat
                            mapCenterLng = targetLng
                            selectedSector = "#$clean"
                        }
                        isSearchDialogOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhosphorCyan)
                ) {
                    Text("JUMP", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { isSearchDialogOpen = false }) {
                    Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}
