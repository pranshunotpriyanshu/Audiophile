package com.mocharealm.accompanist.lyrics.core.model.karaoke

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine

sealed interface KaraokeLine : ISyncedLine {
    val syllables: List<KaraokeSyllable>
    val translation: String?
    val alignment: KaraokeAlignment
    override val start: Int
    override val end: Int
    val phonetic: String?

    fun progress(current: Int): Float {
        return when {
            current < start -> 0f
            isFocused(current) -> (current - start).toFloat() / duration
            current > end -> 1f
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    fun isFocused(current: Int): Boolean {
        return current in start..end
    }

    fun List<KaraokeSyllable>.contents(): String {
        return this.joinToString("") { it.content }
    }

    data class MainKaraokeLine(
        override val syllables: List<KaraokeSyllable>,
        override val translation: String?,
        override val alignment: KaraokeAlignment,
        override val start: Int,
        override val end: Int,
        override val phonetic: String? = null,
        val accompanimentLines: List<AccompanimentKaraokeLine>? = null
    ) : KaraokeLine {

        init {
            require(end >= start)
        }

        override val duration = end - start

    }

    data class AccompanimentKaraokeLine(
        override val syllables: List<KaraokeSyllable>,
        override val translation: String?,
        override val alignment: KaraokeAlignment,
        override val start: Int,
        override val end: Int,
        override val phonetic: String? = null
    ) : KaraokeLine {
        init {
            require(end >= start)
        }

        override val duration = end - start
    }
}

fun KaraokeLine.copy(
    syllables: List<KaraokeSyllable> = this.syllables,
    translation: String? = this.translation,
    alignment: KaraokeAlignment = this.alignment,
    start: Int = this.start,
    end: Int = this.end,
    phonetic: String? = this.phonetic
): KaraokeLine = when (this) {
    is KaraokeLine.MainKaraokeLine -> this.copy(
        syllables = syllables,
        translation = translation,
        alignment = alignment,
        start = start,
        end = end,
        phonetic = phonetic
    )

    is KaraokeLine.AccompanimentKaraokeLine -> this.copy(
        syllables = syllables,
        translation = translation,
        alignment = alignment,
        start = start,
        end = end,
        phonetic = phonetic
    )
}
