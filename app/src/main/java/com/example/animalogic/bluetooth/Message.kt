package com.example.animalogic.bluetooth

import com.example.animalogic.game.PuzzleFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bumped whenever the byte layout below changes. Both phones must match. */
const val PROTOCOL_VERSION = 1

/**
 * Default ATT MTU is 23 bytes, minus 3 bytes of header = 20 usable bytes per packet.
 * Keeping every message under this means no MTU negotiation is needed.
 * Source: https://punchthrough.com/maximizing-ble-throughput-part-2-use-larger-att-mtu/
 */
const val MAX_PACKET = 20

/**
 * Everything the two phones say to each other. Each message is one packet (<= 20 bytes):
 * byte 0 is the type, the rest are the fields. Multi-byte numbers are little-endian.
 *
 * Match flow:  Hello <-> Hello,  host: Start,  guest: Ready,  host: Go,  both: Finished
 */
sealed class Message {

    /** First message each way. Carries the app protocol version and the player's username. */
    data class Hello(val version: Int, val username: String) : Message()

    /** Host -> guest. Both devices build the board from [seed]; [checksum] proves they got the same one. */
    data class Start(val seed: Long, val level: Int, val checksum: Int) : Message()

    /** Guest -> host. [ok] is false if the guest's board did not match the checksum. */
    data class Ready(val ok: Boolean) : Message()

    /** Host -> guest. Both players start their timers when they get/send this. */
    object Go : Message()

    /** Either direction. Time from Go to the last tile, measured on the sender's own clock. */
    data class Finished(val elapsedMs: Long) : Message()

    fun encode(): ByteArray = when (this) {
        is Hello -> {
            val name = username.toByteArray(Charsets.UTF_8).let { it.copyOf(minOf(it.size, MAX_PACKET - 2)) }
            ByteBuffer.allocate(2 + name.size).order(ByteOrder.LITTLE_ENDIAN)
                .put(HELLO).put(version.toByte()).put(name).array()
        }
        is Start -> ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
            .put(START).putLong(seed).put(level.toByte()).putInt(checksum).array()
        is Ready -> byteArrayOf(READY, if (ok) 1 else 0)
        Go -> byteArrayOf(GO)
        is Finished -> ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .put(FINISHED).putInt(elapsedMs.coerceIn(0L, 0xFFFF_FFFFL).toInt()).array()
    }

    companion object {
        private const val HELLO: Byte = 1
        private const val START: Byte = 2
        private const val READY: Byte = 3
        private const val GO: Byte = 4
        private const val FINISHED: Byte = 5

        /** Returns null for anything malformed, so a bad packet is dropped instead of crashing the match. */
        fun decode(bytes: ByteArray): Message? {
            if (bytes.isEmpty() || bytes.size > MAX_PACKET) return null
            val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return when (data.get()) {
                HELLO -> {
                    if (bytes.size < 2) return null
                    val version = data.get().toInt() and 0xFF
                    val name = String(bytes, 2, bytes.size - 2, Charsets.UTF_8)
                    Hello(version, name)
                }
                START -> {
                    if (bytes.size != 14) return null
                    val seed = data.long
                    val level = data.get().toInt()
                    val checksum = data.int
                    if (level !in PuzzleFactory.MIN_LEVEL..PuzzleFactory.MAX_LEVEL) return null
                    Start(seed, level, checksum)
                }
                READY -> if (bytes.size == 2 && bytes[1] in 0..1) Ready(bytes[1] == 1.toByte()) else null
                GO -> if (bytes.size == 1) Go else null
                FINISHED -> if (bytes.size == 5) Finished(data.int.toLong() and 0xFFFF_FFFFL) else null
                else -> null
            }
        }
    }
}
