package dev.lyra.player.data

import dev.lyra.player.model.LyraLinks
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class HttpStatusException(val code: Int) : IOException("HTTP $code")

class Net(val client: OkHttpClient) {
    fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", LyraLinks.USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            return body
        }
    }

    companion object {
        fun client(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}

fun <T> retrying(times: Int = 3, block: () -> T): T {
    var last: Exception? = null
    repeat(times) { attempt ->
        try {
            return block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (http: HttpStatusException) {
            if (http.code in 400..499 && http.code != 408 && http.code != 429) throw http
            last = http
        } catch (io: IOException) {
            last = io
        }
        if (attempt < times - 1) {
            try {
                Thread.sleep(250L * (attempt + 1) * (attempt + 1))
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("interrupted")
            }
        }
    }
    throw last ?: IOException("request failed")
}

fun Throwable.userMessage(): String = when (this) {
    is UnknownHostException, is SocketTimeoutException, is ConnectException ->
        "The network is unavailable."
    is HttpStatusException -> when (code) {
        404 -> "Nothing matched."
        429 -> "The service asked us to slow down. Try again shortly."
        else -> "The service is busy. Try again."
    }
    is IOException -> "The request failed. Try again."
    else -> "Something went wrong."
}
