package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.PairedTv
import com.ajit.tvhero.remote.TvClient
import com.ajit.tvhero.remote.TvException
import kotlinx.coroutines.launch

/** First run (or after forgetting a TV): find the TV on the Wi-Fi and pair with the code it shows. */
@Composable
fun ConnectScreen(state: AppState) {
    val found by state.discovery.found.collectAsState()
    val paired = remember { state.store.tvs() }
    var manual by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf<Pair<String, String>?>(null) }   // host, name

    pairing?.let { (host, name) ->
        PairScreen(state, host, name) { pairing = null }
        return
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("tv-hero", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light)
        Text("Control your TV from this phone. Your phone and TV need to be on the same Wi-Fi.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (paired.isNotEmpty()) {
            Section("Paired TVs")
            paired.forEach { tv -> TvRow(tv.name, tv.host, "Open") { state.select(tv) } }
        }

        Section("TVs on this Wi-Fi")
        if (found.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp).height(20.dp))
                Text("Looking for tv-hero TVs…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        found.forEach { tv -> TvRow(tv.name, tv.host, "Pair") { pairing = tv.host to tv.name } }

        Section("Or enter the TV's address")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(manual, { manual = it.trim() }, singleLine = true, label = { Text("e.g. 192.168.1.50") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(1f))
            TextButton(enabled = manual.isNotEmpty(), onClick = { pairing = manual to "TV" }) { Text("Pair") }
        }
    }
}

@Composable
private fun TvRow(name: String, host: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Tv, null, Modifier.padding(end = 16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(action, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun Section(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PairScreen(state: AppState, host: String, tvName: String, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("starting") }         // starting | code | working | error
    var salt by remember { mutableStateOf("") }
    var passwordSet by remember { mutableStateOf(true) }
    var code by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(state.store.deviceName) }
    var error by remember { mutableStateOf<String?>(null) }
    val client = remember { TvClient(host) }

    fun start() {
        step = "starting"; error = null
        scope.launch {
            try {
                val r = client.pairStart(name)
                salt = r.getString("salt")
                passwordSet = r.optBoolean("password_set", true)
                step = "code"
            } catch (e: TvException) {
                error = e.message; step = "error"
            }
        }
    }
    LaunchedEffect(Unit) { start() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Pair with $tvName", style = MaterialTheme.typography.headlineSmall)
        when (step) {
            "starting" -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp)); Text("Asking the TV for a code…")
            }
            "error" -> {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
                Button(onClick = { start() }) { Text("Try Again") }
            }
            else -> {
                Text("A 6-digit code is showing on the TV now.")
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    label = { Text("Code on TV") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth())
                if (passwordSet) {
                    OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("Parent password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Create the parent password. It's needed to unblock apps, approve new apps and change protection, from any phone.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("New parent password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pw2, { pw2 = it }, singleLine = true, label = { Text("Repeat password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("This phone's name") },
                    modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val valid = code.length == 6 && pw.length >= 4 && (passwordSet || pw == pw2)
                if (!passwordSet && pw.isNotEmpty() && pw.length < 4) Text("Use at least 4 characters.", color = MaterialTheme.colorScheme.error)
                if (!passwordSet && pw2.isNotEmpty() && pw != pw2) Text("The passwords don't match.", color = MaterialTheme.colorScheme.error)
                Button(enabled = valid && step != "working", modifier = Modifier.fillMaxWidth(), onClick = {
                    step = "working"; error = null
                    scope.launch {
                        try {
                            val (deviceId, secret) = client.pairFinish(name, code, pw, salt)
                            val info = client.info()
                            state.store.deviceName = name
                            state.passwordWorked(pw)
                            state.paired(PairedTv(info.optString("model", tvName) + "@" + host,
                                "tv-hero · " + info.optString("model", tvName), host, deviceId, secret))
                        } catch (e: TvException) {
                            error = e.message; step = "code"
                            if (e.status == 409) start()
                        }
                    }
                }) { Text(if (step == "working") "Pairing…" else "Pair") }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}
