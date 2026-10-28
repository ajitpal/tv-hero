package com.ajit.tvhero.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ajit.tvhero.remote.ui.Root
import com.ajit.tvhero.remote.ui.TvHeroTheme

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = AppState.get(this)
        setContent { TvHeroTheme { Root(state, this) } }
    }

    override fun onStart() {
        super.onStart()
        state.discovery.start()
        state.refresh()
    }

    override fun onStop() {
        state.discovery.stop()
        super.onStop()
    }
}
