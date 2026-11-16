package com.argon.launcher.account

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Shown to the player while we wait for them to approve the sign-in in a browser. */
data class DeviceCodePrompt(
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val pollIntervalSeconds: Int
)

class MicrosoftAuthException(message: String) : Exception(message)

/**
 * Minecraft's Microsoft sign-in chain: Microsoft identity platform (device code flow) ->
 * Xbox Live -> XSTS -> Minecraft Services. This is the same publicly documented flow every
 * third-party launcher implements (see Microsoft's and Mojang's own published auth docs);
 * nothing here talks to, or reuses code from, any other launcher project.
 */
class MicrosoftAuth(private val httpClient: OkHttpClient = OkHttpClient()) {

    companion object {
        // You need your own Azure AD application registration (free, via portal.azure.com)
        // with the "public client / native" platform enabled and the
        // "XboxLive.signin offline_access" scope allowed. Put its Application (client) ID here.
        private const val CLIENT_ID = "00000000-0000-0000-0000-000000000000"

        private const val DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
        private const val TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
        private const val XBOX_LIVE_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate"
        private const val XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
        private const val MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
        private const val MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    /** Starts the device-code flow and returns what to show the player (code + URL to open). */
    suspend fun startDeviceCodeFlow(): Pair<DeviceCodePrompt, String> {
        val body = "client_id=$CLIENT_ID&scope=XboxLive.signin%20offline_access"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val response = execute(Request.Builder().url(DEVICE_CODE_URL).post(body).build())
        val prompt = DeviceCodePrompt(
            userCode = response["user_code"].asString,
            verificationUri = response["verification_uri"].asString,
            expiresInSeconds = response["expires_in"].asInt,
            pollIntervalSeconds = response["interval"].asInt
        )
        return prompt to response["device_code"].asString
    }

    /**
     * Polls until the player finishes signing in in their browser, then runs the full
     * Xbox Live -> XSTS -> Minecraft Services chain and returns the resulting [Account].
     */
    suspend fun pollForAccount(prompt: DeviceCodePrompt, deviceCode: String): Account {
        val deadline = System.currentTimeMillis() + prompt.expiresInSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(prompt.pollIntervalSeconds * 1000L)

            val body = ("client_id=$CLIENT_ID" +
                    "&grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                    "&device_code=$deviceCode")
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val raw = executeAllowingErrors(Request.Builder().url(TOKEN_URL).post(body).build())
            val error = raw["error"]?.asString
            when {
                error == "authorization_pending" -> continue
                error == "authorization_declined" -> throw MicrosoftAuthException("Sign-in was declined")
                error == "expired_token" -> throw MicrosoftAuthException("The sign-in code expired, start again")
                error != null -> throw MicrosoftAuthException("Microsoft sign-in failed: $error")
                else -> return finishLogin(raw["access_token"].asString, raw["refresh_token"].asString)
            }
        }
        throw MicrosoftAuthException("The sign-in code expired, start again")
    }

    /** Uses a previously stored refresh token to get a new Minecraft session without any UI. */
    suspend fun refresh(account: Account): Account {
        val body = ("client_id=$CLIENT_ID" +
                "&grant_type=refresh_token" +
                "&refresh_token=${account.refreshToken}" +
                "&scope=XboxLive.signin%20offline_access")
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val raw = execute(Request.Builder().url(TOKEN_URL).post(body).build())
        return finishLogin(raw["access_token"].asString, raw["refresh_token"].asString)
    }

    private suspend fun finishLogin(msAccessToken: String, msRefreshToken: String): Account {
        // 1) Xbox Live
        val xblRequestJson = """
            {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$msAccessToken"},
             "RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}
        """.trimIndent()
        val xbl = execute(Request.Builder().url(XBOX_LIVE_AUTH_URL).post(xblRequestJson.toRequestBody(JSON)).build())
        val xblToken = xbl["Token"].asString
        val userHash = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")[0].asJsonObject["uhs"].asString

        // 2) XSTS
        val xstsRequestJson = """
            {"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},
             "RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}
        """.trimIndent()
        val xsts = executeAllowingErrors(Request.Builder().url(XSTS_AUTH_URL).post(xstsRequestJson.toRequestBody(JSON)).build())
        if (xsts.has("XErr")) {
            throw MicrosoftAuthException(describeXstsError(xsts["XErr"].asLong))
        }
        val xstsToken = xsts["Token"].asString

        // 3) Minecraft Services
        val mcLoginJson = """{"identityToken":"XBL3.0 x=$userHash;$xstsToken"}"""
        val mcLogin = execute(Request.Builder().url(MC_LOGIN_URL).post(mcLoginJson.toRequestBody(JSON)).build())
        val mcAccessToken = mcLogin["access_token"].asString
        val expiresInSeconds = mcLogin["expires_in"].asLong

        // 4) Profile (username + UUID)
        val profile = execute(
            Request.Builder().url(MC_PROFILE_URL)
                .header("Authorization", "Bearer $mcAccessToken")
                .get().build()
        )

        return Account(
            id = "microsoft:${profile["id"].asString}",
            type = AccountType.MICROSOFT,
            username = profile["name"].asString,
            uuid = dashedUuid(profile["id"].asString),
            accessToken = mcAccessToken,
            refreshToken = msRefreshToken,
            expiresAtMillis = System.currentTimeMillis() + expiresInSeconds * 1000L
        )
    }

    private fun describeXstsError(code: Long): String = when (code) {
        2148916233L -> "This Microsoft account has no Xbox profile. Create one at xbox.com first."
        2148916235L -> "Xbox Live isn't available in this account's country."
        2148916236L, 2148916237L -> "This account needs adult verification (South Korea age rules)."
        2148916238L -> "This is a child account; it needs to be added to a Microsoft family first."
        else -> "Xbox Live rejected the sign-in (code $code)"
    }

    private fun dashedUuid(undashed: String): String {
        return "${undashed.substring(0, 8)}-${undashed.substring(8, 12)}-${undashed.substring(12, 16)}-" +
                "${undashed.substring(16, 20)}-${undashed.substring(20, 32)}"
    }

    private suspend fun execute(request: Request): JsonObject {
        val json = executeAllowingErrors(request)
        if (json.has("error")) throw MicrosoftAuthException(json["error"].asString)
        return json
    }

    private suspend fun executeAllowingErrors(request: Request): JsonObject {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: throw IOException("Empty response from ${request.url}")
                JsonParser.parseString(text).asJsonObject
            }
        }
    }
}
