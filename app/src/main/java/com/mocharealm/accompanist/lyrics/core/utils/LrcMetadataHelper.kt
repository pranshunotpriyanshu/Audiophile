package com.mocharealm.accompanist.lyrics.core.utils

import com.mocharealm.accompanist.lyrics.core.model.Attributes

object LrcMetadataHelper {

    private val METADATA_TAGS = setOf("ar", "ti", "al", "offset", "length")

    // This regex is still good for finding the general [tag:value] pattern.
    private val attributeParser = Regex("""^\[([a-zA-Z]+):\s*(.*)\]\s*$""")

    fun parse(lines: List<String>): Attributes {
        val attributesMap = lines.mapNotNull { line ->
            attributeParser.find(line)?.destructured?.let { (tag, value) ->
                if (tag in METADATA_TAGS) {
                    tag.trim() to value.trim()
                } else {
                    null
                }
            }
        }.toMap()

        return Attributes(
            artist = attributesMap["ar"],
            title = attributesMap["ti"],
            album = attributesMap["al"],
            offset = attributesMap["offset"]?.toIntOrNull() ?: 0,
            duration = attributesMap["length"]?.toIntOrNull() ?: 0
        )
    }

    fun removeAttributes(lines: List<String>): List<String> {
        return lines.filterNot { line ->
            // Try to parse the line as a generic attribute.
            attributeParser.find(line)?.destructured?.let { (tag, _) ->
                tag in METADATA_TAGS
            } == true // If it doesn't match the pattern at all, keep it.
        }
    }
}
