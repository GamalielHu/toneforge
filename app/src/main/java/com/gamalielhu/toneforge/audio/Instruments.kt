package com.gamalielhu.toneforge.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

enum class Instrumento(val nombre: String) {
    PIANO("piano"), PIANO_ELECTRICO("piano eléctrico"), ORGANO("órgano"),
    GUITARRA("guitarra"), REQUINTO("requinto"), CHARANGO("charango"), GUITARRA_ELECTRICA("guitarra eléctrica"),
    CUERDAS("cuerdas"), VIOLIN("violín"), TROMPETA("trompeta"), METALES("metales"), SAXO("saxo"),
    FLAUTA("flauta"), ZAMPONA("zampoña"), ACORDEON("acordeón"), MARIMBA("marimba"), CAMPANITA("campanita"),
    BAJO("bajo"), GUITARRON("guitarrón"), BAJO_SINTE("bajo sintetizado"), BAJO_808("808"),
    SINTE("sintetizador"), PAD("pad"),
}

enum class Golpe { BOMBO, BOMBO_808, CAJA, PALMAS, HIHAT, HIHAT_ABIERTO, PLATILLO, CLAVE, BONGO_AGUDO, BONGO_GRAVE, GUIRO, MARACA, CONGA, CENCERRO, TIMBAL, RIM, MATRACA }

/** Sintetiza notas e golpes sumándolos en un buffer mono. Todo es determinista con el Random recibido. */
class Instrumentos(private val sr: Int) {

    private class Voz(
        val ondas: List<Triple<Char, Double, Double>>, // forma (s=seno t=triángulo q=cuadrada w=sierra), multiplicador, amplitud
        val ataque: Double, val caida: Double, val sostenido: Double, val suelta: Double,
        val corteIni: Double, val corteFin: Double, val corteTau: Double, // en múltiplos de f
        val vibHz: Double = 0.0, val vibSemi: Double = 0.0, val vibRetardo: Double = 0.3,
        val soplo: Double = 0.0, val desafinar: Double = 0.0, val satura: Double = 0.0, val resonancia: Double = 0.7,
    )

    private val voces = mapOf(
        Instrumento.CUERDAS to Voz(listOf(Triple('w', 1.0, 0.5), Triple('w', 1.0, 0.5), Triple('w', 2.0, 0.15)), 0.22, 0.6, 0.85, 0.45, 3.5, 3.5, 1.0, 5.5, 0.12, 0.35, desafinar = 9.0),
        Instrumento.VIOLIN to Voz(listOf(Triple('w', 1.0, 0.8), Triple('w', 1.0, 0.3)), 0.07, 0.4, 0.9, 0.2, 5.0, 4.0, 0.3, 6.0, 0.25, 0.2, soplo = 0.015, desafinar = 5.0),
        Instrumento.TROMPETA to Voz(listOf(Triple('w', 1.0, 1.0)), 0.035, 0.3, 0.85, 0.08, 1.6, 7.0, 0.06, 5.2, 0.13, 0.25, soplo = 0.02, resonancia = 1.1),
        Instrumento.METALES to Voz(listOf(Triple('w', 1.0, 0.6), Triple('w', 1.0, 0.6)), 0.03, 0.25, 0.75, 0.1, 1.5, 6.0, 0.05, 0.0, 0.0, 0.0, desafinar = 12.0, resonancia = 1.0),
        Instrumento.SAXO to Voz(listOf(Triple('q', 1.0, 0.6), Triple('w', 1.0, 0.5)), 0.04, 0.3, 0.85, 0.12, 2.5, 4.5, 0.1, 5.0, 0.15, 0.3, soplo = 0.05, satura = 0.4),
        Instrumento.FLAUTA to Voz(listOf(Triple('s', 1.0, 1.0), Triple('s', 2.0, 0.12), Triple('s', 3.0, 0.05)), 0.05, 0.4, 0.85, 0.15, 6.0, 6.0, 1.0, 5.0, 0.12, 0.25, soplo = 0.06),
        Instrumento.ZAMPONA to Voz(listOf(Triple('s', 1.0, 1.0), Triple('t', 2.0, 0.15)), 0.03, 0.5, 0.75, 0.12, 6.0, 6.0, 1.0, 4.5, 0.18, 0.2, soplo = 0.22),
        Instrumento.ACORDEON to Voz(listOf(Triple('q', 1.0, 0.5), Triple('q', 1.0, 0.45), Triple('w', 2.0, 0.2)), 0.025, 0.3, 0.9, 0.08, 5.0, 5.0, 1.0, 0.0, 0.0, 0.0, desafinar = 14.0),
        Instrumento.ORGANO to Voz(listOf(Triple('s', 1.0, 1.0), Triple('s', 2.0, 0.6), Triple('s', 3.0, 0.4), Triple('s', 4.0, 0.25)), 0.01, 0.2, 1.0, 0.05, 20.0, 20.0, 1.0),
        Instrumento.SINTE to Voz(listOf(Triple('q', 1.0, 0.55), Triple('w', 1.0, 0.55)), 0.01, 0.25, 0.7, 0.1, 6.0, 3.0, 0.25, 5.5, 0.08, 0.3, desafinar = 10.0, resonancia = 1.2),
        Instrumento.PAD to Voz(listOf(Triple('w', 1.0, 0.4), Triple('w', 1.0, 0.4), Triple('w', 0.5, 0.3)), 0.35, 1.0, 0.85, 0.6, 2.5, 2.5, 1.0, 0.0, 0.0, 0.0, desafinar = 14.0),
        Instrumento.BAJO_SINTE to Voz(listOf(Triple('w', 1.0, 0.8), Triple('q', 0.5, 0.4)), 0.005, 0.25, 0.6, 0.06, 7.0, 2.0, 0.08, resonancia = 1.6),
        Instrumento.GUITARRA_ELECTRICA to Voz(listOf(Triple('w', 1.0, 0.6), Triple('w', 1.5, 0.4)), 0.005, 0.6, 0.6, 0.1, 6.0, 3.0, 0.3, satura = 3.0, desafinar = 6.0),
    )

    private fun hz(midi: Double) = 440.0 * 2.0.pow((midi - 69) / 12)

    /** Agrega una nota. [dur] es lo que dura apretada; la suelta suena después. */
    fun nota(buf: FloatArray, t0: Double, dur: Double, midi: Double, vel: Double, inst: Instrumento, rnd: Random) {
        val f = hz(midi)
        when (inst) {
            Instrumento.GUITARRA -> cuerda(buf, t0, dur + 0.3, f, vel, 0.996, 0.55, rnd)
            Instrumento.REQUINTO -> cuerda(buf, t0, dur + 0.25, f, vel, 0.997, 0.8, rnd)
            Instrumento.CHARANGO -> cuerda(buf, t0, dur + 0.15, f, vel, 0.993, 0.9, rnd)
            Instrumento.PIANO -> piano(buf, t0, dur, f, vel)
            Instrumento.PIANO_ELECTRICO -> fm(buf, t0, dur, f, vel, 1.0, 2.2, 0.35, 1.6)
            Instrumento.CAMPANITA -> fm(buf, t0, dur, f, vel * 0.6, 3.5, 2.5, 0.5, 1.2)
            Instrumento.MARIMBA -> aditiva(buf, t0, min(dur + 0.4, 1.2), f, vel, listOf(1.0 to 1.0, 3.93 to 0.35, 9.2 to 0.1), 0.35)
            Instrumento.BAJO, Instrumento.GUITARRON -> bajo(buf, t0, dur, f, vel, inst == Instrumento.GUITARRON)
            Instrumento.BAJO_808 -> ochoCeroOcho(buf, t0, dur, f, vel)
            else -> sustractiva(buf, t0, dur, f, vel, voces.getValue(inst), rnd)
        }
    }

    private fun rango(t0: Double, largo: Double, buf: FloatArray): IntRange {
        val s0 = (t0 * sr).toInt().coerceAtLeast(0)
        val s1 = min(buf.size, s0 + (largo * sr).toInt())
        return s0 until s1
    }

    private fun adsr(t: Double, dur: Double, a: Double, d: Double, s: Double, r: Double): Double {
        val on = when {
            t < a -> t / a
            t < a + d -> 1 - (1 - s) * (t - a) / d
            else -> s
        }
        if (t <= dur) return on
        val nivel = if (dur < a) dur / a else if (dur < a + d) 1 - (1 - s) * (dur - a) / d else s
        return (nivel * (1 - (t - dur) / r)).coerceAtLeast(0.0)
    }

    /** Osciladores con polyBLEP + filtro de estado variable con envolvente: metales, cuerdas, sintes. */
    private fun sustractiva(buf: FloatArray, t0: Double, dur: Double, f: Double, vel: Double, v: Voz, rnd: Random) {
        val fases = DoubleArray(v.ondas.size) { rnd.nextDouble() }
        val desaf = DoubleArray(v.ondas.size) { i -> if (i == 0) 1.0 else 2.0.pow((if (i % 2 == 1) 1 else -1) * v.desafinar / 1200.0) }
        var lp = 0.0; var bp = 0.0
        val q = 1.0 / (0.5 + v.resonancia)
        val dt = 1.0 / sr
        val r = rango(t0, dur + v.suelta, buf)
        for (i in r) {
            val t = (i - r.first) * dt
            val env = adsr(t, dur, v.ataque, v.caida, v.sostenido, v.suelta)
            if (env <= 0 && t > dur) break
            val vib = if (t > v.vibRetardo) 2.0.pow(v.vibSemi * sin(2 * PI * v.vibHz * t) * min(1.0, (t - v.vibRetardo) * 2) / 12) else 1.0
            var x = 0.0
            for (k in v.ondas.indices) {
                val (forma, mul, amp) = v.ondas[k]
                val inc = f * mul * desaf[k] * vib * dt
                if (inc >= 0.45) continue
                fases[k] += inc; if (fases[k] >= 1) fases[k] -= 1.0
                x += amp * onda(forma, fases[k], inc)
            }
            x += v.soplo * rnd.nextDouble(-1.0, 1.0) * env
            if (v.satura > 0) x = tanh(x * (1 + v.satura)) / tanh(1 + v.satura)
            val corteMul = v.corteFin + (v.corteIni - v.corteFin) * exp(-t / v.corteTau)
            val fc = (f * corteMul * (0.6 + 0.4 * vel)).coerceIn(80.0, sr / 6.5)
            val g = 2 * sin(PI * fc / sr)
            val hp = x - lp - q * bp
            bp += g * hp; lp += g * bp
            buf[i] += (lp * env * vel * 0.35).toFloat()
        }
    }

    private fun blep(t: Double, dt: Double): Double = when {
        t < dt -> { val x = t / dt; x + x - x * x - 1 }
        t > 1 - dt -> { val x = (t - 1) / dt; x * x + x + x + 1 }
        else -> 0.0
    }

    private fun onda(forma: Char, p: Double, dt: Double): Double = when (forma) {
        's' -> sin(2 * PI * p)
        't' -> 4 * abs(p - 0.5) - 1
        'w' -> (2 * p - 1) - blep(p, dt)
        'q' -> (if (p < 0.5) 1.0 else -1.0) + blep(p, dt) - blep((p + 0.5) % 1.0, dt)
        else -> 0.0
    }

    /** Karplus-Strong con retardo fraccional (afinación exacta): guitarra, requinto, charango. */
    private fun cuerda(buf: FloatArray, t0: Double, largo: Double, f: Double, vel: Double, amort: Double, brillo: Double, rnd: Random) {
        val r = rango(t0, largo, buf)
        if (r.isEmpty()) return
        val n = r.last - r.first + 1
        val y = DoubleArray(n)
        val d = sr / f - 0.5
        val ini = min(n, ceil(d).toInt() + 2)
        var prev = 0.0
        for (i in 0 until ini) { val x = rnd.nextDouble(-1.0, 1.0); prev += brillo * (x - prev); y[i] = prev }
        val perdida = amort.pow(440.0 / f.coerceAtLeast(60.0)).coerceIn(0.97, 0.9995)
        for (i in ini until n) {
            val pos = i - d
            val i0 = floor(pos).toInt()
            val fr = pos - i0
            val a = if (i0 >= 0) y[i0] * (1 - fr) + y[min(i0 + 1, i - 1)] * fr else 0.0
            val b = if (i0 - 1 >= 0) y[i0 - 1] * (1 - fr) + y[i0] * fr else 0.0
            y[i] = perdida * 0.5 * (a + b)
        }
        val fade = (0.02 * sr).toInt()
        for (i in 0 until n) {
            val g = if (i > n - fade) (n - i).toDouble() / fade else 1.0
            buf[r.first + i] += (y[i] * vel * 0.5 * g).toFloat()
        }
    }

    private fun piano(buf: FloatArray, t0: Double, dur: Double, f: Double, vel: Double) {
        val sost = (2.5 * 220 / f).coerceIn(0.4, 3.0)
        val largo = dur + 0.25
        val r = rango(t0, largo, buf)
        val dt = 1.0 / sr
        val parciales = (1..8).filter { f * it < sr / 2.5 }
        for (i in r) {
            val t = (i - r.first) * dt
            val apagado = if (t > dur) exp(-(t - dur) / 0.06) else 1.0
            var x = 0.0
            for (k in parciales) {
                val fk = f * k * sqrt(1 + 0.0004 * k * k)
                x += sin(2 * PI * fk * t) / k.toDouble().pow(1.1 + (1 - vel) * 0.6) * exp(-t * k * 0.9 / sost)
            }
            val ataque = min(1.0, t / 0.002)
            buf[i] += (x * ataque * apagado * vel * 0.3 * exp(-t / (sost * 1.6))).toFloat()
        }
    }

    private fun fm(buf: FloatArray, t0: Double, dur: Double, f: Double, vel: Double, razon: Double, indice: Double, tauIndice: Double, tauAmp: Double) {
        val r = rango(t0, dur + 0.3, buf)
        val dt = 1.0 / sr
        for (i in r) {
            val t = (i - r.first) * dt
            val apagado = if (t > dur) exp(-(t - dur) / 0.08) else 1.0
            val idx = indice * exp(-t / tauIndice) * vel + 0.3
            val x = sin(2 * PI * f * t + idx * sin(2 * PI * f * razon * t))
            buf[i] += (x * min(1.0, t / 0.002) * exp(-t / tauAmp) * apagado * vel * 0.3).toFloat()
        }
    }

    private fun aditiva(buf: FloatArray, t0: Double, largo: Double, f: Double, vel: Double, parciales: List<Pair<Double, Double>>, tau: Double) {
        val r = rango(t0, largo, buf)
        val dt = 1.0 / sr
        for (i in r) {
            val t = (i - r.first) * dt
            var x = 0.0
            for ((k, p) in parciales.withIndex()) if (f * p.first < sr / 2.5) x += p.second * sin(2 * PI * f * p.first * t) * exp(-t * (1 + k * 4) / tau)
            buf[i] += (x * min(1.0, t / 0.002) * vel * 0.35).toFloat()
        }
    }

    private fun bajo(buf: FloatArray, t0: Double, dur: Double, f: Double, vel: Double, acustico: Boolean) {
        val r = rango(t0, dur + 0.08, buf)
        val dt = 1.0 / sr
        var lp = 0.0
        var fase = 0.0
        for (i in r) {
            val t = (i - r.first) * dt
            val apagado = if (t > dur) exp(-(t - dur) / 0.03) else 1.0
            fase += f * dt; if (fase >= 1) fase -= 1.0
            val sierra = 2 * fase - 1
            val fc = f * (if (acustico) 2.0 + 6 * exp(-t / 0.04) else 1.5 + 4 * exp(-t / 0.08))
            lp += (1 - exp(-2 * PI * fc / sr)) * (sierra - lp)
            val x = 0.7 * sin(2 * PI * fase) + 0.6 * lp
            buf[i] += (x * min(1.0, t / 0.004) * exp(-t / (if (acustico) 0.5 else 1.2)) * apagado * vel * 0.5).toFloat()
        }
    }

    private fun ochoCeroOcho(buf: FloatArray, t0: Double, dur: Double, f: Double, vel: Double) {
        val r = rango(t0, dur + 0.05, buf)
        val dt = 1.0 / sr
        var fase = 0.0
        for (i in r) {
            val t = (i - r.first) * dt
            fase += f * (1 + 1.2 * exp(-t / 0.025)) * dt
            val apagado = if (t > dur) exp(-(t - dur) / 0.03) else 1.0
            val x = tanh(1.8 * sin(2 * PI * fase))
            buf[i] += (x * min(1.0, t / 0.003) * exp(-t / 1.5) * apagado * vel * 0.55).toFloat()
        }
    }

    // ---------- Percusión ----------

    fun golpe(buf: FloatArray, t0: Double, g: Golpe, vel: Double, rnd: Random) {
        when (g) {
            Golpe.BOMBO -> tambor(buf, t0, 52.0, 2.2, 0.035, 0.28, 0.9 * vel, 0.08 * vel, 0.006, rnd)
            Golpe.BOMBO_808 -> tambor(buf, t0, 45.0, 1.5, 0.03, 0.7, 0.9 * vel, 0.05 * vel, 0.005, rnd)
            Golpe.CAJA -> tambor(buf, t0, 185.0, 0.6, 0.02, 0.09, 0.35 * vel, 0.6 * vel, 0.14, rnd, ruidoAgudo = true)
            Golpe.RIM -> tambor(buf, t0, 1650.0, 0.0, 1.0, 0.02, 0.4 * vel, 0.2 * vel, 0.01, rnd)
            Golpe.PALMAS -> repeat(3) { k -> ruido(buf, t0 + k * 0.011, if (k == 2) 0.12 else 0.01, 0.5 * vel, 1200.0, 0.9, rnd) }
            Golpe.HIHAT -> ruido(buf, t0, 0.035, 0.28 * vel, 7000.0, 1.0, rnd)
            Golpe.HIHAT_ABIERTO -> ruido(buf, t0, 0.22, 0.25 * vel, 6500.0, 1.0, rnd)
            Golpe.PLATILLO -> ruido(buf, t0, 1.2, 0.3 * vel, 5000.0, 1.0, rnd)
            Golpe.MARACA -> ruido(buf, t0, 0.04, 0.18 * vel, 5000.0, 1.0, rnd)
            Golpe.MATRACA -> repeat(5) { k -> ruido(buf, t0 + k * 0.018, 0.012, 0.3 * vel, 2500.0, 0.8, rnd) }
            Golpe.GUIRO -> repeat(7) { k -> ruido(buf, t0 + k * 0.014, 0.01, 0.22 * vel * (1 - k * 0.08), 3200.0, 0.7, rnd) }
            Golpe.CLAVE -> tambor(buf, t0, 2500.0, 0.0, 1.0, 0.03, 0.4 * vel, 0.0, 0.01, rnd)
            Golpe.CENCERRO -> { tambor(buf, t0, 800.0, 0.0, 1.0, 0.08, 0.22 * vel, 0.0, 0.01, rnd); tambor(buf, t0, 540.0, 0.0, 1.0, 0.08, 0.22 * vel, 0.0, 0.01, rnd) }
            Golpe.BONGO_AGUDO -> tambor(buf, t0, 420.0, 0.25, 0.02, 0.09, 0.5 * vel, 0.05 * vel, 0.01, rnd)
            Golpe.BONGO_GRAVE -> tambor(buf, t0, 290.0, 0.25, 0.02, 0.12, 0.5 * vel, 0.05 * vel, 0.01, rnd)
            Golpe.CONGA -> tambor(buf, t0, 210.0, 0.2, 0.02, 0.18, 0.5 * vel, 0.04 * vel, 0.01, rnd)
            Golpe.TIMBAL -> tambor(buf, t0, 640.0, 0.1, 0.02, 0.15, 0.35 * vel, 0.25 * vel, 0.06, rnd, ruidoAgudo = true)
        }
    }

    private fun tambor(
        buf: FloatArray, t0: Double, f: Double, caidaTono: Double, tauTono: Double, tau: Double,
        ampTono: Double, ampRuido: Double, tauRuido: Double, rnd: Random, ruidoAgudo: Boolean = false,
    ) {
        val r = rango(t0, tau * 5 + tauRuido * 5, buf)
        val dt = 1.0 / sr
        var fase = 0.0
        var lp = 0.0
        for (i in r) {
            val t = (i - r.first) * dt
            fase += f * (1 + caidaTono * exp(-t / tauTono)) * dt
            val n = rnd.nextDouble(-1.0, 1.0)
            lp += 0.3 * (n - lp)
            val ruido = if (ruidoAgudo) n - lp else lp
            val x = ampTono * sin(2 * PI * fase) * exp(-t / tau) + ampRuido * ruido * exp(-t / tauRuido)
            buf[i] += (x * min(1.0, t / 0.001)).toFloat()
        }
    }

    private fun ruido(buf: FloatArray, t0: Double, tau: Double, amp: Double, fc: Double, agudo: Double, rnd: Random) {
        val r = rango(t0, tau * 6, buf)
        val a = 1 - exp(-2 * PI * fc / sr)
        var lp = 0.0
        val dt = 1.0 / sr
        for (i in r) {
            val t = (i - r.first) * dt
            val n = rnd.nextDouble(-1.0, 1.0)
            lp += a * (n - lp)
            val x = agudo * (n - lp) + (1 - agudo) * lp
            buf[i] += (x * amp * exp(-t / tau) * min(1.0, t / 0.0008)).toFloat()
        }
    }
}
