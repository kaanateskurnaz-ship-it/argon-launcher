package com.argon.launcher.account

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ElyByAuthException(message: String) : Exception(message)

/**
 * Ely.by's own OAuth2 flow (https://docs.ely.by/en/oauth.html): the player approves the
 * sign-in in a browser, Ely.by redirects back to our app with a code, and we trade that code
 * for a token + profile. You need your own registered application at
 * https://ely.by/dev/applications/add to get a client id/secret and to register the redirect
 * URI used below.
 *
 * Note: this only gets us an Ely.by-authenticated [Account] (username/UUID/token). Actually
 * making the game *trust* that identity in place of Mojang's requires wiring up
 * authlib-injector against Ely.by's Yggdrasil-compatible API at launch time, which is part of
 * the game-launching engine this project intentionally does not include yet.
 */
class ElyByAuth(
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val AUTHORIZE_URL = "https://account.ely.by/oauth2/v1"
        private const val TOKEN_URL = "https://account.ely.by/api/oauth2/v1/token"
        private const val ACCOUNT_INFO_URL = "https://account.ely.by/api/account/v1/info"
    }

    /** Open this URL in a browser/custom tab; Ely.by will redirect back to [redirectUri]?code=... */
    fun buildAuthorizationUrl(): String {
        return "$AUTHORIZE_URL?client_id=$clientId" +
                "&redirect_uri=${java.net.URLEncoder.encode(redirectUri, "UTF-8")}" +
                "&response_type=code" +
                "&scope=${java.net.URLEncoder.encode("account_info minecraft_server_session", "UTF-8")}"
    }

    /** Call this once your redirect-handling Activity receives the "code" query parameter. */
    suspend fun exchangeCodeForAccount(code: String): Account {
        val tokenBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("redirect_uri", redirectUri)
            .add("code", code)
            .build()

        val tokenResponse = execute(Request.Builder().url(TOKEN_URL).post(tokenBody).build())
        val accessToken = tokenResponse["access_token"].asString
        val refreshToken = tokenResponse["refresh_token"]?.asString ?: ""
        val expiresIn = tokenResponse["expires_in"]?.asLong ?: 0L

        val profile = execute(
            Request.Builder().url(ACCOUNT_INFO_URL)
                .header("Authorization", "Bearer $accessToken")
                .get().build()
        )

        return Account(
            id = "elyby:${profile["uuid"].asString}",
            type = AccountType.ELY_BY,
            username = profile["username"].asString,
            uuid = profile["uuid"].asString,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L
        )
    }

    suspend fun refresh(account: Account): Account {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", account.refreshToken)
            .build()
        val tokenResponse = execute(Request.Builder().url(TOKEN_URL).post(body).build())
        val accessToken = tokenResponse["access_token"].asString
        val profile = execute(
            Request.Builder().url(ACCOUNT_INFO_URL)
                .header("Authorization", "Bearer $accessToken")
                .get().build()
        )
        return account.copy(
            accessToken = accessToken,
            refreshToken = tokenResponse["refresh_token"]?.asString ?: account.refreshToken,
            expiresAtMillis = System.currentTimeMillis() + (tokenResponse["expires_in"]?.asLong ?: 0L) * 1000L,
            username = profile["username"].asString
        )
    }

    private suspend fun execute(request: Request): JsonObject = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw IOException("Empty response from ${request.url}")
            val json = JsonParser.parseString(text).asJsonObject
            if (!response.isSuccessful) {
                val message = json["message"]?.asString ?: json["error"]?.asString ?: "HTTP ${response.code}"
                throw ElyByAuthException(message)
            }
            json
        }
    }
}
