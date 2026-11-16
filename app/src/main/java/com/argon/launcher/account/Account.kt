package com.argon.launcher.account

/** How an account authenticates. */
enum class AccountType {
    OFFLINE,
    MICROSOFT,
    ELY_BY
}

/**
 * A single player profile the launcher can play as.
 *
 * [accessToken] is whatever bearer token the game process needs to pass along (empty for
 * offline accounts). [refreshToken] is only meaningful for Microsoft/Ely.by, and is used to
 * silently renew [accessToken] without asking the player to sign in again.
 */
data class Account(
    val id: String,
    val type: AccountType,
    val username: String,
    val uuid: String,
    val accessToken: String = "",
    val refreshToken: String = "",
    /** Epoch millis after which [accessToken] should be refreshed before use. */
    val expiresAtMillis: Long = 0L
)
