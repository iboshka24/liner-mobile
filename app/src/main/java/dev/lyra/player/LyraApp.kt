package dev.lyra.player

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.lyra.player.data.ArchiveRepository
import dev.lyra.player.data.AudiusRepository
import dev.lyra.player.data.LibraryRepository
import dev.lyra.player.data.Net
import dev.lyra.player.data.PlaylistRepository
import dev.lyra.player.lyrics.LyricsRepository
import java.io.File

class LyraApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        val net = Net(Net.client())
        graph = AppGraph(
            net = net,
            library = LibraryRepository(this),
            audius = AudiusRepository(net),
            archive = ArchiveRepository(net, File(filesDir, "archive-map.txt")),
            lyrics = LyricsRepository(this, net),
            playlists = PlaylistRepository(this),
        )
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizeBytes(12 * 1024 * 1024)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("artwork"))
                        .maxSizeBytes(32L * 1024 * 1024)
                        .build()
                }
                .okHttpClient(net.client)
                .respectCacheHeaders(false)
                .crossfade(false)
                .build(),
        )
    }
}

class AppGraph(
    val net: Net,
    val library: LibraryRepository,
    val audius: AudiusRepository,
    val archive: ArchiveRepository,
    val lyrics: LyricsRepository,
    val playlists: PlaylistRepository,
)
