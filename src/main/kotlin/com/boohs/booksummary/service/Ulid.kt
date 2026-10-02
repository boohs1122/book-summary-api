package com.boohs.booksummary.service

import java.security.SecureRandom
import java.time.Instant

object Ulid {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val random = SecureRandom()

    fun generate(): String {
        var timestamp = Instant.now().toEpochMilli()
        val characters = CharArray(26)
        for (index in 9 downTo 0) {
            characters[index] = ALPHABET[(timestamp and 31).toInt()]
            timestamp = timestamp ushr 5
        }

        val randomness = ByteArray(10).also(random::nextBytes)
        var bits = 0
        var buffer = 0
        var position = 10
        for (byte in randomness) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                characters[position++] = ALPHABET[(buffer ushr bits) and 31]
            }
        }
        return String(characters)
    }
}
