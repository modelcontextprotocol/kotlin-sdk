package org.kotlinlang.mcp.algolia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AlgoliaSearchRequest(
    val query: String,
    val hitsPerPage: Int,
    val attributesToRetrieve: List<String>,
    val attributesToSnippet: List<String>,
)

@Serializable
internal data class AlgoliaSearchResponse(
    val hits: List<AlgoliaHit>,
)

@Serializable
internal data class AlgoliaHit(
    val objectID: String,
    val mainTitle: String? = null,
    val url: String,
    val headings: String? = null,
    @SerialName("_snippetResult")
    val snippetResult: SnippetResult? = null,
)

@Serializable
internal data class SnippetResult(
    val content: SnippetValue,
)

@Serializable
internal data class SnippetValue(
    val value: String,
    val matchLevel: String,
)
