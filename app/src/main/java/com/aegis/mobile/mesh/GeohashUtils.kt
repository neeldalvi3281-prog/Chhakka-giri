package com.aegis.mobile.mesh

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

        while (geohash.length < precision) {
            if (isEven) {
                val mid = (lonMin + lonMax) / 2
                if (longitude >= mid) {
                    ch = ch or BITS[bit]
                    lonMin = mid
                } else {
                    lonMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2
                if (latitude >= mid) {
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
}
