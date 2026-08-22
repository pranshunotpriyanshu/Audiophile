package com.mocharealm.accompanist.lyrics.core.model.karaoke.mapper

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable

fun Collection<KaraokeSyllable>.contentToString(): String = this.joinToString(separator = "") {
    it.content
}

fun Collection<KaraokeSyllable>.phoneticToString(): String = this.joinToString(separator = " ") {
    it.phonetic ?: ""
}

private fun Char.isOpenParen() = this == '(' || this == '（'
private fun Char.isCloseParen() = this == ')' || this == '）'

private fun isWrappedInMatchedParens(text: String): Boolean {
    if (text.length < 2 || !text.first().isOpenParen() || !text.last().isCloseParen()) return false
    var depth = 0
    for (i in text.indices) {
        val c = text[i]
        when {
            c.isOpenParen() -> depth++
            c.isCloseParen() -> {
                depth--
                if (depth == 0) return i == text.lastIndex
                if (depth < 0) return false
            }
        }
    }
    return false
}

/** Rebuild the syllables with the characters at the given global (line-wide) offsets removed. */
private fun List<KaraokeSyllable>.removeCharsAtGlobalOffsets(offsets: Set<Int>): List<KaraokeSyllable> {
    var base = 0
    return map { syllable ->
        val content = syllable.content
        val sb = StringBuilder(content.length)
        for (i in content.indices) {
            if (base + i !in offsets) sb.append(content[i])
        }
        base += content.length
        syllable.copy(content = sb.toString())
    }
}

fun List<KaraokeSyllable>.stripEnclosingParentheses(): List<KaraokeSyllable> {
    var current = this
    while (current.isNotEmpty()) {
        val joined = current.joinToString(separator = "") { it.content }
        val open = joined.indexOfFirst { !it.isWhitespace() }
        val close = joined.indexOfLast { !it.isWhitespace() }
        if (open < 0 || close <= open) break
        if (!isWrappedInMatchedParens(joined.substring(open, close + 1))) break
        current = current.removeCharsAtGlobalOffsets(setOf(open, close))
    }
    return current
}

fun String.stripEnclosingParentheses(): String {
    var current = this
    while (current.isNotEmpty()) {
        val open = current.indexOfFirst { !it.isWhitespace() }
        val close = current.indexOfLast { !it.isWhitespace() }
        if (open !in 0..<close) break
        if (!isWrappedInMatchedParens(current.substring(open, close + 1))) break
        current = StringBuilder(current).apply {
            deleteAt(close)
            deleteAt(open)
        }.toString()
    }
    return current
}
