package com.ajit.tvhero.remote

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.ajit.tvhero.remote.ui.Root
import com.ajit.tvhero.remote.ui.hig.HigTheme

class MainActivity : FragmentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = AppState.get(this)
        setContent { HigTheme { Root(state, this) } }
    }

    override fun onStart() {
        super.onStart()
        val away = System.currentTimeMillis() - state.backgroundAt
        if (state.store.appLock && AppLock.available(this) && (state.backgroundAt == 0L || away > AppLock.RELOCK_AFTER_MS)) {
            state.locked = true
            unlock()
        }
        state.discovery.start()
        state.refresh()
        state.startPolling()
        state.software.checkIfDue()
    }

    fun unlock() = AppLock.prompt(this) { state.locked = false }

    override fun onStop() {
        state.backgroundAt = System.currentTimeMillis()
        state.stopPolling()
        state.discovery.stop()
        super.onStop()
    }
}
