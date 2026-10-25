package dev.lyra.player.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.google.common.util.concurrent.ListenableFuture
import dev.lyra.player.data.ArchiveRepository
import dev.lyra.player.model.CachePolicy
import dev.lyra.player.model.LyraLinks
import dev.lyra.player.model.Track
import dev.lyra.player.model.TrackSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private const val ART_CAP_BYTES = 1_500_000
private const val ART_EDGE = 256

fun Track.toMediaItem(): MediaItem {
    val playback = playbackUri()
    val extras = Bundle().apply {
        putString("album", album)
        putLong("durationMs", durationMs)
        putString("source", source.name)
        putString("uri", playback)
        putString("artwork", artworkUri.orEmpty())
        putString("albumKey", albumKey)
        putBoolean("cacheable", cacheable)
        putLong("dateAdded", dateAdded)
    }
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(playback)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .setExtras(extras)
                .build(),
        )
        .build()
}

fun MediaItem.toTrack(): Track {
    val metadata = mediaMetadata
    val extras = metadata.extras
    val source = runCatching {
        TrackSource.valueOf(extras?.getString("source") ?: TrackSource.LOCAL.name)
    }.getOrDefault(TrackSource.LOCAL)
    return Track(
        id = mediaId,
        title = metadata.title?.toString().orEmpty().ifBlank { "Untitled" },
        artist = metadata.artist?.toString().orEmpty().ifBlank { "Unknown" },
        album = metadata.albumTitle?.toString().orEmpty(),
        durationMs = extras?.getLong("durationMs") ?: 0L,
        source = source,
        uri = extras?.getString("uri") ?: localConfiguration?.uri?.toString().orEmpty(),
        artworkUri = extras?.getString("artwork").takeUnless { it.isNullOrEmpty() }
            ?: metadata.artworkUri?.toString(),
        albumKey = extras?.getString("albumKey").orEmpty(),
        dateAdded = extras?.getLong("dateAdded") ?: 0L,
        cacheable = extras?.getBoolean("cacheable") ?: false,
    )
}

@OptIn(UnstableApi::class)
object StreamCache {
    private val lock = Any()
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val dir = File(context.cacheDir, "stream").apply { mkdirs() }
            val created = SimpleCache(
                dir,
                LeastRecentlyUsedCacheEvictor(128L * 1024 * 1024),
                StandaloneDatabaseProvider(context.applicationContext),
            )
            cache = created
            return created
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            val existing = cache
            if (existing != null) {
                existing.keys.toList().forEach { key ->
                    runCatching { existing.removeResource(key) }
                }
            } else {
                File(context.cacheDir, "stream").deleteRecursively()
            }
        }
    }

    fun bytes(context: Context): Long {
        val dir = File(context.cacheDir, "stream")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

@OptIn(UnstableApi::class)
class LyraDataSourceFactory(
    context: Context,
    private val archive: ArchiveRepository,
) : DataSource.Factory {
    private val app = context.applicationContext
    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(LyraLinks.USER_AGENT)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(20_000)
        .setAllowCrossProtocolRedirects(true)
    private val cacheFactory = CacheDataSource.Factory()
        .setCache(StreamCache.get(app))
        .setUpstreamDataSourceFactory(ResolvingFactory(httpFactory, archive))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    override fun createDataSource(): DataSource = LyraDataSource(app, httpFactory, cacheFactory)
}

@OptIn(UnstableApi::class)
private class ResolvingFactory(
    private val httpFactory: DataSource.Factory,
    private val archive: ArchiveRepository,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = ResolvingHttpDataSource(httpFactory.createDataSource(), archive)
}

@OptIn(UnstableApi::class)
private class ResolvingHttpDataSource(
    private val http: DataSource,
    private val archive: ArchiveRepository,
) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) {
        http.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val resolved = when {
            uri.scheme == "lyra" && uri.host == "archive" -> {
                val id = uri.lastPathSegment ?: throw java.io.IOException("missing archive id")
                Uri.parse(archive.resolveBlocking(id))
            }
            uri.fragment != null -> uri.buildUpon().fragment(null).build()
            else -> uri
        }
        val spec = if (resolved == uri) dataSpec else dataSpec.buildUpon().setUri(resolved).build()
        return http.open(spec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = http.read(buffer, offset, length)

    override fun getUri(): Uri? = http.uri

    override fun getResponseHeaders(): Map<String, List<String>> = http.responseHeaders

    override fun close() = http.close()
}

@OptIn(UnstableApi::class)
private class LyraDataSource(
    private val context: Context,
    private val httpFactory: DataSource.Factory,
    private val cacheFactory: DataSource.Factory,
) : DataSource {
    private val listeners = ArrayList<TransferListener>(2)
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        active?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        close()
        val source = if (CachePolicy.shouldCache(dataSpec.uri.toString())) {
            cacheFactory.createDataSource()
        } else when (dataSpec.uri.scheme) {
            "content" -> androidx.media3.datasource.ContentDataSource(context)
            "file" -> FileDataSource()
            else -> httpFactory.createDataSource()
        }
        listeners.forEach(source::addTransferListener)
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return (active ?: error("not open")).read(buffer, offset, length)
    }

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}

@OptIn(UnstableApi::class)
class SmallArtLoader(context: Context) : BitmapLoader {
    private val app = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lyra-art").apply { isDaemon = true }
    }

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = future("decode") {
        decodeBytes(data) ?: error("artwork")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = future("artwork") {
        load(uri) ?: error("artwork")
    }

    private fun future(name: String, block: () -> Bitmap): ListenableFuture<Bitmap> {
        return CallbackToFutureAdapter.getFuture { completer ->
            executor.execute {
                try {
                    completer.set(block())
                } catch (error: Exception) {
                    completer.setException(error)
                }
            }
            name
        }
    }

    private fun load(uri: Uri): Bitmap? {
        return when (uri.scheme) {
            "content", "file", "android.resource" -> {
                app.contentResolver.openInputStream(uri)?.use { stream ->
                    decodeBytes(readCapped(stream))
                }
            }
            "http", "https" -> {
                val connection = URL(uri.toString()).openConnection() as HttpURLConnection
                connection.connectTimeout = 6_000
                connection.readTimeout = 8_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", LyraLinks.USER_AGENT)
                try {
                    if (connection.responseCode !in 200..299) return null
                    connection.inputStream.use { stream -> decodeBytes(readCapped(stream)) }
                } finally {
                    connection.disconnect()
                }
            }
            else -> null
        }
    }

    private fun readCapped(input: InputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > ART_CAP_BYTES) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun decodeBytes(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > ART_EDGE || bounds.outHeight / sample > ART_EDGE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
