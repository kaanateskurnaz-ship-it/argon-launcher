package com.argon.launcher.mods

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Wrapper around the CurseForge v1 API (https://docs.curseforge.com/rest-api/). Unlike Modrinth,
 * CurseForge requires every app to bring its own API key (free, via console.curseforge.com) -
 * pass yours in when constructing this client.
 */
class CurseForgeClient(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) : ModClient {

    override val source = ModSource.CURSEFORGE

    companion object {
        private const val BASE_URL = "https://api.curseforge.com/v1"
        private const val MINECRAFT_GAME_ID = 432
        private const val CLASS_ID_MOD = 6
        private const val CLASS_ID_MODPACK = 4471
        private const val RELATION_REQUIRED_DEPENDENCY = 3
    }

    override suspend fun search(query: String, isModpack: Boolean, mcVersion: String?): List<ModSummary> {
        val urlBuilder = "$BASE_URL/mods/search".toHttpUrl().newBuilder()
            .addQueryParameter("gameId", MINECRAFT_GAME_ID.toString())
            .addQueryParameter("classId", (if (isModpack) CLASS_ID_MODPACK else CLASS_ID_MOD).toString())
            .addQueryParameter("searchFilter", query)
            .addQueryParameter("pageSize", "30")
        if (!mcVersion.isNullOrBlank()) urlBuilder.addQueryParameter("gameVersion", mcVersion)

        val response = getJson(urlBuilder.build().toString()).asJsonObject
        val data = response.getAsJsonArray("data") ?: JsonArray()
        return data.map { element ->
            val obj = element.asJsonObject
            ModSummary(
                id = obj["id"].asString,
                source = ModSource.CURSEFORGE,
                title = obj["name"].asString,
                description = obj["summary"]?.asString ?: "",
                iconUrl = obj.getAsJsonObject("logo")?.get("url")?.asString,
                isModpack = isModpack
            )
        }
    }

    override suspend fun getVersions(modId: String): List<ModVersion> {
        val response = getJson("$BASE_URL/mods/$modId/files?pageSize=50").asJsonObject
        val data = response.getAsJsonArray("data") ?: JsonArray()
        return data.map { parseFile(it.asJsonObject) }
    }

    override suspend fun findBestVersion(modId: String, mcVersion: String?): ModVersion? {
        val urlBuilder = "$BASE_URL/mods/$modId/files".toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", "1")
        if (!mcVersion.isNullOrBlank()) urlBuilder.addQueryParameter("gameVersion", mcVersion)
        val response = getJson(urlBuilder.build().toString()).asJsonObject
        val data = response.getAsJsonArray("data") ?: JsonArray()
        if (data.isEmpty) return null
        return parseFile(data[0].asJsonObject)
    }

    override suspend fun getVersionById(versionId: String): ModVersion? {
        // CurseForge files are scoped by mod id; without it we can't look up a bare file id here.
        // Required dependencies are resolved through findBestVersion(modId, mcVersion) instead,
        // since CurseForge's dependency entries only ever give us a mod id, never a file id.
        return null
    }

    private fun parseFile(file: JsonObject): ModVersion {
        val dependencies = (file.getAsJsonArray("dependencies") ?: JsonArray())
            .mapNotNull { element ->
                val dep = element.asJsonObject
                val relationType = dep["relationType"]?.asInt ?: return@mapNotNull null
                ModDependencyRef(
                    modId = dep["modId"].asString,
                    versionId = null,
                    required = relationType == RELATION_REQUIRED_DEPENDENCY
                )
            }
            .filter { it.required }

        val sha1 = (file.getAsJsonArray("hashes") ?: JsonArray())
            .map { it.asJsonObject }
            .firstOrNull { it["algo"]?.asInt == 1 } // 1 == sha1 per CurseForge's HashAlgo enum
            ?.get("value")?.asString

        return ModVersion(
            versionId = file["id"].asString,
            versionName = file["displayName"].asString,
            mcVersions = (file.getAsJsonArray("gameVersions") ?: JsonArray()).map { it.asString },
            downloadUrl = file["downloadUrl"]?.asString
                ?: fallbackCdnUrl(file["id"].asLong, file["fileName"].asString),
            fileName = file["fileName"].asString,
            sha1 = sha1,
            dependencies = dependencies
        )
    }

    /** Some CurseForge mods disable the official download-url API field; this mirrors their CDN layout. */
    private fun fallbackCdnUrl(fileId: Long, fileName: String): String {
        return "https://edge.forgecdn.net/files/${fileId / 1000}/${fileId % 1000}/$fileName"
    }

    private suspend fun getJson(url: String) = withContext(Dispatchers.IO) {
        httpClient.newCall(
            Request.Builder().url(url).header("x-api-key", apiKey).build()
        ).execute().use { response ->
            val text = response.body?.string() ?: "{}"
            JsonParser.parseString(text)
        }
    }
}
