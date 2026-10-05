package com.example.freezer

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

data class AppItem(val label: String, val pkg: String, val frozen: Boolean)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Screen() } }
    }

    private fun shizukuReady() = Shizuku.pingBinder() &&
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    private fun run(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText() +
            p.errorStream.bufferedReader().readText()
        p.waitFor()
        out.trim()
    } catch (e: Exception) {
        "Error: $e"
    }

    private fun loadApps(): List<AppItem> =
        packageManager.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != packageName }
            .map { AppItem(it.loadLabel(packageManager).toString(), it.packageName, !it.enabled) }
            .sortedBy { it.label.lowercase() }

    @Composable
    fun Screen() {
        var ready by remember { mutableStateOf(shizukuReady()) }
        var message by remember { mutableStateOf("") }
        val apps = remember { mutableStateListOf<AppItem>().apply { addAll(loadApps()) } }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                if (ready) "Shizuku: connected" else "Shizuku: not ready",
                style = MaterialTheme.typography.titleMedium
            )
            Row {
                Button(onClick = {
                    if (Shizuku.pingBinder() &&
                        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
                    ) Shizuku.requestPermission(1)
                }) { Text("Grant") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { ready = shizukuReady() }) { Text("Refresh") }
            }
            if (message.isNotEmpty()) Text(message)

            LazyColumn {
                items(apps.toList()) { app ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label)
                            Text(app.pkg, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = {
                            if (!shizukuReady()) {
                                message = "Shizuku isn't ready"
                            } else {
                                Thread {
                                    val cmd = if (app.frozen) "pm enable ${app.pkg}"
                                    else "am force-stop ${app.pkg}; pm disable-user --user 0 ${app.pkg}"
                                    val out = run(cmd)
                                    runOnUiThread {
                                        val i = apps.indexOfFirst { it.pkg == app.pkg }
                                        if (i >= 0) apps[i] = app.copy(frozen = !app.frozen)
                                        message = out
                                    }
                                }.start()
                            }
                        }) { Text(if (app.frozen) "Unfreeze" else "Freeze") }
                    }
                }
            }
        }
    }
}
