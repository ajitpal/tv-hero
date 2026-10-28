package com.ajit.tvhero.remote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ajit.tvhero.remote.ui.AppsTab
import com.ajit.tvhero.remote.ui.FamilyTab
import com.ajit.tvhero.remote.ui.PasswordGate
import com.ajit.tvhero.remote.ui.RemoteTab
import com.ajit.tvhero.remote.ui.SettingsTab
import com.ajit.tvhero.remote.ui.TvTab
import com.ajit.tvhero.remote.ui.hig.HigTheme
import com.ajit.tvhero.remote.ui.hig.TabBar
import com.ajit.tvhero.remote.ui.hig.TabItem
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every tab with sample data to docs/screenshots/phone/. Runs only with
 * -Pscreenshots=record, so normal test runs stay fast.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var state: AppState
    private val dir = File(System.getProperty("screenshotDir") ?: "build/screenshots")

    @Before fun setUp() {
        assumeTrue(System.getProperty("screenshots") == "record")
        dir.mkdirs()
        state = AppState.get(compose.activity)
        val now = System.currentTimeMillis()
        state.preview(PairedTv("t", "tv-hero · MiTV-AXSO0", "192.168.1.50", "d", ByteArray(32), "100.101.12.7"))
        state.connection = "home"
        state.apps = listOf(
            TvApp("com.google.android.youtube.tv", "YouTube", "allowed", true, false),
            TvApp("com.google.android.youtube.tvkids", "YouTube Kids", "allowed", false, false),
            TvApp("in.startv.hotstar", "JioHotstar", "allowed", false, false),
            TvApp("com.mitv.tvhome.mitvplus", "Xiaomi TV+", "allowed", false, false),
            TvApp("com.android.tv", "Live TV", "allowed", true, true),
            TvApp("com.android.vending", "Google Play Store", "allowed", true, true),
            TvApp("com.example.game", "Racing Game", "pending", false, false),
            TvApp("com.example.shorts", "Shorts Player", "blocked", false, false),
        )
        val colors = listOf(Color.RED, Color.rgb(255, 80, 120), Color.rgb(40, 60, 230), Color.rgb(255, 110, 60), Color.rgb(0, 120, 215),
            Color.rgb(52, 168, 83), Color.rgb(140, 70, 200), Color.DKGRAY)
        state.apps.forEachIndexed { i, a -> state.icons[a.pkg] = icon(colors[i], a.label.take(1)) }
        state.family = Family(
            kids = listOf(
                Kid("k1", "Aarav", 0, setOf("com.google.android.youtube.tvkids", "com.mitv.tvhome.mitvplus"), 90, true, 21 * 60, 7 * 60, 65, 0, 25),
                Kid("k2", "Anaya", 7, setOf("com.google.android.youtube.tvkids"), 60, true, 20 * 60 + 30, 7 * 60, 60, 0, 0),
            ),
            active = "k1", onWake = "ask", pinSet = true, locked = false, parentUsed = 48, sleepAt = 0, maxKids = 10,
        )
        state.now = NowPlaying(true, "k1", "Aarav", "com.google.android.youtube.tvkids", "YouTube Kids",
            "Peppa Pig: The Big Day Out", now - 23 * 60_000, "none", 25, false, 0)
        state.sessions = listOf(
            Session("k2", "com.google.android.youtube.tvkids", "YouTube Kids", "Cocomelon Songs", now - 5 * 3600_000, now - 4 * 3600_000),
            Session("parent", "in.startv.hotstar", "JioHotstar", "", now - 3 * 3600_000, now - 2 * 3600_000 - 600_000),
            Session("k1", "com.mitv.tvhome.mitvplus", "Xiaomi TV+", "Cartoon Network", now - 90 * 60_000, now - 50 * 60_000),
            Session("k1", "com.google.android.youtube.tvkids", "YouTube Kids", "Peppa Pig: The Big Day Out", now - 23 * 60_000, now),
        )
        state.home = HomeLayout(listOf("com.google.android.youtube.tv", "com.google.android.youtube.tvkids", "in.startv.hotstar",
            "com.mitv.tvhome.mitvplus", "com.android.tv", "com.android.vending"), listOf(), 6, "Pal family", true)
        state.remote = RemoteAccess(false, "", listOf("192.168.1.50", "100.101.12.7"), true, "100.101.12.7")
    }

    private fun icon(color: Int, letter: String): Bitmap {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(color)
        c.drawText(letter, 30f, 66f, Paint().apply { this.color = Color.WHITE; textSize = 52f; isFakeBoldText = true })
        return b
    }

    private fun shot(name: String, tab: Int, content: @Composable () -> Unit) {
        compose.setContent {
            HigTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { content() }
                    TabBar(listOf(
                        TabItem("TV", androidx.compose.material.icons.Icons.Outlined.Tv, androidx.compose.material.icons.Icons.Rounded.Tv),
                        TabItem("Family", androidx.compose.material.icons.Icons.Outlined.FamilyRestroom, androidx.compose.material.icons.Icons.Rounded.FamilyRestroom),
                        TabItem("Apps", androidx.compose.material.icons.Icons.Outlined.Apps, androidx.compose.material.icons.Icons.Rounded.Apps, badge = 1),
                        TabItem("Remote", androidx.compose.material.icons.Icons.Outlined.SettingsRemote, androidx.compose.material.icons.Icons.Rounded.SettingsRemote),
                        TabItem("Settings", androidx.compose.material.icons.Icons.Outlined.Settings, androidx.compose.material.icons.Icons.Rounded.Settings),
                    ), tab) {}
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(dir, "$name.png").path)
    }

    @Test fun tv() = shot("1-tv", 0) { TvTab(state, PasswordGate(state)) {} }
    @Test fun family() = shot("2-family", 1) { FamilyTab(state, PasswordGate(state)) }
    @Test fun apps() = shot("3-apps", 2) { AppsTab(state, PasswordGate(state)) }
    @Test fun remote() = shot("4-remote", 3) { RemoteTab(state, compose.activity) }
    @Test fun settings() = shot("5-settings", 4) { SettingsTab(state, PasswordGate(state)) }

    @Config(qualifiers = "w412dp-h915dp-xxhdpi-night")
    @Test fun tvDark() = shot("6-tv-dark", 0) { TvTab(state, PasswordGate(state)) {} }
}
