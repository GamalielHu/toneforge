package com.gamalielhu.toneforge.audio

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

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

class DefaultToneRepository(
    private val providers: List<AudioSourceProvider> = listOf(SynthProvider()),
    private val synth: ToneSynth = ToneSynth(),
) : ToneRepository {

    override suspend fun search(query: String, kind: ToneKind, limit: Int): List<ToneItem> =
        coroutineScope {
            providers.map { p -> async { runCatching { p.search(query, kind, limit) }.getOrDefault(emptyList()) } }
                .awaitAll().flatten()
                .distinctBy { it.id }
                .sortedByDescending { it.score }
                .take(limit)
        }

    override suspend fun render(item: ToneItem, outDir: File): File {
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
