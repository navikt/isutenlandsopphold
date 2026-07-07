package no.nav.syfo.utenlandsopphold.domain

data class DocumentComponent(
    val type: DocumentComponentType,
    val key: String? = null,
    val title: String?,
    val texts: List<String>,
) {
    companion object {
        internal val illegalCharacters = listOf('\u0002')
    }
}

fun List<DocumentComponent>.sanitizeForPdfGen(): List<DocumentComponent> =
    this.map {
        it.copy(
            texts =
                it.texts.map { text ->
                    text
                        .toCharArray()
                        .filter { char -> char !in DocumentComponent.illegalCharacters }
                        .joinToString("")
                },
        )
    }

enum class DocumentComponentType {
    HEADER_H1,
    HEADER_H2,
    HEADER_H3,
    PARAGRAPH,
    BULLET_POINTS,
    LINK,
}

fun List<DocumentComponent>.serialize(): String =
    buildString {
        this@serialize.forEach { documentComponentDTO ->
            documentComponentDTO.title?.let {
                appendLine(it)
            }
            documentComponentDTO.texts.forEach {
                appendLine(it)
            }
            appendLine()
        }
    }
