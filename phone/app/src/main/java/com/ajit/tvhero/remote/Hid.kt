package com.ajit.tvhero.remote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

/**
 * The phone as a Bluetooth HID device: one keyboard (arrows, Enter, typing), one consumer
 * control (Home, Back, volume, play/pause) and one game pad (16 buttons, D-pad hat, two sticks).
 * The TV pairs with it like any Bluetooth remote or controller, so it works in every app and
 * game, with no software on the TV side.
 */
@SuppressLint("MissingPermission")   // callers check BLUETOOTH_CONNECT first
class HidController(private val context: Context) {

    sealed interface Status {
        data object Off : Status
        data class Unavailable(val why: String) : Status
        data object Starting : Status
        data object Ready : Status
        data class Connected(val name: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            _status.value = if (registered) Status.Ready else Status.Unavailable("Bluetooth remote could not start on this phone.")
            if (registered && pluggedDevice != null) hid?.connect(pluggedDevice)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    _status.value = Status.Connected(device.name ?: "TV")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host == device) host = null
                    if (_status.value is Status.Connected) _status.value = Status.Ready
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = when (id.toInt()) { KEYBOARD -> 8; CONSUMER -> 2; GAMEPAD -> 7; else -> 0 }
            hid?.replyReport(device, type, id, ByteArray(size).also { if (id.toInt() == GAMEPAD) it[2] = HAT_NONE })
        }
    }

    fun start() {
        if (adapter == null) { _status.value = Status.Unavailable("This phone has no Bluetooth."); return }
        if (!adapter.isEnabled) { _status.value = Status.Unavailable("Turn on Bluetooth to use the remote and game controller."); return }
        if (hid != null) return
        _status.value = Status.Starting
        val ok = adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val h = proxy as BluetoothHidDevice
                hid = h
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "tv-hero remote", "Remote, keyboard and game controller", "tv-hero",
                    BluetoothHidDevice.SUBCLASS1_COMBO, DESCRIPTOR)
                if (!h.registerApp(sdp, null, null, executor, callback)) {
                    _status.value = Status.Unavailable("This phone doesn't allow acting as a Bluetooth controller.")
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                hid = null
                _status.value = Status.Off
            }
        }, BluetoothProfile.HID_DEVICE)
        if (!ok) _status.value = Status.Unavailable("This phone doesn't support the Bluetooth controller profile.")
    }

    fun stop() {
        hid?.let { runCatching { it.unregisterApp() } }
        hid?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hid = null
        host = null
        _status.value = Status.Off
    }

    /** TVs this phone is already paired with over Bluetooth. */
    fun bondedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.toList().orEmpty()

    fun connect(device: BluetoothDevice) {
        hid?.connect(device)
    }

    val connected: Boolean get() = host != null

    // ---------- keyboard / consumer ----------

    fun tapKey(usage: Int, modifiers: Int = 0) {
        send(KEYBOARD, byteArrayOf(modifiers.toByte(), 0, usage.toByte(), 0, 0, 0, 0, 0))
        send(KEYBOARD, ByteArray(8))
    }

    fun tapConsumer(usage: Int) {
        send(CONSUMER, byteArrayOf((usage and 0xff).toByte(), (usage shr 8 and 0xff).toByte()))
        send(CONSUMER, ByteArray(2))
    }

    fun type(text: String) {
        for (ch in text) {
            val (usage, shift) = charUsage(ch) ?: continue
            tapKey(usage, if (shift) MOD_SHIFT else 0)
        }
    }

    // ---------- game pad ----------

    private var buttons = 0
    private var hat = HAT_NONE.toInt()
    private val axes = ByteArray(4)

    fun button(index: Int, down: Boolean) {
        buttons = if (down) buttons or (1 shl (index - 1)) else buttons and (1 shl (index - 1)).inv()
        sendPad()
    }

    fun dpad(dir: Int) {   // 0..7 clockwise from up, or -1 for released
        hat = if (dir < 0) HAT_NONE.toInt() else dir
        sendPad()
    }

    /** x, y in -1..1; stick 0 = left (X/Y), 1 = right (Z/Rz). */
    fun stick(stick: Int, x: Float, y: Float) {
        axes[stick * 2] = (x.coerceIn(-1f, 1f) * 127).toInt().toByte()
        axes[stick * 2 + 1] = (y.coerceIn(-1f, 1f) * 127).toInt().toByte()
        sendPad()
    }

    private fun sendPad() {
        send(GAMEPAD, byteArrayOf((buttons and 0xff).toByte(), (buttons shr 8 and 0xff).toByte(), hat.toByte(),
            axes[0], axes[1], axes[2], axes[3]))
    }

    private fun send(id: Int, data: ByteArray) {
        val h = hid ?: return
        val d = host ?: return
        executor.execute { runCatching { h.sendReport(d, id, data) }.onFailure { Log.w("Hid", "send: ${it.message}") } }
    }

    companion object {
        const val KEYBOARD = 1
        const val CONSUMER = 2
        const val GAMEPAD = 3
        const val MOD_SHIFT = 0x02
        private const val HAT_NONE: Byte = 8

        // Keyboard usages
        const val KEY_ENTER = 0x28
        const val KEY_ESC = 0x29
        const val KEY_BACKSPACE = 0x2A
        const val KEY_SPACE = 0x2C
        const val KEY_RIGHT = 0x4F
        const val KEY_LEFT = 0x50
        const val KEY_DOWN = 0x51
        const val KEY_UP = 0x52

        // Consumer usages (Android maps these to HOME, BACK, MENU, volume and media keys)
        const val CC_HOME = 0x223
        const val CC_BACK = 0x224
        const val CC_MENU = 0x40
        const val CC_VOL_UP = 0xE9
        const val CC_VOL_DOWN = 0xEA
        const val CC_MUTE = 0xE2
        const val CC_PLAY_PAUSE = 0xCD
        const val CC_NEXT = 0xB5
        const val CC_PREV = 0xB6

        // Game pad buttons as Linux numbers them for a "Game Pad" collection: 1 A, 2 B, 4 X, 5 Y,
        // 7 L1, 8 R1, 11 Select, 12 Start, 13 Mode (Android: BUTTON_A, _B, _X, _Y, _L1, _R1, _SELECT, _START, _MODE).
        const val BTN_A = 1
        const val BTN_B = 2
        const val BTN_X = 4
        const val BTN_Y = 5
        const val BTN_L1 = 7
        const val BTN_R1 = 8
        const val BTN_SELECT = 11
        const val BTN_START = 12
        const val BTN_MODE = 13

        fun charUsage(c: Char): Pair<Int, Boolean>? = when (c) {
            in 'a'..'z' -> (0x04 + (c - 'a')) to false
            in 'A'..'Z' -> (0x04 + (c - 'A')) to true
            in '1'..'9' -> (0x1E + (c - '1')) to false
            '0' -> 0x27 to false
            ' ' -> KEY_SPACE to false
            '\n' -> KEY_ENTER to false
            '-' -> 0x2D to false; '_' -> 0x2D to true
            '=' -> 0x2E to false; '+' -> 0x2E to true
            '.' -> 0x37 to false; '>' -> 0x37 to true
            ',' -> 0x36 to false; '<' -> 0x36 to true
            '/' -> 0x38 to false; '?' -> 0x38 to true
            ';' -> 0x33 to false; ':' -> 0x33 to true
            '\'' -> 0x34 to false; '"' -> 0x34 to true
            '!' -> 0x1E to true; '@' -> 0x1F to true; '#' -> 0x20 to true; '$' -> 0x21 to true
            '%' -> 0x22 to true; '&' -> 0x24 to true; '*' -> 0x25 to true; '(' -> 0x26 to true; ')' -> 0x27 to true
            else -> null
        }

        val DESCRIPTOR: ByteArray = intArrayOf(
            // Keyboard, report 1: modifiers, reserved, 6 keys
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, KEYBOARD,
            0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
            0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
            0xC0,
            // Consumer control, report 2: one 16-bit usage
            0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, CONSUMER,
            0x15, 0x00, 0x26, 0xFF, 0x03, 0x19, 0x00, 0x2A, 0xFF, 0x03, 0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
            0xC0,
            // Game pad, report 3: 16 buttons, hat (4 bits + 4 padding), X Y Z Rz
            0x05, 0x01, 0x09, 0x05, 0xA1, 0x01, 0x85, GAMEPAD,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x10, 0x81, 0x02,
            0x05, 0x01, 0x09, 0x39, 0x15, 0x00, 0x25, 0x07, 0x35, 0x00, 0x46, 0x3B, 0x01, 0x65, 0x14,
            0x75, 0x04, 0x95, 0x01, 0x81, 0x42,
            0x65, 0x00, 0x75, 0x04, 0x95, 0x01, 0x81, 0x01,
            0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35, 0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x04, 0x81, 0x02,
            0xC0,
        ).map { it.toByte() }.toByteArray()
    }
}
