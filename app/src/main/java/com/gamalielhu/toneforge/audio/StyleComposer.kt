package com.gamalielhu.toneforge.audio

import java.io.File
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Perfil musical de un género. Los patrones son cadenas de [pasos] caracteres por compás:
 * batería/bajo/acompañamiento usan 'x' golpe; bajo: r raíz, 5 quinta, 8 octava, 3 tercera, p aproximación;
 * acompañamiento: X acorde sostenido, x acorde corto, s rasgueo, a arpegio; melodía: x nota, - liga, . silencio.
 */
data class Estilo(
    val id: String,
    val nombre: String,
    val bpm: IntRange,
    val pasos: Int = 16,
    val menor: Double = 0.3,            // probabilidad de tonalidad menor
    val escalaMayor: List<Int> = listOf(0, 2, 4, 5, 7, 9, 11),
    val escalaMenor: List<Int> = listOf(0, 2, 3, 5, 7, 8, 10),
    val progMayor: List<List<String>>,
    val progMenor: List<List<String>>,
    val melodia: List<Instrumento>,
    val terceras: Double = 0.0,         // probabilidad de doblar la melodía una tercera abajo
    val armonia: Instrumento,
    val acomp: List<String>,
    val pad: Instrumento? = null,
    val bajo: Instrumento?,
    val bajoPatron: List<String>,
    val bateria: Map<Golpe, String> = emptyMap(),
    val celdas: List<String>,
    val cierre: List<String>,
    val swing: Double = 0.0,
    val reverb: Double = 0.2,
    val tonicas: IntRange = 55..64,     // midi de la tónica (registro del acompañamiento)
    val octavaMelodia: Int = 12,
    val adornos: Double = 0.0,          // probabilidad de mordente antes de notas largas
    val palabras: List<String> = emptyList(),
)

/** Receta de un tono compuesto: con esto se regenera el mismo audio. */
data class Composicion(val estiloId: String, val seed: Int, val kind: ToneKind, val artista: String? = null)

object Estilos {
    private const val B = "x...x...x...x..."
    val todos: List<Estilo> = listOf(
        Estilo(
            "bolero", "Bolero", 76..92, menor = 0.35,
            progMayor = listOf(listOf("Imaj7", "vi7", "ii7", "V7"), listOf("I", "I7", "IV", "iv"), listOf("Imaj7", "III7", "vi7", "II7", "ii7", "V7")),
            progMenor = listOf(listOf("i", "iv", "V7", "i"), listOf("i", "VI", "ii7", "V7")),
            melodia = listOf(Instrumento.VIOLIN, Instrumento.REQUINTO, Instrumento.SAXO, Instrumento.TROMPETA), terceras = 0.25,
            armonia = Instrumento.GUITARRA, acomp = listOf("a.a.a.a.a.a.a.a.", "s...s.s.s...s.s."), pad = Instrumento.CUERDAS,
            bajo = Instrumento.BAJO, bajoPatron = listOf("r.....5.r.......", "r.......5.....5."),
            bateria = mapOf(Golpe.BONGO_AGUDO to "x.x.x.x.x.x.x.x.", Golpe.BONGO_GRAVE to "......x.......x.", Golpe.MARACA to "x.x.x.x.x.x.x.x.", Golpe.CLAVE to "....x.......x..."),
            celdas = listOf("x--.x-x-x---x-x-", "x---x-x-x-------", "..x-x-x-x---x---", "x-----x-x-x-x---"), cierre = listOf("x---x---x-------", "x-x-x-----------"),
            reverb = 0.45, adornos = 0.2, palabras = listOf("bolero", "romance", "trio"),
        ),
        Estilo(
            "balada", "Balada romántica", 64..80, menor = 0.25,
            progMayor = listOf(listOf("I", "V", "vi", "IV"), listOf("I", "iii", "IV", "V"), listOf("vi", "IV", "I", "V"), listOf("Imaj7", "IVmaj7", "ii7", "V7")),
            progMenor = listOf(listOf("i", "bVI", "bIII", "bVII"), listOf("i", "iv", "bVII", "bIII")),
            melodia = listOf(Instrumento.PIANO, Instrumento.CUERDAS, Instrumento.SAXO, Instrumento.PIANO_ELECTRICO),
            armonia = Instrumento.PIANO, acomp = listOf("X.......a...a...", "a.a.a.a.a.a.a.a."), pad = Instrumento.CUERDAS,
            bajo = Instrumento.BAJO, bajoPatron = listOf("r-------r---5---", "r-------------p-"),
            bateria = mapOf(Golpe.BOMBO to "x.......x.x.....", Golpe.CAJA to "....x.......x...", Golpe.HIHAT to "x.x.x.x.x.x.x.x."),
            celdas = listOf("x-x-x---x-x-x---", "..x-x-x-x-------", "x---x-x-x-x-x---", "x-x-x-x-x-----x-"), cierre = listOf("x-x-x-----------", "x---x-----------"),
            reverb = 0.45, palabras = listOf("balada", "romantic", "romantica", "amor"),
        ),
        Estilo(
            "pop80", "Pop latino 80s/90s", 108..124, menor = 0.3,
            progMayor = listOf(listOf("I", "V", "vi", "IV"), listOf("IV", "V", "iii", "vi"), listOf("I", "vi", "IV", "V")),
            progMenor = listOf(listOf("i", "bVII", "bVI", "bVII"), listOf("i", "iv", "bVII", "bIII")),
            melodia = listOf(Instrumento.METALES, Instrumento.SINTE, Instrumento.SAXO, Instrumento.PIANO_ELECTRICO), terceras = 0.3,
            armonia = Instrumento.PIANO_ELECTRICO, acomp = listOf("..x...x...x...x.", "x..x..x...x..x.."), pad = Instrumento.PAD,
            bajo = Instrumento.BAJO_SINTE, bajoPatron = listOf("r.8.r.8.r.8.r.8.", "r.r.r.r.r.r.5.8."),
            bateria = mapOf(Golpe.BOMBO to "x.......x.......", Golpe.CAJA to "....x.......x...", Golpe.HIHAT to "x.x.x.x.x.x.x.x.", Golpe.PALMAS to "............x..."),
            celdas = listOf("x.x.x.x.x-x-..x-", "x-x-..x-x-..x-x-", "..x.x.x.x---x---", "x---x-x-..x-x---"), cierre = listOf("x.x.x-----------", "x---------------"),
            reverb = 0.3, palabras = listOf("pop latino", "ochenta", "80s", "90s", "noventa", "disco"),
        ),
        Estilo(
            "mariachi", "Mariachi / ranchera", 150..176, pasos = 12, menor = 0.15,
            progMayor = listOf(listOf("I", "V7", "V7", "I"), listOf("I", "IV", "V7", "I"), listOf("I", "I7", "IV", "V7")),
            progMenor = listOf(listOf("i", "iv", "V7", "i")),
            melodia = listOf(Instrumento.TROMPETA, Instrumento.VIOLIN), terceras = 0.85,
            armonia = Instrumento.GUITARRA, acomp = listOf("....s...s..."), pad = null,
            bajo = Instrumento.GUITARRON, bajoPatron = listOf("r...........", "5..........."),
            bateria = emptyMap(),
            celdas = listOf("x---x-x-x-x-", "x-----x---x-", "x---x---x-x-", "..x-x-x-x---"), cierre = listOf("x---x-------", "x-----------"),
            reverb = 0.3, adornos = 0.35, palabras = listOf("mariachi", "ranchera", "mexican", "charro", "regional mexicano"),
        ),
        Estilo(
            "norteno", "Norteño / corrido", 100..116, menor = 0.35,
            progMayor = listOf(listOf("I", "IV", "V7", "I"), listOf("I", "V7", "V7", "I")),
            progMenor = listOf(listOf("i", "bVII", "bVI", "V7"), listOf("i", "iv", "V7", "i")),
            melodia = listOf(Instrumento.ACORDEON, Instrumento.REQUINTO), terceras = 0.5,
            armonia = Instrumento.GUITARRA, acomp = listOf("..s...s...s...s."), bajo = Instrumento.BAJO, bajoPatron = listOf("r.......5.......", "r...5...r...5..."),
            bateria = mapOf(Golpe.BOMBO to "x...x...x...x...", Golpe.CAJA to "..x...x...x...x."),
            celdas = listOf("x-x-x-x-x---x-x-", "x.x.x.x.x-x-x---", "..x-x-x-x-x-x---"), cierre = listOf("x-x-x-----------"),
            reverb = 0.2, adornos = 0.3, palabras = listOf("norteño", "norteno", "corrido", "tumbado", "banda", "grupera"),
        ),
        Estilo(
            "reggaeton", "Reggaetón", 88..98, menor = 0.85,
            progMayor = listOf(listOf("vi", "IV", "I", "V")),
            progMenor = listOf(listOf("i", "bVI", "bIII", "bVII"), listOf("i", "bVII", "bVI", "bVII"), listOf("i", "iv", "bVI", "V")),
            melodia = listOf(Instrumento.SINTE, Instrumento.CAMPANITA, Instrumento.REQUINTO, Instrumento.PIANO),
            armonia = Instrumento.PAD, acomp = listOf("X..............."), pad = null,
            bajo = Instrumento.BAJO_808, bajoPatron = listOf("r--r--r-r--r--r-", "r-----r-r-------"),
            bateria = mapOf(Golpe.BOMBO_808 to B, Golpe.CAJA to "...x..x....x..x.", Golpe.HIHAT to "x.x.x.x.x.x.x.x.", Golpe.RIM to "...x..x....x..x."),
            celdas = listOf("x.x.x.x...x.x...", "..x.x.x.x.x.x-..", "x-x-x---..x-x---"), cierre = listOf("x.x.x-----------"),
            reverb = 0.2, palabras = listOf("reggaeton", "regueton", "perreo", "urbano", "dembow", "latin urban"),
        ),
        Estilo(
            "cumbia", "Cumbia", 92..104, menor = 0.5,
            progMayor = listOf(listOf("I", "V7", "V7", "I"), listOf("I", "IV", "V7", "I")),
            progMenor = listOf(listOf("i", "iv", "V7", "i"), listOf("i", "bVII", "bVI", "V7")),
            melodia = listOf(Instrumento.ACORDEON, Instrumento.SINTE, Instrumento.FLAUTA, Instrumento.REQUINTO), terceras = 0.4,
            armonia = Instrumento.PIANO_ELECTRICO, acomp = listOf("..x...x...x...x."), bajo = Instrumento.BAJO, bajoPatron = listOf("r.......5.......", "r.....r.5......."),
            bateria = mapOf(Golpe.GUIRO to "x.x.x.x.x.x.x.x.", Golpe.CONGA to "......x.......x.", Golpe.BONGO_AGUDO to "..x...x...x...x.", Golpe.BOMBO to "x.......x......."),
            celdas = listOf("x.x.x.x.x---x.x.", "..x.x.x.x-x-x---", "x-x-x.x.x.x.x---"), cierre = listOf("x.x.x-----------"),
            reverb = 0.2, palabras = listOf("cumbia", "chicha", "tropical", "vallenato"),
        ),
        Estilo(
            "salsa", "Salsa", 92..100, menor = 0.6,
            progMayor = listOf(listOf("I", "IV", "V7", "IV"), listOf("ii7", "V7", "I", "I")),
            progMenor = listOf(listOf("i", "iv", "V7", "i"), listOf("ii7", "V7", "i", "i")),
            melodia = listOf(Instrumento.METALES, Instrumento.TROMPETA, Instrumento.PIANO), terceras = 0.6,
            armonia = Instrumento.PIANO, acomp = listOf("x.ax.xa.x.ax.xa."), bajo = Instrumento.BAJO, bajoPatron = listOf("...r..5....r..5."),
            bateria = mapOf(Golpe.CLAVE to "x..x..x...x.x...", Golpe.CENCERRO to "x.x.x.x.x.x.x.x.", Golpe.CONGA to "......xx......xx", Golpe.BONGO_AGUDO to "..x...x...x...x."),
            celdas = listOf("x.x.x..x..x.x...", "..x.x.x.x..x.x..", "x..x..x.x-x-x---"), cierre = listOf("x..x..x---------"),
            reverb = 0.2, palabras = listOf("salsa", "son cubano", "timba", "tropical"),
        ),
        Estilo(
            "bachata", "Bachata", 124..134, menor = 0.7,
            progMayor = listOf(listOf("I", "IV", "V7", "IV"), listOf("I", "vi", "IV", "V7")),
            progMenor = listOf(listOf("i", "iv", "V7", "i"), listOf("i", "bVI", "bVII", "i")),
            melodia = listOf(Instrumento.REQUINTO), terceras = 0.2,
            armonia = Instrumento.GUITARRA, acomp = listOf("a.a.a.a.a.a.a.a."), bajo = Instrumento.BAJO, bajoPatron = listOf("r.....5.r.....8.", "...r..5....r..5."),
            bateria = mapOf(Golpe.BONGO_AGUDO to "x.x.x.x.x.x.x.x.", Golpe.BONGO_GRAVE to "......x.......x.", Golpe.GUIRO to "x.xxx.xxx.xxx.xx"),
            celdas = listOf("x.x.x.x.x.x.x-x-", "x-x.x.x.x---x.x.", "..x.x.x.x.x.x---"), cierre = listOf("x.x.x.x---------"),
            reverb = 0.25, adornos = 0.3, palabras = listOf("bachata"),
        ),
        Estilo(
            "andino", "Folklore andino", 100..124, menor = 0.9,
            escalaMenor = listOf(0, 3, 5, 7, 10), escalaMayor = listOf(0, 2, 4, 7, 9),
            progMayor = listOf(listOf("I", "V", "vi", "iii"), listOf("I", "IV", "V", "I")),
            progMenor = listOf(listOf("i", "bIII", "bVII", "i"), listOf("i", "iv", "bIII", "V7"), listOf("bIII", "bVII", "i", "i")),
            melodia = listOf(Instrumento.ZAMPONA, Instrumento.FLAUTA, Instrumento.CHARANGO), terceras = 0.5,
            armonia = Instrumento.CHARANGO, acomp = listOf("s.s.s.ss.s.s.s.s", "s..ss..ss..ss..s"), bajo = Instrumento.BAJO, bajoPatron = listOf("r...5...r...5...", "r..r....r..r...."),
            bateria = mapOf(Golpe.BOMBO to "x..x....x..x....", Golpe.MARACA to "x.x.x.x.x.x.x.x."),
            celdas = listOf("x..xx.x.x..xx.x.", "x-x.x.x.x---x.x.", "x..xx.x.x-------"), cierre = listOf("x..xx-----------"),
            reverb = 0.25, octavaMelodia = 12, palabras = listOf("andino", "huayño", "huayno", "saya", "folklore", "folclore", "boliviano", "kjarkas", "zampoña", "charango", "tinku"),
        ),
        Estilo(
            "morenada", "Morenada / caporal", 84..100, menor = 0.8,
            progMayor = listOf(listOf("I", "V7", "V7", "I")),
            progMenor = listOf(listOf("i", "iv", "V7", "i"), listOf("i", "bVII", "bIII", "V7")),
            melodia = listOf(Instrumento.METALES, Instrumento.TROMPETA), terceras = 0.8,
            armonia = Instrumento.METALES, acomp = listOf("..x...x...x...x."), bajo = Instrumento.BAJO, bajoPatron = listOf("r.......5.......", "r...5...r...5..."),
            bateria = mapOf(Golpe.BOMBO to "x.......x...x...", Golpe.MATRACA to "x.x...x.x.x...x.", Golpe.PLATILLO to "....x.......x...", Golpe.CAJA to "..x...x...x...x."),
            celdas = listOf("x-x-x.x.x---x-x-", "x--.x-x-x---x---"), cierre = listOf("x--.x-----------"),
            reverb = 0.25, palabras = listOf("morenada", "caporal", "diablada", "banda boliviana", "carnaval", "oruro", "gran poder"),
        ),
        Estilo(
            "rock", "Rock", 110..132, menor = 0.5,
            progMayor = listOf(listOf("I", "bVII", "IV", "I"), listOf("I", "IV", "V", "IV"), listOf("I", "V", "vi", "IV")),
            progMenor = listOf(listOf("i", "bVI", "bVII", "i"), listOf("i", "bIII", "bVII", "iv")),
            melodia = listOf(Instrumento.GUITARRA_ELECTRICA, Instrumento.SINTE),
            armonia = Instrumento.GUITARRA_ELECTRICA, acomp = listOf("x.x.x.x.x.x.x.x."), bajo = Instrumento.BAJO, bajoPatron = listOf("r.r.r.r.r.r.r.r."),
            bateria = mapOf(Golpe.BOMBO to "x.....x.x.......", Golpe.CAJA to "....x.......x...", Golpe.HIHAT to "x.x.x.x.x.x.x.x."),
            celdas = listOf("x-x-x.x.x---x-x-", "x---x-x-x-x-x---"), cierre = listOf("x-x-x-----------"),
            reverb = 0.2, tonicas = 52..59, palabras = listOf("rock", "metal", "punk", "grunge", "alternativo", "alternative"),
        ),
        Estilo(
            "pop", "Pop", 100..120, menor = 0.3,
            progMayor = listOf(listOf("I", "V", "vi", "IV"), listOf("vi", "IV", "I", "V"), listOf("I", "IV", "vi", "V")),
            progMenor = listOf(listOf("i", "bVI", "bIII", "bVII")),
            melodia = listOf(Instrumento.PIANO, Instrumento.SINTE, Instrumento.CAMPANITA, Instrumento.MARIMBA),
            armonia = Instrumento.PIANO, acomp = listOf("x.x.x.x.x.x.x.x.", "X...x...X...x..."), pad = Instrumento.PAD,
            bajo = Instrumento.BAJO_SINTE, bajoPatron = listOf("r...r...r...r...", "r..r..r.r..r..r."),
            bateria = mapOf(Golpe.BOMBO to "x.......x.......", Golpe.PALMAS to "....x.......x...", Golpe.HIHAT to "..x...x...x...x."),
            celdas = listOf("x.x.x.x...x.x...", "..x.x.x.x---x---", "x-x-x-..x-x-x---"), cierre = listOf("x.x.x-----------"),
            reverb = 0.25, palabras = listOf("pop", "k-pop", "kpop"),
        ),
        Estilo(
            "edm", "Electrónica", 122..128, menor = 0.7,
            progMayor = listOf(listOf("vi", "IV", "I", "V")),
            progMenor = listOf(listOf("i", "bVI", "bIII", "bVII"), listOf("i", "bVII", "bVI", "bVII")),
            melodia = listOf(Instrumento.SINTE, Instrumento.CAMPANITA),
            armonia = Instrumento.PAD, acomp = listOf("..X...X...X...X."), bajo = Instrumento.BAJO_SINTE, bajoPatron = listOf("..r...r...r...r."),
            bateria = mapOf(Golpe.BOMBO to B, Golpe.PALMAS to "....x.......x...", Golpe.HIHAT_ABIERTO to "..x...x...x...x."),
            celdas = listOf("x.x.x.x.x.x.x.x.", "x..x..x.x..x..x.", "x-x-x-x-x-x-x---"), cierre = listOf("x.x.x-----------"),
            reverb = 0.3, palabras = listOf("electronica", "electronic", "edm", "house", "techno", "dance", "trance"),
        ),
        Estilo(
            "trap", "Trap / hip hop", 66..76, menor = 0.9,
            progMayor = listOf(listOf("vi", "IV", "I", "V")),
            progMenor = listOf(listOf("i", "bVI", "i", "bVII"), listOf("i", "iv", "bVI", "V")),
            melodia = listOf(Instrumento.CAMPANITA, Instrumento.PIANO, Instrumento.FLAUTA),
            armonia = Instrumento.PAD, acomp = listOf("X..............."), bajo = Instrumento.BAJO_808, bajoPatron = listOf("r------r--r-----", "r---------r--r--"),
            bateria = mapOf(Golpe.BOMBO_808 to "x......x..x.....", Golpe.CAJA to "........x.......", Golpe.HIHAT to "x.xxx.x.x.xxx.xx"),
            celdas = listOf("x.x.x...x.x.x...", "x--.x--.x-x-x---"), cierre = listOf("x.x.x-----------"),
            reverb = 0.3, palabras = listOf("trap", "hip hop", "hip-hop", "rap", "drill"),
        ),
        Estilo(
            "jazz", "Jazz / swing", 110..140, menor = 0.2, swing = 0.33,
            progMayor = listOf(listOf("ii7", "V7", "Imaj7", "vi7"), listOf("Imaj7", "vi7", "ii7", "V7")),
            progMenor = listOf(listOf("ii7", "V7", "i", "i")),
            melodia = listOf(Instrumento.SAXO, Instrumento.TROMPETA, Instrumento.PIANO),
            armonia = Instrumento.PIANO, acomp = listOf("...x......x....."), bajo = Instrumento.BAJO, bajoPatron = listOf("r...3...5...p..."),
            bateria = mapOf(Golpe.HIHAT_ABIERTO to "x...x.x.x...x.x.", Golpe.HIHAT to "....x.......x..."),
            celdas = listOf("x.x.x.x.x---..x.", "..x.x.x-x.x.x---"), cierre = listOf("x.x.x-----------"),
            reverb = 0.25, palabras = listOf("jazz", "swing", "blues", "crooner", "big band"),
        ),
        Estilo(
            "clasica", "Clásica", 80..110, menor = 0.35,
            progMayor = listOf(listOf("I", "IV", "V7", "I"), listOf("I", "vi", "ii", "V7")),
            progMenor = listOf(listOf("i", "iv", "V7", "i")),
            melodia = listOf(Instrumento.PIANO, Instrumento.VIOLIN, Instrumento.FLAUTA),
            armonia = Instrumento.PIANO, acomp = listOf("a.a.a.a.a.a.a.a."), pad = Instrumento.CUERDAS, bajo = null, bajoPatron = listOf(""),
            celdas = listOf("x.x.x.x.x---x---", "x-x-x-x-x.x.x.x.", "x---x.x.x---x---"), cierre = listOf("x.x.x-----------"),
            reverb = 0.5, palabras = listOf("clasica", "classical", "orquesta", "piano clasico", "barroco", "opera"),
        ),
        Estilo(
            "tango", "Tango", 112..124, menor = 0.9,
            progMenor = listOf(listOf("i", "iv", "V7", "i"), listOf("i", "V7", "V7", "i")),
            progMayor = listOf(listOf("I", "V7", "V7", "I")),
            melodia = listOf(Instrumento.ACORDEON, Instrumento.VIOLIN),
            armonia = Instrumento.PIANO, acomp = listOf("x...x...x...x...", "x..x..x.x......."), bajo = Instrumento.BAJO, bajoPatron = listOf("r...5...r...5...", "r..5..r.r......."),
            celdas = listOf("x--x--x.x-x-x---", "x.x.x.x.x---x---"), cierre = listOf("x--x--x---------"),
            reverb = 0.25, adornos = 0.25, palabras = listOf("tango", "milonga", "bandoneon"),
        ),
        Estilo(
            "lofi", "Lo-fi / chill", 70..86, menor = 0.4, swing = 0.25,
            progMayor = listOf(listOf("IVmaj7", "iii7", "ii7", "Imaj7"), listOf("Imaj7", "vi7", "ii7", "V7")),
            progMenor = listOf(listOf("i7", "iv7", "bVIImaj7", "bIIImaj7")),
            melodia = listOf(Instrumento.PIANO_ELECTRICO, Instrumento.CAMPANITA, Instrumento.GUITARRA),
            armonia = Instrumento.PIANO_ELECTRICO, acomp = listOf("X.......X......."), bajo = Instrumento.BAJO, bajoPatron = listOf("r.......r..5...."),
            bateria = mapOf(Golpe.BOMBO to "x.....x...x.....", Golpe.RIM to "....x.......x...", Golpe.HIHAT to "x.x.x.x.x.x.x.x."),
            celdas = listOf("x.x.x...x.x.....", "..x.x.x.x-------"), cierre = listOf("x.x.x-----------"),
            reverb = 0.35, palabras = listOf("lofi", "lo-fi", "chill", "relajante", "estudiar"),
        ),
    )

    fun porId(id: String) = todos.first { it.id == id }

    /** Artistas conocidos -> estilos (el primero pesa más). */
    val artistas: Map<String, List<String>> = buildMap {
        fun a(estilos: List<String>, vararg nombres: String) = nombres.forEach { put(normalizar(it), estilos) }
        a(listOf("bolero", "balada", "pop80", "mariachi"), "luis miguel", "luismi")
        a(listOf("bolero", "balada"), "armando manzanero", "los panchos", "trio los panchos", "lucho gatica", "javier solis", "jose jose", "marco antonio solis", "los temerarios", "julio iglesias", "sin bandera", "camilo sesto", "raphael", "jose luis perales", "roberto carlos", "la sonora santanera", "eydie gorme", "natalia lafourcade")
        a(listOf("balada", "pop80"), "cristian castro", "ricardo arjona", "franco de vita", "ricardo montaner", "chayanne", "alejandro sanz", "laura pausini", "eros ramazzotti", "rocio durcal", "juan gabriel", "ana gabriel", "yuri", "emmanuel", "mijares", "pandora", "rio roma", "reik", "jesse y joy", "camila", "david bisbal", "pablo alboran", "luis fonsi", "franco de vita")
        a(listOf("pop80", "pop"), "ricky martin", "gloria trevi", "thalia", "paulina rubio", "enrique iglesias", "gloria estefan", "menudo", "timbiriche", "flans", "magneto", "mecano", "shakira", "carlos vives")
        a(listOf("mariachi", "norteno"), "vicente fernandez", "alejandro fernandez", "pedro infante", "jorge negrete", "antonio aguilar", "pepe aguilar", "christian nodal", "angela aguilar", "lola beltran", "jose alfredo jimenez", "mariachi vargas", "joan sebastian", "lucero")
        a(listOf("norteno", "mariachi"), "los tigres del norte", "ramon ayala", "intocable", "grupo firme", "peso pluma", "natanael cano", "junior h", "fuerza regida", "eslabon armado", "gerardo ortiz", "los tucanes de tijuana", "calibre 50", "carin leon", "banda ms", "la arrolladora", "julion alvarez", "chalino sanchez", "los bukis")
        a(listOf("reggaeton", "trap"), "bad bunny", "daddy yankee", "j balvin", "karol g", "maluma", "ozuna", "feid", "rauw alejandro", "anuel aa", "anuel", "nicky jam", "don omar", "wisin y yandel", "wisin", "yandel", "farruko", "myke towers", "sech", "manuel turizo", "becky g", "tini", "nathy peluso", "rosalia", "young miko", "blessd", "ryan castro", "quevedo", "bizarrap", "jhay cortez", "arcangel", "de la ghetto", "tego calderon", "ivy queen", "zion y lennox", "plan b")
        a(listOf("cumbia"), "los angeles azules", "grupo 5", "americo", "tropicalisimo apache", "los askis", "aguaturbia", "la sonora dinamita", "rodrigo tapari", "los mirlos", "agua marina", "armonia 10", "corazon serrano", "kaleth morales", "silvestre dangond", "diomedes diaz", "binomio de oro", "los kjarkitas", "chila jatun", "maroyu", "azul azul", "damas gratis", "los palmeras", "la nueva luna", "karicia", "los chalchaleros")
        a(listOf("salsa", "cumbia"), "marc anthony", "celia cruz", "hector lavoe", "willie colon", "ruben blades", "gilberto santa rosa", "grupo niche", "oscar d leon", "joe arroyo", "victor manuelle", "tito nieves", "frankie ruiz", "el gran combo", "la india", "jerry rivera", "eddie santiago", "tito rojas", "buena vista social club")
        a(listOf("bachata", "balada"), "romeo santos", "aventura", "prince royce", "juan luis guerra", "frank reyes", "anthony santos", "grupo extra", "monchy y alexandra", "toby love")
        a(listOf("andino", "morenada"), "los kjarkas", "kjarkas", "savia andina", "proyeccion", "bonny lovy", "llajtaymanta", "wara", "los canarios del chaco", "jach a mallku", "kalamarka", "los jairas", "ruphay", "pasion andina", "alaxpacha", "zulma yugar", "gilbert cortez", "yarita lizeth", "kala marka", "la mazcara", "los rumberos", "kory sonqo", "chila jatun")
        a(listOf("morenada", "andino"), "banda intercontinental poopo", "poopo", "banda pagador", "banda san simon", "banda espectacular pagador", "banda real imperial", "kollasuyo maya", "banda munecas")
        a(listOf("rock", "balada"), "soda stereo", "gustavo cerati", "mana", "heroes del silencio", "enanitos verdes", "caifanes", "cafe tacvba", "los prisioneros", "aterciopelados", "la ley", "hombres g", "los fabulosos cadillacs", "vilma palma", "los enanitos verdes", "babasonicos", "el tri", "zoe", "molotov", "queen", "the beatles", "beatles", "metallica", "ac dc", "acdc", "guns n roses", "nirvana", "bon jovi", "linkin park", "coldplay", "oasis", "foo fighters", "arctic monkeys", "red hot chili peppers", "imagine dragons", "the rolling stones", "led zeppelin", "pink floyd", "u2", "radiohead", "green day", "aerosmith", "kiss", "scorpions")
        a(listOf("pop", "edm"), "taylor swift", "dua lipa", "ed sheeran", "the weeknd", "ariana grande", "billie eilish", "bruno mars", "michael jackson", "madonna", "lady gaga", "katy perry", "justin bieber", "harry styles", "olivia rodrigo", "sabrina carpenter", "miley cyrus", "selena gomez", "rihanna", "beyonce", "adele", "sia", "abba", "bts", "blackpink", "maroon 5", "one direction", "backstreet boys", "britney spears", "whitney houston", "mariah carey", "celine dion", "elton john", "lana del rey", "charli xcx", "doja cat", "aitana", "morat", "sebastian yatra", "camilo", "danna paola", "kali uchis", "mon laferte", "carin leon")
        a(listOf("edm", "pop"), "avicii", "david guetta", "calvin harris", "daft punk", "marshmello", "martin garrix", "tiesto", "skrillex", "kygo", "alan walker", "deadmau5", "swedish house mafia", "the chainsmokers", "zedd", "alok", "fred again")
        a(listOf("trap", "reggaeton"), "eminem", "drake", "travis scott", "kanye west", "kendrick lamar", "post malone", "21 savage", "tupac", "2pac", "snoop dogg", "50 cent", "duki", "trueno", "khea", "eladio carrion", "cris mj", "paulo londra", "canserbero", "residente", "calle 13", "nicki minaj", "cardi b", "lil wayne", "future", "j cole", "xxxtentacion", "juice wrld")
        a(listOf("jazz", "balada"), "frank sinatra", "michael buble", "louis armstrong", "ella fitzgerald", "nat king cole", "tony bennett", "norah jones", "billie holiday", "ray charles", "stevie wonder", "miles davis", "john coltrane", "duke ellington", "amy winehouse", "diana krall")
        a(listOf("clasica"), "mozart", "beethoven", "bach", "vivaldi", "chopin", "tchaikovsky", "chaikovski", "debussy", "handel", "haydn", "schubert", "brahms", "liszt", "strauss", "hans zimmer", "john williams", "ludovico einaudi", "yiruma", "andrea bocelli", "pavarotti")
        a(listOf("tango"), "carlos gardel", "gardel", "astor piazzolla", "piazzolla", "bajofondo", "gotan project", "anibal troilo", "julio sosa")
        a(listOf("lofi", "jazz"), "nujabes", "lofi girl", "jinsang", "tomppabeats")
    }

    fun normalizar(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Género de iTunes/Apple Music -> estilo. */
    fun desdeGeneroRemoto(g: String): List<String> {
        val n = normalizar(g)
        return when {
            "regional mexicano" in n || "mariachi" in n || "ranchera" in n -> listOf("mariachi", "norteno")
            "musica mexicana" in n || "banda" in n || "norte" in n -> listOf("norteno", "mariachi")
            "urbano" in n || "reggaeton" in n || "latin urban" in n -> listOf("reggaeton", "trap")
            "salsa" in n || "tropical" in n -> listOf("salsa", "cumbia")
            "cumbia" in n -> listOf("cumbia")
            "bachata" in n -> listOf("bachata")
            "andin" in n || "folk" in n || "world" in n -> listOf("andino", "morenada")
            "tango" in n -> listOf("tango")
            "rock" in n || "metal" in n || "alternative" in n || "alternativ" in n || "punk" in n -> listOf("rock", "pop")
            "hip hop" in n || "rap" in n -> listOf("trap", "reggaeton")
            "electronic" in n || "dance" in n || "house" in n || "techno" in n -> listOf("edm", "pop")
            "jazz" in n || "vocal" in n || "blues" in n || "r b" in n || "soul" in n -> listOf("jazz", "balada")
            "classical" in n || "clasica" in n || "soundtrack" in n || "banda sonora" in n -> listOf("clasica")
            "pop latino" in n || "latin pop" in n || "latin" in n || "latino" in n -> listOf("balada", "pop80")
            "romantic" in n || "bolero" in n -> listOf("bolero", "balada")
            "pop" in n || "k pop" in n -> listOf("pop", "edm")
            "chill" in n || "lofi" in n -> listOf("lofi")
            else -> listOf("pop", "balada")
        }
    }
}

/** Compone un tono original con la armonía, el ritmo y los instrumentos de un género. */
class StyleComposer(private val sr: Int = 44_100) {
    private val inst = Instrumentos(sr)

    private class Nota(val t: Double, val dur: Double, val midi: Int, val vel: Double)

    private data class Acorde(val raiz: Int, val tonos: List<Int>)

    fun descripcion(c: Composicion): String {
        val e = Estilos.porId(c.estiloId)
        val p = plan(c)
        val instr = listOfNotNull(p.melodia, p.armonia, e.pad, e.bajo).distinct().joinToString(", ") { it.nombre }
        return "$instr · ${p.bpm} bpm · ${nombreNota(p.tonica)} ${if (p.menor) "menor" else "mayor"}"
    }

    fun duracionMs(c: Composicion): Int {
        val p = plan(c)
        val compas = 60.0 / p.bpm / 4.0 * p.e.pasos
        return ((compas * (p.acordes.size - 1) + compas * 0.6 + 1.2) * 1000).toInt()
    }

    private val nombres = listOf("Do", "Do#", "Re", "Mib", "Mi", "Fa", "Fa#", "Sol", "Lab", "La", "Sib", "Si")
    private fun nombreNota(m: Int) = nombres[m % 12]

    private class Plan(
        val e: Estilo, val bpm: Int, val tonica: Int, val menor: Boolean, val acordes: List<Acorde>,
        val melodia: Instrumento, val armonia: Instrumento, val acomp: String, val bajoPatron: String,
        val terceras: Boolean, val rnd: Random,
    )

    private fun plan(c: Composicion): Plan {
        val e = Estilos.porId(c.estiloId)
        val rnd = Random(c.seed)
        val bpm = rnd.nextInt(e.bpm.first, e.bpm.last + 1)
        val menor = rnd.nextDouble() < e.menor
        val tonica = rnd.nextInt(e.tonicas.first, e.tonicas.last + 1)
        val prog = (if (menor) e.progMenor else e.progMayor).random(rnd)
        val compas = 60.0 / bpm * (e.pasos / 4.0)
        val grados: List<String> = if (c.kind == ToneKind.NOTIFICACION) {
            val compases = (4.5 / compas).roundToInt().coerceIn(1, 4)
            val tonicaG = if (menor) "i" else "I"
            if (compases == 1) listOf(tonicaG) else (prog + tonicaG).takeLast(compases)
        } else {
            val vueltas = max(1, (18.0 / (compas * prog.size)).roundToInt())
            val lista = (1..vueltas).flatMap { prog }
            val tonicaG = if (menor) "i" else "I"
            (lista + tonicaG).take((30.0 / compas).toInt().coerceAtLeast(2))
        }
        val acordes = grados.map { acorde(it, tonica, menor) }
        return Plan(
            e, bpm, tonica, menor, acordes,
            melodia = e.melodia.random(rnd), armonia = e.armonia, acomp = e.acomp.random(rnd), bajoPatron = e.bajoPatron.random(rnd),
            terceras = rnd.nextDouble() < e.terceras, rnd = rnd,
        )
    }

    /** "bVI", "ii7", "V7", "Imaj7", "iv" -> acorde en semitonos absolutos (midi). */
    private fun acorde(g: String, tonica: Int, menor: Boolean): Acorde {
        var s = g
        var alter = 0
        while (s.startsWith("b")) { alter -= 1; s = s.drop(1) }
        while (s.startsWith("#")) { alter += 1; s = s.drop(1) }
        val romano = s.takeWhile { it.uppercaseChar() in "IV" }
        val sufijo = s.drop(romano.length)
        val grado = listOf("I", "II", "III", "IV", "V", "VI", "VII").indexOf(romano.uppercase())
        val mayorGrados = listOf(0, 2, 4, 5, 7, 9, 11)
        val menorGrados = listOf(0, 2, 3, 5, 7, 8, 10)
        // En tonalidad menor los grados con b se miden desde la escala mayor (bVI = 8), sin b desde la menor.
        val base = if (alter != 0 || !menor) mayorGrados[grado] else menorGrados[grado]
        val raiz = tonica + base + alter
        val esMenor = romano.first().isLowerCase()
        val tonos = when (sufijo) {
            "maj7" -> listOf(0, 4, 7, 11)
            "7" -> if (esMenor) listOf(0, 3, 7, 10) else listOf(0, 4, 7, 10)
            "dim" -> listOf(0, 3, 6)
            else -> if (esMenor) listOf(0, 3, 7) else listOf(0, 4, 7)
        }
        return Acorde(raiz, tonos.map { raiz + it })
    }

    fun render(c: Composicion): ShortArray {
        val p = plan(c)
        val e = p.e
        val rnd = p.rnd
        val paso = 60.0 / p.bpm / 4.0          // semicorchea
        val compas = paso * e.pasos
        val total = compas * p.acordes.size + 2.2
        val n = (total * sr).toInt()
        val pistas = mapOf(
            "mel" to FloatArray(n), "arm" to FloatArray(n), "bajo" to FloatArray(n), "bat" to FloatArray(n),
        )
        fun t(compasI: Int, pasoI: Int): Double {
            val sw = if (e.swing > 0 && pasoI % 4 == 2) e.swing * paso * 2 else 0.0
            return compasI * compas + pasoI * paso + sw
        }

        val escala = if (p.menor) e.escalaMenor else e.escalaMayor
        val pcsEscala = escala.map { (p.tonica + it) % 12 }.toSet()

        // ----- Melodía -----
        val melodia = componerMelodia(p, escala, pcsEscala)
        val ultimo = p.acordes.size - 1
        for (nt in melodia) {
            val ti = nt.t
            if (e.adornos > 0 && nt.dur > paso * 3 && rnd.nextDouble() < e.adornos) {
                inst.nota(pistas.getValue("mel"), ti - 0.05, 0.05, nt.midi + 0.0 + (if (rnd.nextBoolean()) 2 else 1), nt.vel * 0.7, p.melodia, rnd)
            }
            inst.nota(pistas.getValue("mel"), ti + rnd.nextDouble(-0.004, 0.004), nt.dur * 0.95, nt.midi.toDouble(), nt.vel, p.melodia, rnd)
            if (p.terceras) {
                val abajo = moverEnEscala(nt.midi, -2, escala, p.tonica)
                inst.nota(pistas.getValue("mel"), ti + 0.006, nt.dur * 0.95, abajo.toDouble(), nt.vel * 0.65, p.melodia, rnd)
            }
        }

        // ----- Armonía, pad y bajo -----
        var voz = listOf<Int>()
        for ((ci, ac) in p.acordes.withIndex()) {
            voz = voicing(ac, voz, p.tonica)
            val final = ci == ultimo
            val patron = if (final) "X" + ".".repeat(e.pasos - 1) else p.acomp
            var arp = 0
            for ((si, ch) in patron.withIndex()) {
                if (ch == '.' || ch == '-') continue
                var largo = 1
                while (si + largo < patron.length && patron[si + largo] == '-') largo++
                val sig = (si + 1 until patron.length).firstOrNull { patron[it] != '-' && patron[it] != '.' } ?: patron.length
                val hasta = if (ch == 'X') (sig - si) * paso else largo * paso * 1.5
                val ti = t(ci, si)
                when (ch) {
                    'X' -> voz.forEach { inst.nota(pistas.getValue("arm"), ti, if (final) 1.8 else hasta, it.toDouble(), 0.55, p.armonia, rnd) }
                    'x' -> voz.forEach { inst.nota(pistas.getValue("arm"), ti, hasta, it.toDouble(), 0.5 + if (si % 4 == 0) 0.1 else 0.0, p.armonia, rnd) }
                    's' -> {
                        val abajo = si % 4 == 0 || rnd.nextBoolean()
                        val cuerdas = (listOf(ac.raiz - 12 + if (ac.raiz - 12 < 40) 12 else 0) + voz).let { if (abajo) it else it.reversed() }
                        cuerdas.forEachIndexed { k, m -> inst.nota(pistas.getValue("arm"), ti + k * 0.012, paso * 1.6, m.toDouble(), 0.45, p.armonia, rnd) }
                    }
                    'a' -> {
                        val arpegio = listOf(voz[0] - 12, voz[0], voz[1], voz[2], voz.last() + 12 - 12 * (if (voz.size > 3) 1 else 0), voz[2], voz[1], voz[0])
                        val m = arpegio[arp % arpegio.size]; arp++
                        inst.nota(pistas.getValue("arm"), ti, paso * 2.5, m.toDouble(), 0.45, p.armonia, rnd)
                    }
                }
            }
            e.pad?.let { pad ->
                if (pad != p.melodia) voz.forEach { inst.nota(pistas.getValue("arm"), t(ci, 0), compas * (if (final) 1.4 else 1.02), it.toDouble() + 12, 0.28, pad, rnd) }
            }
            // Bajo
            e.bajo?.let { b ->
                val raizBajo = 36 + ((ac.raiz - 36) % 12 + 12) % 12
                val patronB = if (final) "r" + "-".repeat(e.pasos / 2 - 1) + ".".repeat(e.pasos / 2) else p.bajoPatron
                val siguiente = p.acordes.getOrNull(ci + 1)?.raiz
                for ((si, ch) in patronB.withIndex()) {
                    if (ch == '.' || ch == '-') continue
                    var largo = 1
                    while (si + largo < patronB.length && patronB[si + largo] == '-') largo++
                    val m = when (ch) {
                        'r' -> raizBajo; '5' -> raizBajo + 7; '8' -> raizBajo + 12
                        '3' -> raizBajo + (ac.tonos[1] - ac.raiz)
                        'p' -> siguiente?.let { 36 + ((it - 36) % 12 + 12) % 12 + if (rnd.nextBoolean()) 1 else -1 } ?: (raizBajo + 7)
                        else -> raizBajo
                    }
                    inst.nota(pistas.getValue("bajo"), t(ci, si), max(largo, 2) * paso * 0.9, m.toDouble(), 0.8, b, rnd)
                }
            }
            // Batería: en el último compás solo el primer tiempo (el final queda sonando).
            for ((g, pat) in e.bateria) {
                for ((si, ch) in pat.withIndex()) {
                    if (ch != 'x') continue
                    if (final && si > 0) continue
                    val acento = if (si % 4 == 0) 1.0 else 0.75
                    inst.golpe(pistas.getValue("bat"), t(ci, si) + rnd.nextDouble(0.0, 0.004), g, acento * rnd.nextDouble(0.85, 1.0), rnd)
                }
            }
            if (final && e.bateria.isNotEmpty() && e.id !in listOf("bolero", "bachata", "cumbia", "salsa", "andino"))
                inst.golpe(pistas.getValue("bat"), t(ci, 0), Golpe.PLATILLO, 0.6, rnd)
        }

        // ----- Mezcla -----
        val mezcla = FloatArray(n)
        val ganancias = mapOf("mel" to 1.0f, "arm" to 0.55f, "bajo" to 0.75f, "bat" to 0.6f)
        for ((k, pista) in pistas) {
            val pico = pista.maxOf { abs(it) }
            if (pico <= 0f) continue
            val g = ganancias.getValue(k) / pico.coerceAtLeast(0.25f)
            for (i in 0 until n) mezcla[i] += pista[i] * g
        }
        if (e.reverb > 0) reverb(mezcla, e.reverb.toFloat())
        // Recorta el silencio del final y aplica fade.
        val pico = mezcla.maxOf { abs(it) }.coerceAtLeast(1e-4f)
        var fin = n - 1
        while (fin > sr && abs(mezcla[fin]) < pico * 0.006f) fin--
        val largo = min(n, fin + sr / 10)
        val fadeOut = min(largo, (0.35 * sr).toInt())
        return ShortArray(largo) { i ->
            var x = mezcla[i] / pico * 1.5f
            x = tanh(x.toDouble()).toFloat() * 0.92f
            if (i < 200) x *= i / 200f
            if (i > largo - fadeOut) x *= (largo - i).toFloat() / fadeOut
            (x * Short.MAX_VALUE).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun voicing(ac: Acorde, previa: List<Int>, tonica: Int): List<Int> {
        val centro = if (previa.isEmpty()) tonica + 4 else previa.average().roundToInt()
        return ac.tonos.map { pc ->
            var m = pc
            while (m < centro - 6) m += 12
            while (m > centro + 6) m -= 12
            m
        }.sorted()
    }

    private fun aEscala(m: Int, escala: List<Int>, tonica: Int): Int {
        for (d in 0..6) {
            if (((m - d - tonica) % 12 + 12) % 12 in escala) return m - d
            if (((m + d - tonica) % 12 + 12) % 12 in escala) return m + d
        }
        return m
    }

    private fun moverEnEscala(m: Int, pasos: Int, escala: List<Int>, tonica: Int): Int {
        var x = aEscala(m, escala, tonica)
        val dir = if (pasos > 0) 1 else -1
        repeat(abs(pasos)) {
            do { x += dir } while (((x - tonica) % 12 + 12) % 12 !in escala)
        }
        return x
    }

    private fun tonoAcordeCercano(m: Int, ac: Acorde): Int {
        var mejor = m; var dist = 99
        for (pc in ac.tonos) for (oct in -2..3) {
            val c = (pc % 12) + 12 * ((m / 12) + oct - 1)
            if (abs(c - m) < dist) { dist = abs(c - m); mejor = c }
        }
        return mejor
    }

    /** Motivo en el primer compás, secuencia sobre los acordes siguientes y cadencia a la tónica. */
    private fun componerMelodia(p: Plan, escala: List<Int>, pcsEscala: Set<Int>): List<Nota> {
        val e = p.e
        val rnd = p.rnd
        val paso = 60.0 / p.bpm / 4.0
        val compas = paso * e.pasos
        val lo = p.tonica + e.octavaMelodia - 3
        val hi = p.tonica + e.octavaMelodia + 14
        val celdaA = e.celdas.random(rnd).take(e.pasos).padEnd(e.pasos, '.')
        val celdaB = e.celdas.random(rnd).take(e.pasos).padEnd(e.pasos, '.')
        val cierre = e.cierre.random(rnd).take(e.pasos).padEnd(e.pasos, '.')
        val notas = mutableListOf<Nota>()
        var motivo: List<Int>? = null    // saltos en grados del motivo
        var actual = tonoAcordeCercano(p.tonica + e.octavaMelodia + 4, p.acordes[0])

        fun onsets(celda: String) = celda.indices.filter { celda[it] == 'x' }.map { i ->
            var largo = 1
            while (i + largo < celda.length && celda[i + largo] == '-') largo++
            i to largo
        }

        for ((ci, ac) in p.acordes.withIndex()) {
            val final = ci == p.acordes.size - 1
            val celda = when {
                final -> cierre
                ci % 4 == 2 && p.acordes.size > 3 -> celdaB
                else -> celdaA
            }
            val ons = onsets(celda)
            if (final) {
                // Baja por grados conjuntos hasta la tónica más cercana.
                val tonicaCerca = (lo..hi).filter { ((it - p.tonica) % 12 + 12) % 12 == 0 }.minByOrNull { abs(it - actual) } ?: (p.tonica + 12)
                ons.forEachIndexed { k, (si, largo) ->
                    val faltan = ons.size - 1 - k
                    val m = if (faltan == 0) tonicaCerca else moverEnEscala(tonicaCerca, faltan, escala, p.tonica)
                    val dur = if (faltan == 0) max(largo * paso, compas * 0.6) else largo * paso
                    notas += Nota(ci * compas + si * paso, dur, m, if (faltan == 0) 0.9 else 0.8)
                }
                break
            }
            val usarMotivo = motivo != null && celda == celdaA && rnd.nextDouble() < 0.75
            val saltos = mutableListOf<Int>()
            ons.forEachIndexed { k, (si, largo) ->
                val fuerte = si % 4 == 0
                var m: Int
                if (k == 0) {
                    m = tonoAcordeCercano(actual + if (usarMotivo) 0 else listOf(-2, 0, 2, 3).random(rnd), ac)
                } else if (usarMotivo && k - 1 < motivo!!.size) {
                    m = moverEnEscala(actual, motivo!![k - 1], escala, p.tonica)
                    if (fuerte && rnd.nextDouble() < 0.6) m = tonoAcordeCercano(m, ac)
                } else {
                    val progreso = (ci * e.pasos + si).toDouble() / (p.acordes.size * e.pasos)
                    val sesgo = if (progreso < 0.5) 1 else -1
                    val r = rnd.nextDouble()
                    m = when {
                        r < 0.5 -> moverEnEscala(actual, if (rnd.nextDouble() < 0.65) sesgo else -sesgo, escala, p.tonica)
                        r < 0.65 -> moverEnEscala(actual, 2 * sesgo, escala, p.tonica)
                        r < 0.85 -> tonoAcordeCercano(actual + 4 * sesgo, ac)
                        else -> actual
                    }
                    if (fuerte) m = tonoAcordeCercano(m, ac)
                }
                while (m > hi) m -= 12
                while (m < lo) m += 12
                if (((m % 12) !in pcsEscala) && ac.tonos.none { (it - m) % 12 == 0 }) m = aEscala(m, escala, p.tonica)
                if (k > 0) saltos += gradosEntre(actual, m, escala, p.tonica)
                actual = m
                val vel = (if (fuerte) 0.9 else 0.75) * rnd.nextDouble(0.9, 1.0)
                notas += Nota(ci * compas + si * paso, largo * paso, m, vel)
            }
            if (motivo == null) motivo = saltos
        }
        return notas
    }

    private fun gradosEntre(a: Int, b: Int, escala: List<Int>, tonica: Int): Int {
        if (a == b) return 0
        var x = aEscala(a, escala, tonica)
        var n = 0
        val dir = if (b > a) 1 else -1
        while ((dir > 0 && x < b) || (dir < 0 && x > b)) { x = moverEnEscala(x, dir, escala, tonica); n += dir; if (abs(n) > 14) break }
        return n
    }

    /** Reverb de Schroeder (4 peines + 2 pasa-todo). */
    private fun reverb(buf: FloatArray, mezcla: Float) {
        val combs = intArrayOf(1557, 1617, 1491, 1422).map { it * sr / 44_100 }
        val out = FloatArray(buf.size)
        for (d in combs) {
            val linea = FloatArray(d)
            var idx = 0
            var lp = 0f
            for (i in buf.indices) {
                val y = linea[idx]
                lp = y * 0.75f + lp * 0.25f
                linea[idx] = buf[i] + lp * 0.8f
                out[i] += y * 0.25f
                idx = (idx + 1) % d
            }
        }
        for (d in intArrayOf(225, 556).map { it * sr / 44_100 }) {
            val linea = FloatArray(d)
            var idx = 0
            for (i in out.indices) {
                val bufOut = linea[idx]
                val x = out[i]
                linea[idx] = x + bufOut * 0.5f
                out[i] = bufOut - x * 0.5f
                idx = (idx + 1) % d
            }
        }
        for (i in buf.indices) buf[i] = buf[i] * (1 - mezcla * 0.5f) + out[i] * mezcla
    }

    fun writeWav(c: Composicion, out: File) = ToneSynth(sr).writeWav(render(c), out)
}

/** Proveedor: reconoce artista o género y devuelve composiciones originales en ese estilo. */
class StyleProvider(
    private val composer: StyleComposer = StyleComposer(),
    private val generoRemoto: suspend (String) -> String? = { null },
) : AudioSourceProvider {
    override val name = "Compositor por estilo"

    override suspend fun search(query: String, kind: ToneKind, limit: Int): List<ToneItem> {
        val q = Estilos.normalizar(query)
        if (q.isBlank()) return emptyList()
        var artista: String? = null
        var nota = ""
        val estilos: List<String> = run {
            Estilos.artistas.entries.filter { (nombre, _) -> " $q ".contains(" $nombre ") }.maxByOrNull { it.key.length }?.let {
                artista = query.trim(); return@run it.value
            }
            Estilos.todos.filter { e -> e.palabras.any { " $q ".contains(" ${Estilos.normalizar(it)} ") || q.contains(Estilos.normalizar(it)) && it.length > 4 } }
                .map { it.id }.takeIf { it.isNotEmpty() }?.let { return@run it }
            val remoto = runCatching { generoRemoto(query) }.getOrNull() ?: return emptyList()
            artista = query.trim(); nota = " (género: $remoto)"
            Estilos.desdeGeneroRemoto(remoto)
        }
        val porEstilo = if (estilos.size == 1) 6 else if (estilos.size == 2) 4 else 3
        val semilla = q.hashCode()
        return estilos.flatMapIndexed { ei, id ->
            val e = Estilos.porId(id)
            (0 until porEstilo).map { v ->
                val c = Composicion(id, semilla * 31 + ei * 1000 + v, kind, artista)
                val desc = composer.descripcion(c)
                ToneItem(
                    id = "est-$id-${kind.name}-${c.seed.toUInt().toString(16)}",
                    title = "${e.nombre}${artista?.let { " · estilo $it" } ?: ""} #${v + 1}",
                    kind = kind, source = ToneSource.COMPUESTO,
                    durationMs = composer.duracionMs(c), score = 100f - v * 2 - ei * 0.5f,
                    tags = listOf(desc + if (v == 0 && ei == 0) nota else ""), composicion = c,
                    license = "Original, generado en el equipo",
                )
            }
        }.sortedByDescending { it.score }.take(limit)
    }
}
