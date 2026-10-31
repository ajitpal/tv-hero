package com.ajit.tvhero.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Photos on the TV, streamed (2.10). The phone runs the slideshow: it resizes each photo to the TV's screen,
 * re-encodes it as a JPEG (which leaves out its metadata, such as where it was taken) and sends it just
 * before it shows. The TV keeps nothing: it shows the photo it gets and closes the slideshow 30 seconds
 * after the last one. So this keeps going only while this app is open.
 */
class PhotoSlideshow(private val state: AppState) {
    var photos by mutableStateOf<List<Uri>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set
    var running by mutableStateOf(false); private set
    var paused by mutableStateOf(false); private set
    var seconds by mutableIntStateOf(8)
    var problem by mutableStateOf<String?>(null); private set

    private var job: Job? = null
    private var session = ""
    /** Set by Next / Previous so the wait ends at once. */
    @Volatile private var jump = false

    fun start(context: Context, list: List<Uri>) {
        if (list.isEmpty()) return
        stop(tellTv = false)
        photos = list
        index = 0
        paused = false
        problem = null
        running = true
        session = UUID.randomUUID().toString()
        val ctx = context.applicationContext
        job = state.scope.launch { loop(ctx) }
    }

    private suspend fun loop(ctx: Context) {
        while (state.scope.isActive && running) {
            val i = index
            val jpeg = withContext(Dispatchers.IO) { runCatching { prepare(ctx, photos[i]) }.getOrNull() }
            if (jpeg == null) { advance(i); continue }   // unreadable photo: skip it
            if (!send(jpeg, i)) return
            // Wait for the next photo. While paused, send the same one again every 20 s, so the TV,
            // which closes a slideshow 30 s after its last photo, keeps showing it.
            var waited = 0
            jump = false
            while (running && !jump && (paused || waited < seconds * 1000)) {
                delay(250)
                waited += 250
                if (paused && waited >= 20_000) {
                    if (!send(jpeg, i)) return
                    waited = 0
                }
            }
            if (!running) return
            if (!jump) advance(i)
        }
    }

    private suspend fun send(jpeg: ByteArray, i: Int): Boolean {
        val c = state.client ?: run { problem = "Not connected to a TV."; running = false; return false }
        return try {
            c.showPhoto(jpeg, session, "${i + 1} / ${photos.size}")
            problem = null
            true
        } catch (e: TvException) {
            problem = if (e.status == 410) "Stopped on the TV." else e.message
            running = false
            false
        } catch (e: Exception) {
            problem = e.message ?: "Couldn't reach the TV."
            running = false
            false
        }
    }

    private fun advance(from: Int) {
        if (index == from) index = (from + 1) % photos.size.coerceAtLeast(1)
    }

    fun next() { if (photos.isNotEmpty()) { index = (index + 1) % photos.size; jump = true } }

    fun previous() { if (photos.isNotEmpty()) { index = (index - 1 + photos.size) % photos.size; jump = true } }

    fun togglePause() { paused = !paused; if (!paused) jump = true }

    fun stop(tellTv: Boolean = true) {
        val wasRunning = running
        running = false
        job?.cancel()
        job = null
        if (tellTv && wasRunning) state.scope.launch { state.client?.stopPhotos() }
    }

    companion object {
        /** The photo at the TV's size as a JPEG, upright (ImageDecoder applies the camera's rotation). */
        fun prepare(ctx: Context, uri: Uri): ByteArray {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(src) { d, info, _ ->
                val (w, h) = fitWithin(info.size.width, info.size.height)
                d.setTargetSize(w, h)
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try {
                val out = ByteArrayOutputStream(256 * 1024)
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                return out.toByteArray()
            } finally {
                bmp.recycle()
            }
        }
    }
}
