package com.gamalielhu.toneforge.audio

enum class ToneKind(val label: String) { LLAMADA("Llamada"), NOTIFICACION("Notificación") }

enum class ToneSource { SINTETIZADO, FREESOUND, LOCAL, URL }

/** Un resultado de búsqueda: sintetizado (trae [spec]) o de una librería libre (trae [uri]). */
data class ToneItem(
    val id: String,
    val title: String,
    val kind: ToneKind,
    val source: ToneSource,
    val durationMs: Int,
    val score: Float,
    val tags: List<String> = emptyList(),
    val spec: ToneSpec? = null,
    val uri: String? = null,
    val license: String = "CC0",
    val author: String? = null,
)
