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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ajit.tvhero.remote.ui.AppsTab
import com.ajit.tvhero.remote.ui.CommonAppsPage
import com.ajit.tvhero.remote.ui.HomeScreenPage
import com.ajit.tvhero.remote.ui.KidPage
import com.ajit.tvhero.remote.ui.GuestPage
import com.ajit.tvhero.remote.ui.PairScreen
import com.ajit.tvhero.remote.ui.PhonesPage
import com.ajit.tvhero.remote.ui.QuietPage
import com.ajit.tvhero.remote.ui.OnScreenPage
import com.ajit.tvhero.remote.ui.TypePage
import com.ajit.tvhero.remote.ui.LinksPage
import com.ajit.tvhero.remote.ui.RemoteAccessPage
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
        state.preview(PairedTv("t", "tv-hero · MiTV-AXSO0", "192.168.1.50", "d", "fp", "100.101.12.7"))
        state.connection = "home"
        state.store.deviceName = "Pixel 8"
        state.apps = listOf(
            TvApp("com.google.android.youtube.tv", "YouTube", "allowed", true, false),
            TvApp("com.brave.browser", "Brave", "allowed", false, false),
            TvApp("in.startv.hotstar", "JioHotstar", "allowed", false, false),
            TvApp("com.mitv.tvhome.mitvplus", "Xiaomi TV+", "allowed", false, false),
            TvApp("com.whatsapp", "WhatsApp", "allowed", false, false),
            TvApp("com.phlox.tvwebbrowser", "TV Bro", "allowed", false, false),
            TvApp("com.android.tv", "Live TV", "allowed", true, true),
            TvApp("com.android.vending", "Google Play Store", "allowed", true, true),
            TvApp("com.google.android.youtube.tvkids", "YouTube Kids", "blocked", false, false),
            TvApp("com.example.game", "Racing Game", "pending", false, false),
        )
        val colors = listOf(Color.RED, Color.rgb(251, 84, 43), Color.rgb(40, 60, 230), Color.rgb(255, 110, 60), Color.rgb(37, 211, 102),
            Color.rgb(250, 220, 30), Color.rgb(0, 120, 215), Color.rgb(52, 168, 83), Color.rgb(255, 80, 120), Color.rgb(140, 70, 200))
        state.apps.forEachIndexed { i, a -> state.icons[a.pkg] = icon(colors[i], a.label.take(1)) }
        val common = setOf("com.phlox.tvwebbrowser", "com.whatsapp", "com.brave.browser")
        state.family = Family(
            kids = listOf(
                Kid("k7", "Family", 2, setOf("com.google.android.youtube.tv", "in.startv.hotstar", "com.mitv.tvhome.mitvplus", "com.android.tv"),
                    0, false, 21 * 60, 7 * 60, 72, 0, -1),
                Kid("k1", "Aarav", 0, setOf("com.google.android.youtube.tvkids"), 90, true, 21 * 60, 7 * 60, 65, 10, 35,
                    quizEarnedToday = 10, quizRightToday = 2),
            ),
            active = "k7", onWake = "k7", pinSet = false, locked = false, parentUsed = 48, sleepAt = 0, maxKids = 10, commonApps = common,
            guest = GuestTime(60, 20, 0, 40),
        )
        state.now = NowPlaying(true, "k7", "Family", "in.startv.hotstar", "JioHotstar",
            "India vs Australia, 2nd ODI", now - 23 * 60_000, "none", -1, false, 0)
        state.volume = Volume(45, 100, false, 100)
        state.sessions = listOf(
            Session("k1", "com.google.android.youtube.tvkids", "YouTube Kids", "Cocomelon Songs", now - 5 * 3600_000, now - 4 * 3600_000),
            Session("k7", "com.brave.browser", "Brave", "", now - 90 * 60_000, now - 50 * 60_000),
            Session("k7", "in.startv.hotstar", "JioHotstar", "India vs Australia, 2nd ODI", now - 23 * 60_000, now),
        )
        state.appTime = mapOf(
            "k1" to listOf(AppTime("com.google.android.youtube.tvkids", "YouTube Kids", 41 * 60 + 12), AppTime("com.brave.browser", "Brave", 14 * 60 + 40),
                AppTime("com.whatsapp", "WhatsApp", 9 * 60 + 5)),
            "k7" to listOf(AppTime("in.startv.hotstar", "JioHotstar", 46 * 60), AppTime("com.brave.browser", "Brave", 26 * 60 + 30)),
            "guest" to listOf(AppTime("com.google.android.youtube.tv", "YouTube", 20 * 60)),
        )
        state.usageSource = "usage_stats"
        state.home = HomeLayout(listOf("com.google.android.youtube.tv", "com.brave.browser", "in.startv.hotstar",
            "com.mitv.tvhome.mitvplus", "com.whatsapp", "com.phlox.tvwebbrowser"), listOf(), 6, "The Sharmas", true)
        state.remote = RemoteAccess(false, "", listOf("192.168.1.50"), false, "")
        state.internet = InternetControl(false, 0, "", false, "")
        state.phones = listOf(
            PairedPhone("p1", "Mum's phone", now - 86400_000, now - 30_000, "home", true, true),
            PairedPhone("p2", "Setup computer", now - 3 * 3600_000, now - 2 * 3600_000, "home", false, false),
        )
        state.requests = listOf(KidRequest("r1", "k1", "Aarav", "com.whatsapp", "WhatsApp", now - 600_000),
            KidRequest("r2", "k1", "Aarav", "time:30", "30 more minutes today", now - 120_000, kind = "time"))
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
                    if (tab >= 0) TabBar(listOf(
                        TabItem("TV", Icons.Outlined.Tv, Icons.Rounded.Tv),
                        TabItem("Family", Icons.Outlined.FamilyRestroom, Icons.Rounded.FamilyRestroom),
                        TabItem("Apps", Icons.Outlined.Apps, Icons.Rounded.Apps, badge = 1),
                        TabItem("Remote", Icons.Outlined.SettingsRemote, Icons.Rounded.SettingsRemote),
                        TabItem("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
                    ), tab) {}
                }
            }
        }
        compose.waitForIdle()
        compose.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().captureRoboImage(File(dir, "$name.png").path)
    }

    private val gate get() = PasswordGate(state)

    @Test fun tv() = shot("01-tv", 0) { TvTab(state, gate) {} }
    @Test fun family() = shot("02-family", 1) { FamilyTab(state, gate) }
    @Test fun familyProfile() = shot("03-family-profile", 1) { KidPage(state, gate, state.family!!.kids[0]) {} }
    @Test fun kidProfile() = shot("04-kid-profile", 1) { KidPage(state, gate, state.family!!.kids[1]) {} }
    @Test fun guest() = shot("16-guest", 1) { GuestPage(state, gate) {} }
    @Test fun welcome() = shot("17-welcome", -1) { com.ajit.tvhero.remote.ui.ConnectScreen(state) }
    @Test fun setUpNewTv() = shot("18-set-up-new-tv", -1) { com.ajit.tvhero.remote.ui.SetupNewTvScreen(state) {} }
    @Test fun everyone() = shot("05-apps-for-everyone", 1) { CommonAppsPage(state, gate) {} }
    @Test fun apps() = shot("06-apps", 2) { AppsTab(state, gate) }
    @Test fun remote() = shot("07-remote", 3) { RemoteTab(state, compose.activity) }
    @Test fun settings() = shot("08-settings", 4) { SettingsTab(state, gate) }
    @Test fun homeScreen() = shot("09-home-screen", 4) { HomeScreenPage(state) {} }
    @Test fun phones() = shot("10-who-can-control", 4) { PhonesPage(state, gate) {} }
    @Test fun internet() = shot("11-internet-control", 4) { RemoteAccessPage(state, gate) {} }
    @Test fun quiet() = shot("12-night-volume", 4) {
        QuietPage(state, org.json.JSONObject().put("enabled", true).put("start", 21 * 60 + 30).put("end", 7 * 60).put("cap_percent", 35)) {}
    }
    @Test fun onScreen() = shot("19-on-screen-notes", 4) {
        OnScreenPage(state, com.ajit.tvhero.remote.OnScreenNotes(timeLeft = true, toolsCorner = true, homeInfo = false, warnings = true)) {}
    }
    @Test fun typeOnTv() = shot("20-type-on-tv", 0) { TypePage(state, "dinosaur songs for kids") {} }
    @Test fun linksSearch() = shot("21-links-search", 4) {
        LinksPage(state, com.ajit.tvhero.remote.LinkSettings("com.phlox.tvwebbrowser",
            listOf("com.phlox.tvwebbrowser" to "TV Bro", "com.brave.browser" to "Brave"), true,
            mapOf("parent" to "duckduckgo"), listOf("duckduckgo" to "DuckDuckGo (safe search)", "google" to "Google (SafeSearch)", "bing" to "Bing (SafeSearch)"))) {}
    }
    @Test fun pair() = shot("13-pair", 4) { PairScreen(state, "192.168.1.50", "MiTV-AXSO0") {} }

    @Config(qualifiers = "w412dp-h915dp-night-xxhdpi")
    @Test fun tvDark() = shot("14-tv-dark", 0) { TvTab(state, gate) {} }
    @Config(qualifiers = "w412dp-h915dp-night-xxhdpi")
    @Test fun familyDark() = shot("15-family-dark", 1) { FamilyTab(state, gate) }
}
