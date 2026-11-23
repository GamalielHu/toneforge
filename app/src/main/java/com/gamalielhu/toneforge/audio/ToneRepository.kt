package com.gamalielhu.toneforge.audio

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Fuente desacoplada de tonos (síntesis local, Freesound, archivos, URLs…). */
interface AudioSourceProvider {
    val name: String
    suspend fun search(query: String, kind: ToneKind, limit: Int): List<ToneItem>
}

interface ToneRepository {
    suspend fun search(query: String, kind: ToneKind, limit: Int = 20): List<ToneItem>
    /** Deja el audio en un archivo reproducible (WAV para los sintetizados). */
    suspend fun render(item: ToneItem, outDir: File): File
}

class SynthProvider(private val synth: ToneSynth = ToneSynth()) : AudioSourceProvider {
    override val name = "Síntesis local"
    override suspend fun search(query: String, kind: ToneKind, limit: Int) =
        synth.suggest(query, kind, limit)
}

/** Género principal de un artista según la API pública de iTunes (sin clave). null si no hay red o no lo encuentra. */
suspend fun generoItunes(artista: String): String? = withContext(Dispatchers.IO) {
    val url = URL("https://itunes.apple.com/search?entity=musicArtist&limit=1&term=" + URLEncoder.encode(artista.trim(), "UTF-8"))
    val con = url.openConnection() as HttpURLConnection
    con.connectTimeout = 5000; con.readTimeout = 5000
    try {
        if (con.responseCode != 200) return@withContext null
        val json = con.inputStream.bufferedReader().readText()
        Regex("\"primaryGenreName\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
    } finally { con.disconnect() }
}

class DefaultToneRepository(
    private val providers: List<AudioSourceProvider> = listOf(StyleProvider(generoRemoto = ::generoItunes), SynthProvider()),
    private val synth: ToneSynth = ToneSynth(),
    private val composer: StyleComposer = StyleComposer(),
) : ToneRepository {

    override suspend fun search(query: String, kind: ToneKind, limit: Int): List<ToneItem> =
        coroutineScope {
            providers.map { p -> async { runCatching { p.search(query, kind, limit) }.getOrDefault(emptyList()) } }
                .awaitAll().flatten()
                // Si reconoció un artista o género, no mezcla los efectos sueltos.
                .let { todos -> if (todos.any { it.source == ToneSource.COMPUESTO }) todos.filter { it.source == ToneSource.COMPUESTO } else todos }
                .distinctBy { it.id }
                .sortedByDescending { it.score }
                .take(limit)
        }

    override suspend fun render(item: ToneItem, outDir: File): File {
        item.composicion?.let { c ->
            val out = File(outDir, "${item.id}.wav")
            if (!out.exists()) composer.writeWav(c, out)
            return out
        }
        item.spec?.let { spec ->
            val out = File(outDir, "${item.id}.wav")
            if (!out.exists()) synth.writeWav(spec, out)
            return out
        }
        val local = item.uri?.let(::File)
        if (local != null && local.exists()) return local
        error("Fuente ${item.source} aún no soportada")
    }
}
