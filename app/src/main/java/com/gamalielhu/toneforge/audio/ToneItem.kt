package com.gamalielhu.toneforge.audio

enum class ToneKind(val label: String) { LLAMADA("Llamada"), NOTIFICACION("Notificación") }

enum class ToneSource { SINTETIZADO, COMPUESTO, FREESOUND, LOCAL, URL }

/** Un resultado: sintetizado ([spec]), compuesto por estilo ([composicion]) o de una librería libre ([uri]). */
data class ToneItem(
    val id: String,
    val title: String,
    val kind: ToneKind,
    val source: ToneSource,
    val durationMs: Int,
    val score: Float,
    val tags: List<String> = emptyList(),
    val spec: ToneSpec? = null,
    val composicion: Composicion? = null,
    val uri: String? = null,
    val license: String = "CC0",
    val author: String? = null,
)
