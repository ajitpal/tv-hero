package com.ajit.tvhero.remote

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Set Up a New TV: finds TVs with debugging switched on (port 5555 open) on this phone's Wi-Fi.
 * Only the phone's own private /24 network is scanned; nothing is sent beyond a TCP connect.
 */
object NetScan {
    fun myAddress(ctx: Context): Inet4Address? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress }
    }

    suspend fun findDebuggableTvs(ctx: Context): List<String> = withContext(Dispatchers.IO) {
        val me = myAddress(ctx) ?: return@withContext emptyList()
        val b = me.address
        val gate = Semaphore(48)
        coroutineScope {
            (1..254).filter { it != (b[3].toInt() and 0xff) }.map { last ->
                async {
                    gate.withPermit {
                        val host = "${b[0].toInt() and 0xff}.${b[1].toInt() and 0xff}.${b[2].toInt() and 0xff}.$last"
                        runCatching { Socket().use { s -> s.connect(InetSocketAddress(host, 5555), 350) }; host }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}
