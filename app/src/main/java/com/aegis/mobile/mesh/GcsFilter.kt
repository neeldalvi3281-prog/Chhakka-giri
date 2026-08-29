package com.aegis.mobile.mesh

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

/**
 * Golomb-Coded Set filter for peer-to-peer gossip delta synchronization.
 * Efficiently computes set difference to synchronize only missing messages across nodes.
 */
class GcsFilter(
    private val targetFpr: Double = 0.01,
    private val maxBytes: Int = 400
) {
    private val p: Int = ceil(ln(1.0 / targetFpr) / ln(2.0)).toInt()
    private val m: Int = floor((maxBytes * 8.0) / p).toInt()
    private val items: MutableList<Long> = mutableListOf()
    private var sorted: Boolean = false

    fun add(packetId: String) {
        val hash = hashToLong(packetId)
        items.add(hash)
        sorted = false
    }

    fun contains(packetId: String): Boolean {
        val hash = hashToLong(packetId)
        if (!sorted) {
            items.sort()
            sorted = true
        }
        return items.binarySearch(hash) >= 0
    }

    fun containsHash(hash: Long): Boolean {
        if (!sorted) {
            items.sort()
            sorted = true
        }
        return items.binarySearch(hash) >= 0
    }

    fun getDelta(remoteFilter: GcsFilter): List<Long> {
        if (!sorted) {
            items.sort()
            sorted = true
        }
        return items.filter { !remoteFilter.containsHash(it) }
    }

    fun toHex(): String {
        if (!sorted) {
            items.sort()
            sorted = true
        }
        val sb = StringBuilder()
        for (item in items) {
            sb.append(item.toString(16).padStart(16, '0'))
        }
        return sb.toString()
    }

    companion object {
        fun hashToLong(str: String): Long {
            var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325L FNV offset
            val prime = 0x100000001b3L

            for (ch in str) {
                hash = hash xor ch.code.toLong()
                hash *= prime
            }
            return hash and 0x7fffffffffffffffL // positive 63-bit long
        }

        fun fromHex(hex: String, targetFpr: Double = 0.01, maxBytes: Int = 400): GcsFilter {
            val filter = GcsFilter(targetFpr, maxBytes)
            var i = 0
            while (i + 16 <= hex.length) {
                val chunk = hex.substring(i, i + 16)
                val hash = chunk.toLong(16)
                filter.items.add(hash)
                i += 16
            }
            filter.sorted = true
            return filter
        }
    }
}
