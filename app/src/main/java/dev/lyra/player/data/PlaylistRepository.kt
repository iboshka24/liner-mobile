package dev.lyra.player.data

import android.content.Context
import dev.lyra.player.model.Playlist
import dev.lyra.player.model.SavedSession
import dev.lyra.player.model.Track
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class PlaylistRepository(context: Context) {
    private val file = File(context.filesDir, "playlists.json")
    private val sessionFile = File(context.filesDir, "session.json")
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private var playlists: List<Playlist>? = null

    suspend fun list(): List<Playlist> = mutex.withLock {
        playlists ?: readPlaylists().also { playlists = it }
    }

    suspend fun create(name: String, tracks: List<Track> = emptyList()): Playlist = mutex.withLock {
        val current = playlists ?: readPlaylists()
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().take(60).ifBlank { "Playlist" },
            tracks = tracks.distinctBy { it.id },
            createdAt = System.currentTimeMillis(),
        )
        val next = current + playlist
        writePlaylists(next)
        playlist
    }

    suspend fun delete(id: String) = update { it.filterNot { playlist -> playlist.id == id } }

    suspend fun rename(id: String, name: String) = update { list ->
        list.map { playlist ->
            if (playlist.id == id) playlist.copy(name = name.trim().take(60).ifBlank { playlist.name }) else playlist
        }
    }

    suspend fun addTracks(id: String, tracks: List<Track>) = update { list ->
        list.map { playlist ->
            if (playlist.id != id) playlist
            else playlist.copy(tracks = (playlist.tracks + tracks).distinctBy { it.id })
        }
    }

    suspend fun removeTrack(id: String, trackId: String) = update { list ->
        list.map { playlist ->
            if (playlist.id != id) playlist
            else playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId })
        }
    }

    suspend fun loadSession(): SavedSession? = mutex.withLock {
        if (!sessionFile.exists()) return null
        runCatching { json.decodeFromString<SavedSession>(sessionFile.readText()) }
            .getOrNull()
            ?.takeIf { it.tracks.isNotEmpty() }
    }

    suspend fun saveSession(session: SavedSession) = mutex.withLock {
        writeAtomic(sessionFile, json.encodeToString(session))
    }

    private suspend fun update(block: (List<Playlist>) -> List<Playlist>) {
        mutex.withLock {
            val current = playlists ?: readPlaylists()
            writePlaylists(block(current))
        }
    }

    private fun readPlaylists(): List<Playlist> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<PlaylistFile>(file.readText()).playlists
        }.getOrElse {
            emptyList()
        }
    }

    private fun writePlaylists(next: List<Playlist>) {
        playlists = next
        writeAtomic(file, json.encodeToString(PlaylistFile(next)))
    }

    private fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    @Serializable
    private data class PlaylistFile(val playlists: List<Playlist> = emptyList())
}
