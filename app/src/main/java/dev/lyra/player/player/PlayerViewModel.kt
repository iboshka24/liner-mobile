package dev.lyra.player.player

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.lyra.player.LyraApp
import dev.lyra.player.model.SavedSession
import dev.lyra.player.model.Track
import dev.lyra.player.ui.paletteColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as LyraApp).graph

    var track by mutableStateOf<Track?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
    var accent by mutableStateOf(dev.lyra.player.ui.LyraColors.brass)
        private set
    var resume by mutableStateOf<SavedSession?>(null)
        private set

    private var controller: MediaController? = null
    private var connecting = false
    private var pending: Pending? = null
    private var saveJob: Job? = null
    private var paletteJob: Job? = null
    private var paletteFor: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            render(player)
            scheduleSave()
        }

        override fun onPlayerError(playbackError: PlaybackException) {
            error = friendly(playbackError)
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            if (playing) error = null
        }
    }

    init {
        connect()
        viewModelScope.launch {
            resume = graph.playlists.loadSession()
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(getApplication(), ComponentName(getApplication(), PlaybackService::class.java))
        val future = MediaController.Builder(getApplication(), token).buildAsync()
        future.addListener(
            {
                connecting = false
                try {
                    val created = future.get()
                    controller = created
                    created.addListener(listener)
                    render(created)
                    pending?.let { play(it.tracks, it.index, it.positionMs) }
                    pending = null
                } catch (failed: Exception) {
                    error = "The player didn't start."
                }
            },
            ContextCompat.getMainExecutor(getApplication()),
        )
    }

    fun play(tracks: List<Track>, start: Int = 0, position: Long = 0L) {
        if (tracks.isEmpty()) return
        val safeIndex = start.coerceIn(0, tracks.lastIndex)
        val active = controller
        if (active == null) {
            pending = Pending(tracks, safeIndex, position)
            connect()
            return
        }
        error = null
        active.setMediaItems(tracks.map { it.toMediaItem() }, safeIndex, position.coerceAtLeast(0L))
        active.prepare()
        active.play()
    }

    fun toggle() {
        val active = controller ?: return
        if (active.isPlaying) active.pause() else active.play()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seek(position: Long) {
        controller?.seekTo(position.coerceAtLeast(0L))
        positionMs = position.coerceAtLeast(0L)
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        val active = controller ?: return
        active.repeatMode = when (active.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun seekQueue(item: Int) {
        val active = controller ?: return
        if (item !in 0 until active.mediaItemCount) return
        active.seekToDefaultPosition(item)
        active.play()
    }

    fun playNext(track: Track) {
        val active = controller
        if (active == null || active.mediaItemCount == 0) {
            play(listOf(track))
            return
        }
        active.addMediaItem(active.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun retry() {
        val active = controller ?: return
        error = null
        active.prepare()
        active.play()
    }

    fun clearError() {
        error = null
    }

    fun position(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: positionMs

    private fun render(player: Player) {
        val count = player.mediaItemCount
        val items = ArrayList<Track>(count)
        for (i in 0 until count) items += player.getMediaItemAt(i).toTrack()
        queue = items
        val itemIndex = if (count == 0) 0 else player.currentMediaItemIndex.coerceIn(0, count - 1)
        index = itemIndex
        val current = items.getOrNull(itemIndex)
        track = current
        isPlaying = player.isPlaying
        val reported = player.duration
        durationMs = if (reported > 0) reported else current?.durationMs ?: 0L
        positionMs = player.currentPosition.coerceAtLeast(0L)
        shuffle = player.shuffleModeEnabled
        repeatMode = player.repeatMode
        loadAccent(current)
    }

    private fun loadAccent(current: Track?) {
        val id = current?.id
        if (id == paletteFor) return
        paletteFor = id
        paletteJob?.cancel()
        val art = current?.artworkUri
        if (art.isNullOrBlank()) {
            accent = dev.lyra.player.ui.LyraColors.brass
            return
        }
        paletteJob = viewModelScope.launch {
            val color = paletteColor(getApplication(), art)
            if (paletteFor == id) {
                accent = color ?: dev.lyra.player.ui.LyraColors.brass
            }
        }
    }

    private fun scheduleSave() {
        if (queue.isEmpty()) return
        val snapshot = SavedSession(queue, index, positionMs)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            graph.playlists.saveSession(snapshot)
            resume = snapshot
        }
    }

    private fun friendly(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        -> "This track didn't load. Check the connection and try again."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The audio file is missing."
        else -> "Playback stopped. Try again."
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }

    private data class Pending(val tracks: List<Track>, val index: Int, val positionMs: Long)
}
