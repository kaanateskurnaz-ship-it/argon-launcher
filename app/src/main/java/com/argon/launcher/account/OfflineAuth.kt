package com.argon.launcher.account

import java.security.MessageDigest
import java.util.UUID

/**
 * Creates "offline mode" accounts: no server round trip, just a deterministic UUID derived
 * from the username, the same way the vanilla Minecraft client does when it has no Microsoft
 * session. This lets two offline sessions with the same name share one UUID (useful for
 * keeping the same skin/playerdata across reinstalls), while different names never collide.
 */
object OfflineAuth {

    fun createAccount(username: String): Account {
        require(username.isNotBlank()) { "Username can't be empty" }
        return Account(
            id = "offline:$username",
            type = AccountType.OFFLINE,
            username = username,
            uuid = offlineUuidFor(username)
        )
    }

    /**
     * Offline UUID = a version-3 (name-based, MD5) UUID of the string "OfflinePlayer:<name>",
     * with the variant/version bits patched in afterwards. This matches what vanilla and every
     * other Minecraft launcher computes for offline play, so skins/data keyed by this UUID stay
     * consistent across launchers.
     */
    fun offlineUuidFor(username: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))

        // Patch in the "version 3, variant 2" bits per RFC 4122.
        digest[6] = (digest[6].toInt() and 0x0f or 0x30).toByte()
        digest[8] = (digest[8].toInt() and 0x3f or 0x80).toByte()

        var mostSigBits = 0L
        for (i in 0 until 8) mostSigBits = (mostSigBits shl 8) or (digest[i].toLong() and 0xff)
        var leastSigBits = 0L
        for (i in 8 until 16) leastSigBits = (leastSigBits shl 8) or (digest[i].toLong() and 0xff)

        return UUID(mostSigBits, leastSigBits).toString()
    }
}
