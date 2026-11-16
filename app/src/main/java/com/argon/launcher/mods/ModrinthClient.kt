package com.argon.launcher.mods

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Thin wrapper around the public Modrinth API (https://docs.modrinth.com/api/), no API key needed. */
class ModrinthClient(private val httpClient: OkHttpClient = OkHttpClient()) : ModClient {

    override val source = ModSource.MODRINTH

    companion object {
        private const val BASE_URL = "https://api.modrinth.com/v2"
    }

    override suspend fun search(query: String, isModpack: Boolean, mcVersion: String?): List<ModSummary> {
        val facets = mutableListOf("[\"project_type:${if (isModpack) "modpack" else "mod"}\"]")
        if (!mcVersion.isNullOrBlank()) facets.add("[\"versions:$mcVersion\"]")

        val url = "$BASE_URL/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("facets", "[${facets.joinToString(",")}]")
            .addQueryParameter("limit", "30")
            .build()

        val response = getJson(url.toString()).asJsonObject
        val hits = response.getAsJsonArray("hits") ?: JsonArray()
        return hits.map { hit ->
            val obj = hit.asJsonObject
            ModSummary(
                id = obj["project_id"].asString,
                source = ModSource.MODRINTH,
                title = obj["title"].asString,
                description = obj["description"]?.asString ?: "",
                iconUrl = obj["icon_url"]?.takeIf { !it.isJsonNull }?.asString,
                isModpack = isModpack
            )
        }
    }

    override suspend fun getVersions(modId: String): List<ModVersion> {
        val versions = getJson("$BASE_URL/project/$modId/version").asJsonArray
        return versions.map { parseVersion(it.asJsonObject) }
    }

    override suspend fun findBestVersion(modId: String, mcVersion: String?): ModVersion? {
        val urlBuilder = "$BASE_URL/project/$modId/version".toHttpUrl().newBuilder()
        if (!mcVersion.isNullOrBlank()) urlBuilder.addQueryParameter("game_versions", "[\"$mcVersion\"]")
        val versions = getJson(urlBuilder.build().toString()).asJsonArray
        if (versions.isEmpty) return null
        return parseVersion(versions[0].asJsonObject) // Modrinth returns newest-first
    }

    override suspend fun getVersionById(versionId: String): ModVersion? {
        return try {
            parseVersion(getJson("$BASE_URL/version/$versionId").asJsonObject)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVersion(version: JsonObject): ModVersion {
        val file = version.getAsJsonArray("files").firstOrNull { it.asJsonObject["primary"]?.asBoolean == true }
            ?.asJsonObject ?: version.getAsJsonArray("files")[0].asJsonObject
        val hashes = file.getAsJsonObject("hashes")

        val dependencies = (version.getAsJsonArray("dependencies") ?: JsonArray())
            .mapNotNull { element ->
                val dep = element.asJsonObject
                val type = dep["dependency_type"]?.asString
                val projectId = dep["project_id"]?.takeIf { !it.isJsonNull }?.asString
                if (type == null || projectId == null) return@mapNotNull null
                ModDependencyRef(
                    modId = projectId,
                    versionId = dep["version_id"]?.takeIf { !it.isJsonNull }?.asString,
                    required = type == "required"
                )
            }

        return ModVersion(
            versionId = version["id"].asString,
            versionName = version["name"].asString,
            mcVersions = version.getAsJsonArray("game_versions").map { it.asString },
            downloadUrl = file["url"].asString,
            fileName = file["filename"]?.asString ?: file["url"].asString.substringAfterLast('/'),
            sha1 = hashes?.get("sha1")?.takeIf { !it.isJsonNull }?.asString,
            dependencies = dependencies
        )
    }

    private suspend fun getJson(url: String): JsonElement = withContext(Dispatchers.IO) {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val text = response.body?.string() ?: "{}"
            JsonParser.parseString(text)
        }
    }
}
