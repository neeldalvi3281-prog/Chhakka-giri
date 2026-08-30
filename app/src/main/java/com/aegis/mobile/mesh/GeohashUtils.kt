package com.aegis.mobile.mesh

import kotlin.math.*

data class GeohashBounds(
    val latMin: Double,
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double
) {
    val centerLat: Double get() = (latMin + latMax) / 2.0
    val centerLng: Double get() = (lonMin + lonMax) / 2.0
    val widthKm: Double get() = calculateDistanceKm(centerLat, lonMin, centerLat, lonMax)
    val heightKm: Double get() = calculateDistanceKm(latMin, centerLng, latMax, centerLng)
}

data class TacticalSectorCell(
    val geohash: String,
    val bounds: GeohashBounds,
    val rowOffset: Int,
    val colOffset: Int,
    val label: String
)

object GeohashUtils {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private val BITS = intArrayOf(16, 8, 4, 2, 1)

    fun encodeGeohash(latitude: Double, longitude: Double, precision: Int = 6): String {
        var isEven = true
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0
        var bit = 0
        var ch = 0
        val geohash = StringBuilder()

        val clampedLat = latitude.coerceIn(-90.0, 90.0)
        val clampedLng = longitude.coerceIn(-180.0, 180.0)

        while (geohash.length < precision) {
            if (isEven) {
                val mid = (lonMin + lonMax) / 2.0
                if (clampedLng >= mid) {
                    ch = ch or BITS[bit]
                    lonMin = mid
                } else {
                    lonMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2.0
                if (clampedLat >= mid) {
                    ch = ch or BITS[bit]
                    latMin = mid
                } else {
                    latMax = mid
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                geohash.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return geohash.toString()
    }

    fun decodeBounds(geohash: String): GeohashBounds {
        val clean = geohash.trim().lowercase().removePrefix("#")
        var isEven = true
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0

        for (c in clean) {
            val cd = BASE32.indexOf(c)
            if (cd == -1) continue

            for (j in 0..4) {
                val mask = BITS[j]
                if (isEven) {
                    val lonMid = (lonMin + lonMax) / 2.0
                    if ((cd and mask) != 0) {
                        lonMin = lonMid
                    } else {
                        lonMax = lonMid
                    }
                } else {
                    val latMid = (latMin + latMax) / 2.0
                    if ((cd and mask) != 0) {
                        latMin = latMid
                    } else {
                        latMax = latMid
                    }
                }
                isEven = !isEven
            }
        }
        return GeohashBounds(latMin, latMax, lonMin, lonMax)
    }

    fun decodeCoordinates(geohash: String): Pair<Double, Double> {
        val bounds = decodeBounds(geohash)
        return Pair(bounds.centerLat, bounds.centerLng)
    }

    fun generateNeighborGrid(centerLat: Double, centerLng: Double, precision: Int = 6): List<TacticalSectorCell> {
        val centerHash = encodeGeohash(centerLat, centerLng, precision)
        val centerBounds = decodeBounds(centerHash)
        val latDelta = (centerBounds.latMax - centerBounds.latMin)
        val lonDelta = (centerBounds.lonMax - centerBounds.lonMin)

        val cells = mutableListOf<TacticalSectorCell>()

        // Generate 3x3 surrounding grid
        for (r in -1..1) {
            for (c in -1..1) {
                val cellLat = centerBounds.centerLat + (r * latDelta)
                val cellLng = centerBounds.centerLng + (c * lonDelta)
                val hash = encodeGeohash(cellLat, cellLng, precision)
                val bounds = decodeBounds(hash)
                
                val label = when {
                    r == 0 && c == 0 -> "CENTER [LOCK]"
                    r == 1 && c == 0 -> "NORTH"
                    r == -1 && c == 0 -> "SOUTH"
                    r == 0 && c == 1 -> "EAST"
                    r == 0 && c == -1 -> "WEST"
                    r == 1 && c == 1 -> "NORTH-EAST"
                    r == 1 && c == -1 -> "NORTH-WEST"
                    r == -1 && c == 1 -> "SOUTH-EAST"
                    r == -1 && c == -1 -> "SOUTH-WEST"
                    else -> "SECTOR"
                }

                cells.add(
                    TacticalSectorCell(
                        geohash = hash,
                        bounds = bounds,
                        rowOffset = r,
                        colOffset = c,
                        label = label
                    )
                )
            }
        }
        return cells
    }

    fun getPrecisionDescriptor(precision: Int): String {
        return when (precision) {
            4 -> "Regional (~39km × 20km)"
            5 -> "District (~4.9km × 4.9km)"
            6 -> "Tactical Zone (~1.2km × 0.6km)"
            7 -> "Local Grid (~152m × 152m)"
            8 -> "Micro Tactical (~38m × 19m)"
            else -> "Sector (~1.2km)"
        }
    }
}

fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return 6371.0 * c
}

