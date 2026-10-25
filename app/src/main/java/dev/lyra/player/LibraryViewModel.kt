package dev.lyra.player

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lyra.player.data.ArchiveRepository
import dev.lyra.player.data.M3u
import dev.lyra.player.data.userMessage
import dev.lyra.player.model.MusicIndex
import dev.lyra.player.model.Playlist
import dev.lyra.player.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

object AudioAccess {
    fun permission(): String = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission()) == PackageManager.PERMISSION_GRANTED
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as LyraApp).graph
    var index by mutableStateOf(MusicIndex())
        private set
    var granted by mutableStateOf(AudioAccess.granted(app))
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var trending by mutableStateOf<List<Track>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
    private var loadedAt = 0L

    init {
        refreshPlaylists()
        if (granted) refreshLibrary()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { graph.audius.trending() } }
            result.onSuccess { trending = it }
            result.onFailure { ArchiveRepository.logFailure("trending", it) }
        }
    }

    fun refreshPermission() {
        val now = AudioAccess.granted(getApplication())
        granted = now
        if (now && System.currentTimeMillis() - loadedAt > 60_000) refreshLibrary()
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching { graph.library.load() } }
            loaded.onSuccess {
                index = it
                loadedAt = System.currentTimeMillis()
            }.onFailure { message = it.userMessage() }
        }
    }

    fun refreshPlaylists() {
        viewModelScope.launch { playlists = graph.playlists.list() }
    }

    fun createPlaylist(name: String, tracks: List<Track> = emptyList()) {
        viewModelScope.launch {
            graph.playlists.create(name, tracks)
            playlists = graph.playlists.list()
            note("Saved “${name.trim().ifBlank { "Playlist" }}”.")
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            graph.playlists.delete(id)
            playlists = graph.playlists.list()
        }
    }

    fun addToPlaylist(id: String, tracks: List<Track>) {
        viewModelScope.launch {
            graph.playlists.addTracks(id, tracks)
            playlists = graph.playlists.list()
            note("Added to the playlist.")
        }
    }

    fun removeFromPlaylist(id: String, trackId: String) {
        viewModelScope.launch {
            graph.playlists.removeTrack(id, trackId)
            playlists = graph.playlists.list()
        }
    }

    fun importM3u(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                getApplication<LyraApp>().contentResolver.openInputStream(uri)?.use { readCapped(it) }
            }
            if (text.isNullOrBlank()) {
                note("That file was empty.")
                return@launch
            }
            val matched = M3u.match(text, index.tracks)
            if (matched.isEmpty()) {
                note("None of those songs are on this device.")
                return@launch
            }
            val name = displayName(getApplication(), uri)
            graph.playlists.create(name, matched)
            playlists = graph.playlists.list()
            note("Imported ${matched.size} songs.")
        }
    }

    fun note(text: String) {
        message = text
        viewModelScope.launch {
            delay(3200)
            if (message == text) message = null
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        val fallback = "Imported"
        val raw = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: return fallback
        return raw.substringBeforeLast('.').trim().take(60).ifBlank { fallback }
    }

    private fun readCapped(input: InputStream, cap: Int = 512_000): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        while (total < cap) {
            val read = input.read(buffer)
            if (read < 0) break
            val take = minOf(read, cap - total)
            out.write(buffer, 0, take)
            total += take
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as LyraApp).graph
    var query by mutableStateOf("")
        private set
    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var albums by mutableStateOf(emptyList<dev.lyra.player.model.AlbumGroup>())
        private set
    var audius by mutableStateOf<List<Track>>(emptyList())
        private set
    var archive by mutableStateOf<List<Track>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var note by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    fun update(value: String, index: MusicIndex) {
        query = value
        job?.cancel()
        val trimmed = value.trim()
        if (trimmed.length < 2) {
            tracks = emptyList()
            albums = emptyList()
            audius = emptyList()
            archive = emptyList()
            searching = false
            note = null
            return
        }
        job = viewModelScope.launch {
            delay(280)
            val needle = trimmed.lowercase()
            tracks = index.tracks.asSequence().filter { track ->
                track.title.lowercase().contains(needle) ||
                    track.artist.lowercase().contains(needle) ||
                    track.album.lowercase().contains(needle)
            }.take(30).toList()
            albums = index.albums.asSequence().filter { album ->
                album.title.lowercase().contains(needle) || album.artist.lowercase().contains(needle)
            }.take(8).toList()
            searching = true
            note = null
            val audiusResult = withContext(Dispatchers.IO) { runCatching { graph.audius.search(trimmed) } }
            val archiveResult = withContext(Dispatchers.IO) { runCatching { graph.archive.search(trimmed) } }
            audius = audiusResult.getOrElse { emptyList() }
            archive = archiveResult.getOrElse { emptyList() }
            val failure = audiusResult.exceptionOrNull() ?: archiveResult.exceptionOrNull()
            if (failure != null && audius.isEmpty() && archive.isEmpty()) {
                ArchiveRepository.logFailure("search", failure)
                note = failure.userMessage()
            }
            searching = false
        }
    }
}
