package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

private data class TestFeature(override val key: FeatureKey, val payload: String = "") : Feature

private class RecordingListener : FeatureListener {
    val updatedKeys = mutableListOf<String>()

    override fun onFeatureUpdated(featureKey: String) {
        updatedKeys.add(featureKey)
    }
}

class FeatureRegistryTest {

    private val registry = FeatureRegistry<TestFeature>("Tool")
    private val listener = RecordingListener()

    init {
        registry.addListener(listener)
    }

    @Test
    fun `add should register feature and notify listener`() {
        val feature = TestFeature("a")

        registry.add(feature)

        registry.values shouldBe mapOf("a" to feature)
        listener.updatedKeys shouldBe listOf("a")
    }

    @Test
    fun `add should throw when key is already registered`() {
        registry.add(TestFeature("a"))

        val exception = assertFailsWith<IllegalArgumentException> {
            registry.add(TestFeature("a", payload = "override"))
        }

        exception.message shouldBe "Tool \"a\" is already registered. Remove it first to replace it."
    }

    @Test
    fun `add should keep original feature and not notify when duplicate is rejected`() {
        val original = TestFeature("a", payload = "original")
        registry.add(original)

        assertFailsWith<IllegalArgumentException> {
            registry.add(TestFeature("a", payload = "override"))
        }

        registry.values shouldBe mapOf("a" to original)
        listener.updatedKeys shouldBe listOf("a")
    }

    @Test
    fun `addAll should register all features and notify per feature`() {
        val a = TestFeature("a")
        val b = TestFeature("b")

        registry.addAll(listOf(a, b))

        registry.values shouldBe mapOf("a" to a, "b" to b)
        listener.updatedKeys shouldBe listOf("a", "b")
    }

    @Test
    fun `addAll should throw on duplicate keys within batch and register nothing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            registry.addAll(listOf(TestFeature("a"), TestFeature("b"), TestFeature("a")))
        }

        exception.message shouldBe "Duplicate Tool keys in batch: [a]"
        registry.values shouldBe emptyMap<FeatureKey, TestFeature>()
        listener.updatedKeys shouldBe emptyList<String>()
    }

    @Test
    fun `addAll should throw when any key is already registered and register nothing`() {
        val existing = TestFeature("existing")
        registry.add(existing)

        val exception = assertFailsWith<IllegalArgumentException> {
            registry.addAll(listOf(TestFeature("new"), TestFeature("existing", payload = "override")))
        }

        exception.message shouldBe "Tools already registered: [existing]. Remove them first to replace them."
        registry.values shouldBe mapOf("existing" to existing)
        listener.updatedKeys shouldBe listOf("existing")
    }

    @Test
    fun `add should succeed after the existing feature is removed`() {
        registry.add(TestFeature("a", payload = "original"))
        registry.remove("a")

        registry.add(TestFeature("a", payload = "replacement"))

        registry.values shouldBe mapOf("a" to TestFeature("a", payload = "replacement"))
        listener.updatedKeys shouldBe listOf("a", "a", "a")
    }
}
