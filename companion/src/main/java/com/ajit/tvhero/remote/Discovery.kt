package com.ajit.tvhero.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedQueue

data class FoundTv(val name: String, val host: String)

/** Finds tv-hero TVs on the Wi-Fi through DNS-SD (_tvhero._tcp). */
class Discovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val _found = MutableStateFlow<List<FoundTv>>(emptyList())
    val found: StateFlow<List<FoundTv>> = _found
    private var listener: NsdManager.DiscoveryListener? = null
    // Older Android versions resolve one service at a time.
    private val queue = ConcurrentLinkedQueue<NsdServiceInfo>()
    @Volatile private var resolving = false

    fun start() {
        if (listener != null || nsd == null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(t: String, e: Int) { listener = null }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onServiceFound(s: NsdServiceInfo) { queue.add(s); next() }
            override fun onServiceLost(s: NsdServiceInfo) {
                _found.value = _found.value.filterNot { it.name == s.serviceName }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(com.ajit.tvhero.protocol.Protocol.NSD_TYPE.removeSuffix("."), NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { Log.w("Discovery", "discovery failed: ${it.message}"); listener = null }
    }

    @Suppress("DEPRECATION")
    private fun next() {
        if (resolving) return
        val s = queue.poll() ?: return
        resolving = true
        nsd?.resolveService(s, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, e: Int) { resolving = false; next() }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress
                if (host != null && !host.contains(':')) {
                    _found.value = (_found.value.filterNot { it.host == host } + FoundTv(info.serviceName, host))
                }
                resolving = false
                next()
            }
        })
    }

    fun stop() {
        listener?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
        listener = null
    }
}
