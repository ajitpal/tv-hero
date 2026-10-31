package com.ajit.tvhero.remote

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Share → tv-hero (2.10). Takes the first web link out of the shared text and opens tv-hero, which asks
 * where to open it on the TV. Nothing is sent before that.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val link = webLink(text)
        if (link == null) {
            Toast.makeText(this, "There's no web link to open on the TV.", Toast.LENGTH_SHORT).show()
        } else {
            AppState.get(this).sharedLink = link
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        finish()
    }

    companion object {
        private val URL = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)

        /** The first http(s) link in [text], without trailing punctuation; the TV checks it again. */
        fun webLink(text: String?): String? {
            val m = URL.find(text ?: return null) ?: return null
            return m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'').takeIf { it.length in 10..2048 }
        }
    }
}
