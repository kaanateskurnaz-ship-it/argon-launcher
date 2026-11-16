package com.argon.launcher.mods

enum class ModSource { MODRINTH, CURSEFORGE }

data class ModSummary(
    val id: String,
    val source: ModSource,
    val title: String,
    val description: String,
    val iconUrl: String?,
    val isModpack: Boolean
)

data class ModDependencyRef(
    val modId: String,
    /** Only known for Modrinth, where the API tells us exactly which version. Null = "pick the best one ourselves". */
    val versionId: String?,
    val required: Boolean
)

data class ModVersion(
    val versionId: String,
    val versionName: String,
    val mcVersions: List<String>,
    val downloadUrl: String,
    val fileName: String,
    val sha1: String?,
    val dependencies: List<ModDependencyRef>
)

/** A mod/version resolver a client must provide so [DependencyResolver] can work with either source generically. */
interface ModClient {
    val source: ModSource
    suspend fun search(query: String, isModpack: Boolean, mcVersion: String?): List<ModSummary>
    suspend fun getVersions(modId: String): List<ModVersion>
    /** Best version of [modId] compatible with [mcVersion], used when resolving a dependency that doesn't pin an exact version. */
    suspend fun findBestVersion(modId: String, mcVersion: String?): ModVersion?
    suspend fun getVersionById(versionId: String): ModVersion?
}
