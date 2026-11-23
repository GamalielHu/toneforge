package com.gamalielhu.toneforge.audio

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class Wave { SINE, TRIANGLE, SQUARE, SAW, NOISE }

enum class Pattern(val label: String) {
    CAMPANA("Campana"), PULSO("Pulso"), ACORDE("Acorde"), ARPEGIO("Arpegio"),
    SWEEP("Barrido"), PERCUSION("Percusión"), BAJO808("Bajo 808"),
}

/** Receta de un tono sintetizado: se guarda en el ToneItem y se puede regenerar. */
data class ToneSpec(
    val pattern: Pattern,
    val wave: Wave,
    val rootHz: Double,
    val intervals: List<Int>,      // semitonos sobre la raíz
    val bpm: Int,
    val durationMs: Int,
    val fadeInMs: Int = 5,
    val fadeOutMs: Int = 150,
    val brightness: Float = 0.7f,  // 0 = apagado, 1 = brillante
    val gain: Float = 0.9f,
    val seed: Int = 0,
)

/** Motor de síntesis procedural: descripción -> variantes rankeadas -> PCM 16 bit -> WAV. */
class ToneSynth(private val sampleRate: Int = 44_100) {

    private data class Trait(val pattern: Pattern? = null, val wave: Wave? = null, val octave: Int = 0,
                             val minor: Boolean? = null, val length: Float = 1f, val bpm: Int = 0,
                             val bright: Float = 0f, val soft: Boolean = false)

    private val keywords: Map<String, Trait> = mapOf(
        "futur" to Trait(Pattern.SWEEP, Wave.SQUARE, octave = 1, bright = 0.3f),
        "digital" to Trait(Pattern.PULSO, Wave.SQUARE, octave = 1),
        "alerta" to Trait(Pattern.PULSO, Wave.SQUARE, octave = 1, bpm = 30),
        "alarma" to Trait(Pattern.PULSO, Wave.SAW, octave = 1, bpm = 40),
        "laser" to Trait(Pattern.SWEEP, Wave.SAW, octave = 1),
        "sci" to Trait(Pattern.SWEEP, Wave.TRIANGLE),
        "marimba" to Trait(Pattern.ARPEGIO, Wave.SINE),
        "xilof" to Trait(Pattern.ARPEGIO, Wave.SINE, octave = 1),
        "alegre" to Trait(Pattern.ARPEGIO, minor = false, bpm = 20, bright = 0.2f),
        "feliz" to Trait(Pattern.ARPEGIO, minor = false, bpm = 20),
        "minimal" to Trait(Pattern.CAMPANA, Wave.SINE, length = 0.6f, soft = true),
        "sutil" to Trait(Pattern.CAMPANA, Wave.SINE, length = 0.6f, soft = true, bright = -0.3f),
        "suave" to Trait(Pattern.CAMPANA, Wave.SINE, soft = true, bright = -0.3f),
        "discret" to Trait(Pattern.CAMPANA, Wave.SINE, length = 0.6f, soft = true),
        "campana" to Trait(Pattern.CAMPANA),
        "timbre" to Trait(Pattern.CAMPANA, octave = 1),
        "gota" to Trait(Pattern.CAMPANA, Wave.SINE, octave = 1, length = 0.5f),
        "acorde" to Trait(Pattern.ACORDE, Wave.TRIANGLE),
        "piano" to Trait(Pattern.ACORDE, Wave.TRIANGLE),
        "pad" to Trait(Pattern.ACORDE, Wave.SAW, bright = -0.4f),
        "percus" to Trait(Pattern.PERCUSION, Wave.NOISE),
        "tambor" to Trait(Pattern.PERCUSION, Wave.NOISE, octave = -1),
        "click" to Trait(Pattern.PERCUSION, Wave.NOISE, length = 0.4f),
        "bajo" to Trait(Pattern.BAJO808, Wave.SINE, octave = -2),
        "808" to Trait(Pattern.BAJO808, Wave.SINE, octave = -2),
        "grave" to Trait(Pattern.BAJO808, octave = -1, bright = -0.3f),
        "triste" to Trait(minor = true, bpm = -20),
        "oscur" to Trait(minor = true, octave = -1, bright = -0.3f),
        "epic" to Trait(Pattern.ACORDE, Wave.SAW, minor = true),
        "retro" to Trait(Pattern.ARPEGIO, Wave.SQUARE),
        "8 bit" to Trait(Pattern.ARPEGIO, Wave.SQUARE, octave = 1),
        "8bit" to Trait(Pattern.ARPEGIO, Wave.SQUARE, octave = 1),
        "cort" to Trait(length = 0.5f),
        "breve" to Trait(length = 0.5f),
        "larg" to Trait(length = 1.6f),
        "rapid" to Trait(bpm = 40),
        "lent" to Trait(bpm = -30),
        "brillante" to Trait(bright = 0.3f),
    )

    private val notas = listOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")

    fun suggest(query: String, kind: ToneKind, limit: Int = 12): List<ToneItem> {
        val q = query.lowercase().trim()
        val hits = keywords.filterKeys { q.contains(it) }.values
        val patternVotes = Pattern.entries.associateWith { p -> hits.count { it.pattern == p }.toFloat() }
        val waveVote = hits.mapNotNull { it.wave }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val minor = hits.mapNotNull { it.minor }.lastOrNull() ?: false
        val octave = hits.sumOf { it.octave }.coerceIn(-2, 2)
        val length = hits.map { it.length }.fold(1f) { a, b -> a * b }.coerceIn(0.3f, 2f)
        val bpmDelta = hits.sumOf { it.bpm }
        val bright = (0.65f + hits.sumOf { it.bright.toDouble() }.toFloat()).coerceIn(0.1f, 1f)
        val soft = hits.any { it.soft }

        val baseMs = if (kind == ToneKind.LLAMADA) 15_000 else 1_400
        val durMs = (baseMs * length).toInt().coerceIn(300, 30_000)
        val scale = if (minor) listOf(0, 3, 7, 10, 12) else listOf(0, 4, 7, 11, 12)
        val seed0 = q.hashCode()
        val rnd = Random(seed0)

        val ranked = Pattern.entries.sortedByDescending { patternVotes[it]!! + rnd.nextFloat() * 0.1f }
        val out = mutableListOf<ToneItem>()
        val roots = listOf(0, 5, 7, -3, 2, 9)
        for ((pi, p) in ranked.withIndex()) {
            val variants = if (pi == 0) 4 else if (patternVotes[p]!! > 0) 3 else 1
            for (v in 0 until variants) {
                val semis = roots[v % roots.size]
                val baseOct = when (p) { Pattern.BAJO808 -> -2; Pattern.PERCUSION -> -1; else -> 0 }
                val rootHz = 523.25 * 2.0.pow((semis + 12 * (octave + baseOct)) / 12.0)
                val wave = if (p == Pattern.PERCUSION) Wave.NOISE
                    else waveVote ?: defaultWave(p)
                val bpm = ((if (kind == ToneKind.LLAMADA) 110 else 140) + bpmDelta + v * 8).coerceIn(50, 220)
                val ivals = when (p) {
                    Pattern.ACORDE -> scale.take(3 + v % 2)
                    Pattern.ARPEGIO -> if (v % 2 == 0) scale else scale.reversed()
                    Pattern.CAMPANA -> if (v % 2 == 0) listOf(scale[2], scale[0]) else listOf(scale[0], scale[1], scale[2])
                    else -> scale.take(2)
                }
                val spec = ToneSpec(
                    pattern = p, wave = wave, rootHz = rootHz, intervals = ivals, bpm = bpm,
                    durationMs = durMs, fadeOutMs = min(400, durMs / 5),
                    brightness = bright, gain = if (soft) 0.6f else 0.9f, seed = seed0 + v,
                )
                val score = patternVotes[p]!! * 10f + (if (wave == waveVote) 2f else 0f) - v * 0.5f - pi * 0.2f
                val nota = notas[((semis % 12) + 12) % 12]
                out += ToneItem(
                    id = "syn-${p.name}-${spec.hashCode().toUInt().toString(16)}",
                    title = "${p.label} ${if (minor) "menor" else "mayor"} · $nota" + if (v > 0) " (v${v + 1})" else "",
                    kind = kind, source = ToneSource.SINTETIZADO, durationMs = durMs, score = score,
                    tags = listOf(p.label.lowercase(), wave.name.lowercase()), spec = spec,
                    license = "Generado en el equipo",
                )
            }
        }
        return out.sortedByDescending { it.score }.take(limit)
    }

    private fun defaultWave(p: Pattern) = when (p) {
        Pattern.ACORDE -> Wave.TRIANGLE
        Pattern.PULSO, Pattern.SWEEP -> Wave.SQUARE
        else -> Wave.SINE
    }

    // ---------- Render ----------

    fun render(spec: ToneSpec): ShortArray {
        val n = (spec.durationMs.toLong() * sampleRate / 1000).toInt()
        val buf = FloatArray(n)
        val rnd = Random(spec.seed)
        val beat = 60.0 / spec.bpm
        val freqs = spec.intervals.map { spec.rootHz * 2.0.pow(it / 12.0) }
        val total = spec.durationMs / 1000.0

        // Un "compás" del patrón; se repite hasta llenar la duración (en llamadas con un silencio entre ciclos).
        fun bar(t0: Double): Double = when (spec.pattern) {
            Pattern.CAMPANA -> {
                freqs.forEachIndexed { i, f ->
                    voice(buf, t0 + i * beat * 0.75, 1.6, { f }, spec.wave, listOf(1.0 to 1.0, 2.76 to 0.45, 5.4 to 0.2), 0.55, rnd)
                }
                freqs.size * beat * 0.75 + 1.2
            }
            Pattern.PULSO -> {
                repeat(3) { i -> voice(buf, t0 + i * beat * 0.5, beat * 0.3, { freqs[0] }, spec.wave, listOf(1.0 to 1.0), 5.0, rnd, release = 0.01) }
                beat * 3
            }
            Pattern.ACORDE -> {
                freqs.forEach { f -> voice(buf, t0, beat * 4, { f }, spec.wave, listOf(1.0 to 1.0, 2.0 to 0.3), 1.2, rnd, attack = 0.02) }
                beat * 4
            }
            Pattern.ARPEGIO -> {
                val step = beat / 2
                freqs.forEachIndexed { i, f ->
                    voice(buf, t0 + i * step, 0.6, { f }, spec.wave, listOf(1.0 to 1.0, 4.0 to 0.35, 10.0 to 0.08), 0.22, rnd)
                }
                freqs.size * step + beat
            }
            Pattern.SWEEP -> {
                val len = 0.35
                repeat(2) { i ->
                    voice(buf, t0 + i * len * 1.3, len, { t -> freqs[0] * (1 + 3 * t / len) }, spec.wave, listOf(1.0 to 1.0), 4.0, rnd, release = 0.03)
                }
                len * 2.6 + beat
            }
            Pattern.PERCUSION -> {
                val hits = listOf(0.0, 0.5, 0.75, 1.5)
                hits.forEachIndexed { i, h ->
                    voice(buf, t0 + h * beat, 0.25, { freqs[0] }, Wave.NOISE, listOf(1.0 to 1.0), if (i % 2 == 0) 0.06 else 0.03, rnd)
                    voice(buf, t0 + h * beat, 0.2, { t -> freqs[0] * (1 + 1.5 * exp(-t / 0.02)) }, Wave.SINE, listOf(1.0 to 0.8), 0.08, rnd)
                }
                beat * 2
            }
            Pattern.BAJO808 -> {
                listOf(0.0, 0.75, 1.5).forEach { h ->
                    voice(buf, t0 + h * beat, beat * 1.2, { t -> freqs[0] * (1 + 2 * exp(-t / 0.03)) }, Wave.SINE, listOf(1.0 to 1.0, 2.0 to 0.15), 0.5, rnd)
                }
                beat * 2.5
            }
        }

        var t = 0.0
        while (t < total) {
            t += bar(t)
        }

        lowPass(buf, spec.brightness)
        val peak = buf.maxOf { abs(it) }.takeIf { it > 0f } ?: 1f
        val g = spec.gain / peak
        applyFades(buf, spec.fadeInMs, spec.fadeOutMs)
        return ShortArray(n) { i -> (buf[i] * g * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun waveAt(w: Wave, phase: Double, rnd: Random): Double {
        val p = phase - floor(phase)
        return when (w) {
            Wave.SINE -> sin(2 * PI * p)
            Wave.TRIANGLE -> 4 * abs(p - 0.5) - 1
            Wave.SQUARE -> if (p < 0.5) 0.6 else -0.6
            Wave.SAW -> (2 * p - 1) * 0.6
            Wave.NOISE -> rnd.nextDouble(-1.0, 1.0)
        }
    }

    /** Suma al buffer una nota con parciales (multiplicador, amplitud) y decaimiento exponencial. */
    private fun voice(
        buf: FloatArray, start: Double, dur: Double, freq: (Double) -> Double, wave: Wave,
        partials: List<Pair<Double, Double>>, decayTau: Double, rnd: Random,
        attack: Double = 0.004, release: Double = 0.05,
    ) {
        val s0 = (start * sampleRate).toInt()
        if (s0 >= buf.size) return
        val len = min((dur * sampleRate).toInt(), buf.size - s0)
        val phases = DoubleArray(partials.size)
        val dt = 1.0 / sampleRate
        for (i in 0 until len) {
            val t = i * dt
            val f = freq(t)
            var env = exp(-t / decayTau)
            if (t < attack) env *= t / attack
            val tail = dur - t
            if (tail < release) env *= max(0.0, tail / release)
            var s = 0.0
            for (k in partials.indices) {
                val (mul, amp) = partials[k]
                val pf = f * mul
                if (pf > sampleRate / 2.2) continue
                phases[k] += pf * dt
                // Parciales altos decaen más rápido (más natural en campanas/marimba).
                s += amp * waveAt(wave, phases[k], rnd) * if (k == 0) 1.0 else exp(-t * k * 3)
            }
            buf[s0 + i] += (s * env * 0.4).toFloat()
        }
    }

    private fun lowPass(buf: FloatArray, brightness: Float) {
        val cutoff = 800.0 + brightness * 11_000.0
        val a = (1 - exp(-2 * PI * cutoff / sampleRate)).toFloat()
        var y = 0f
        for (i in buf.indices) { y += a * (buf[i] - y); buf[i] = y }
    }

    private fun applyFades(buf: FloatArray, inMs: Int, outMs: Int) {
        val fi = min(buf.size, inMs * sampleRate / 1000)
        for (i in 0 until fi) buf[i] *= i.toFloat() / fi
        val fo = min(buf.size, outMs * sampleRate / 1000)
        for (i in 0 until fo) buf[buf.size - 1 - i] *= i.toFloat() / fo
    }

    fun writeWav(spec: ToneSpec, out: File) = writeWav(render(spec), out)

    fun writeWav(pcm: ShortArray, out: File) {
        val dataLen = pcm.size * 2
        DataOutputStream(FileOutputStream(out).buffered()).use { o ->
            fun le32(v: Int) { o.write(v and 0xff); o.write(v shr 8 and 0xff); o.write(v shr 16 and 0xff); o.write(v shr 24 and 0xff) }
            fun le16(v: Int) { o.write(v and 0xff); o.write(v shr 8 and 0xff) }
            o.writeBytes("RIFF"); le32(36 + dataLen); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(dataLen)
            for (s in pcm) le16(s.toInt())
        }
    }
}
