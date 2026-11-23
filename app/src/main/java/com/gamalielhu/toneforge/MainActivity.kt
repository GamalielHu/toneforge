package com.gamalielhu.toneforge

import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamalielhu.toneforge.audio.DefaultToneRepository
import com.gamalielhu.toneforge.audio.ToneItem
import com.gamalielhu.toneforge.audio.ToneKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val repo = DefaultToneRepository()
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Pantalla() } }
    }

    override fun onStop() { super.onStop(); detener() }

    private fun detener() { player?.release(); player = null }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Pantalla() {
        var consulta by remember { mutableStateOf("") }
        var tipo by remember { mutableStateOf(ToneKind.NOTIFICACION) }
        var resultados by remember { mutableStateOf(emptyList<ToneItem>()) }
        var sonando by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        fun buscar() = scope.launch {
            resultados = withContext(Dispatchers.Default) { repo.search(consulta, tipo) }
        }

        fun alternar(item: ToneItem) = scope.launch {
            detener()
            if (sonando == item.id) { sonando = null; return@launch }
            runCatching {
                val f = withContext(Dispatchers.Default) { repo.render(item, cacheDir) }
                player = MediaPlayer().apply {
                    setDataSource(f.absolutePath)
                    setOnCompletionListener { sonando = null }
                    prepare(); start()
                }
                sonando = item.id
            }.onFailure { error = it.message }
        }

        Scaffold(topBar = { TopAppBar(title = { Text("ToneForge") }) }) { pad ->
            Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
                OutlinedTextField(
                    value = consulta, onValueChange = { consulta = it },
                    label = { Text("Describe el tono (ej. marimba alegre)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToneKind.entries.forEach { k ->
                        FilterChip(selected = tipo == k, onClick = { tipo = k }, label = { Text(k.label) })
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { buscar() }) { Text("Buscar") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(resultados, key = { it.id }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                                    Text("%.1f s · %s".format(item.durationMs / 1000f, item.license),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { alternar(item) }) {
                                    Text(if (sonando == item.id) "■ Parar" else "▶ Oír")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
