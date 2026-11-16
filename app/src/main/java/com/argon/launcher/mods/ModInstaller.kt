package com.argon.launcher.mods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

sealed class InstallEvent {
    data class Downloading(val fileName: String) : InstallEvent()
    data class DependencyMissing(val modId: String) : InstallEvent()
    data class DependencyFailed(val modId: String, val reason: String) : InstallEvent()
    data object Done : InstallEvent()
}

/**
 * Downloads one mod into [modsDirectory], then walks its "required" dependency graph
 * (recursively, since a dependency can itself depend on something else) and downloads every
 * required dependency too, skipping anything already handled so a dependency shared by two
 * mods is only fetched once.
 *
 * A single broken/renamed dependency is reported through [onEvent] but doesn't abort the whole
 * installation - the mod you actually asked for still gets installed.
 */
class ModInstaller(private val httpClient: OkHttpClient = OkHttpClient()) {

    suspend fun installModWithDependencies(
        client: ModClient,
        rootModId: String,
        rootVersion: ModVersion,
        modsDirectory: File,
        onEvent: (InstallEvent) -> Unit = {}
    ) {
        modsDirectory.mkdirs()
        val handled = mutableSetOf(rootModId)
        val mcVersion = rootVersion.mcVersions.firstOrNull()

        onEvent(InstallEvent.Downloading(rootVersion.fileName))
        downloadFile(rootVersion.downloadUrl, rootVersion.sha1, File(modsDirectory, rootVersion.fileName))

        for (dependency in rootVersion.dependencies.filter { it.required }) {
            installDependency(client, dependency, mcVersion, modsDirectory, handled, onEvent)
        }
        onEvent(InstallEvent.Done)
    }

    private suspend fun installDependency(
        client: ModClient,
        dependency: ModDependencyRef,
        mcVersion: String?,
        modsDirectory: File,
        handled: MutableSet<String>,
        onEvent: (InstallEvent) -> Unit
    ) {
        if (!handled.add(dependency.modId)) return // Shared dependency, or a cycle - already done

        try {
            val version = (dependency.versionId?.let { client.getVersionById(it) })
                ?: client.findBestVersion(dependency.modId, mcVersion)

            if (version == null) {
                onEvent(InstallEvent.DependencyMissing(dependency.modId))
                return
            }

            onEvent(InstallEvent.Downloading(version.fileName))
            downloadFile(version.downloadUrl, version.sha1, File(modsDirectory, version.fileName))

            for (nested in version.dependencies.filter { it.required }) {
                installDependency(client, nested, mcVersion, modsDirectory, handled, onEvent)
            }
        } catch (e: Exception) {
            onEvent(InstallEvent.DependencyFailed(dependency.modId, e.message ?: e.toString()))
        }
    }

    private suspend fun downloadFile(url: String, expectedSha1: String?, target: File) = withContext(Dispatchers.IO) {
        if (expectedSha1 != null && target.exists() && sha1Of(target) == expectedSha1) {
            return@withContext // Already have the right file (e.g. shared dependency, or a retry)
        }

        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code} while downloading $url")
            val body = response.body ?: throw java.io.IOException("Empty body for $url")
            val tmp = File(target.parentFile, "${target.name}.part")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out) }

            if (expectedSha1 != null) {
                val actual = sha1Of(tmp)
                if (!actual.equals(expectedSha1, ignoreCase = true)) {
                    tmp.delete()
                    throw java.io.IOException("Checksum mismatch for ${target.name}")
                }
            }
            tmp.renameTo(target)
        }
    }

    private fun sha1Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
