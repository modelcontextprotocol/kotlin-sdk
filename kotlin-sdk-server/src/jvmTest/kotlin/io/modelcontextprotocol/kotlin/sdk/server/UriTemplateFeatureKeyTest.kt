package io.modelcontextprotocol.kotlin.sdk.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UriTemplateFeatureKeyTest {
    @Test
    fun testParseUriWithoutTemplate() {
        val key = "schema://resource"
        val featureKey = UriTemplateFeatureKey(key)
        assertEquals("schema://resource", featureKey.key)
        assertEquals("^schema://resource/?$", featureKey.value.pattern)

        val keyWithSlash = "schema://resource/"
        val featureKeyWithSlash = UriTemplateFeatureKey(keyWithSlash)
        assertEquals("schema://resource/", featureKeyWithSlash.key)
        assertEquals("^schema://resource/?$", featureKeyWithSlash.value.pattern)
    }

    @Test
    fun testParseUriWithSingleVariable() {
        val key = "schema://resource/{resourceId}"
        val featureKey = UriTemplateFeatureKey(key)
        assertEquals(key, featureKey.key)
        assertEquals("^schema://resource/(?<resourceId>[^/]+)/?$", featureKey.value.pattern)

        val receivedValidUri = "schema://resource/123"
        assertTrue(featureKey.matches(receivedValidUri))
        val parsedArguments = featureKey.extractArguments(receivedValidUri)
        assertEquals(mapOf("resourceId" to "123"), parsedArguments)

        val receivedValidUri2 = "schema://resource/123/"
        assertTrue(featureKey.matches(receivedValidUri2))
        val parsedArguments2 = featureKey.extractArguments(receivedValidUri2)
        assertEquals(mapOf("resourceId" to "123"), parsedArguments2)

        val receivedInvalidUri = "schema://resource2/123"
        assertFalse(featureKey.matches(receivedInvalidUri))
        val receivedInvalidUri2 = "schema://resource/123/abc"
        assertFalse(featureKey.matches(receivedInvalidUri2))
    }

    @Test
    fun testParseUriWithExplodeSingleVariable() {
        val key = "schema://resource/{resourceId*}"
        val featureKey = UriTemplateFeatureKey(key)
        assertEquals(key, featureKey.key)
        assertEquals("^schema://resource/(?<resourceId>.+)/?$", featureKey.value.pattern)

        val receivedValidUri = "schema://resource/123"
        assertTrue(featureKey.matches(receivedValidUri))
        val parsedArguments = featureKey.extractArguments(receivedValidUri)
        assertEquals(mapOf("resourceId" to "123"), parsedArguments)

        val receivedValidUri2 = "schema://resource/123/"
        assertTrue(featureKey.matches(receivedValidUri2))
        val parsedArguments2 = featureKey.extractArguments(receivedValidUri2)
        assertEquals(mapOf("resourceId" to "123"), parsedArguments2)

        val receivedValidUri3 = "schema://resource/123/abc"
        assertTrue(featureKey.matches(receivedValidUri3))
        val parsedArguments3 = featureKey.extractArguments(receivedValidUri3)
        assertEquals(mapOf("resourceId" to "123/abc"), parsedArguments3)

        val receivedValidUri4 = "schema://resource/123/abc/"
        assertTrue(featureKey.matches(receivedValidUri4))
        val parsedArguments4 = featureKey.extractArguments(receivedValidUri4)
        assertEquals(mapOf("resourceId" to "123/abc"), parsedArguments4)

        val receivedInvalidUri = "schema://resource2/123"
        assertFalse(featureKey.matches(receivedInvalidUri))
    }
}
