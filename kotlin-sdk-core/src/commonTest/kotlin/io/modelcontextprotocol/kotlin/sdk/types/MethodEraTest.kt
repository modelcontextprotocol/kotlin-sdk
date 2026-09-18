package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertTrue

class MethodEraTest {

    @Test
    fun `every defined method should declare where it exists`() {
        Method.Defined.entries.forEach { method ->
            assertTrue(method.eras.isNotEmpty(), "${method.value} declares no protocol era")
        }
    }

    @Test
    fun `the removed surface should be reachable only through the handshake`() {
        val legacyOnly = setOf(
            Method.Defined.Initialize,
            Method.Defined.NotificationsInitialized,
            Method.Defined.Ping,
            Method.Defined.LoggingSetLevel,
            Method.Defined.ResourcesSubscribe,
            Method.Defined.ResourcesUnsubscribe,
            Method.Defined.NotificationsResourcesUpdated,
            Method.Defined.NotificationsRootsListChanged,
            Method.Defined.NotificationsElicitationComplete,
            Method.Defined.SamplingCreateMessage,
            Method.Defined.RootsList,
            Method.Defined.ElicitationCreate,
            Method.Defined.TasksGet,
            Method.Defined.TasksResult,
            Method.Defined.TasksList,
            Method.Defined.TasksCancel,
            Method.Defined.NotificationsTasksStatus,
        )

        legacyOnly.forEach { method ->
            assertTrue(method.isAvailableIn(ProtocolEra.Legacy), "${method.value} left the handshake era")
            assertTrue(!method.isAvailableIn(ProtocolEra.Modern), "${method.value} survived into the modern era")
        }
    }

    @Test
    fun `discovery should exist only in the request-scoped lifecycle`() {
        Method.Defined.ServerDiscover.isAvailableIn(ProtocolEra.Modern) shouldBe true
        Method.Defined.ServerDiscover.isAvailableIn(ProtocolEra.Legacy) shouldBe false
    }

    @Test
    fun `the surface that survived the transition should exist in both lifecycles`() {
        val both = setOf(
            Method.Defined.ToolsList,
            Method.Defined.ToolsCall,
            Method.Defined.PromptsList,
            Method.Defined.PromptsGet,
            Method.Defined.ResourcesList,
            Method.Defined.ResourcesTemplatesList,
            Method.Defined.ResourcesRead,
            Method.Defined.CompletionComplete,
            Method.Defined.NotificationsCancelled,
            Method.Defined.NotificationsProgress,
            Method.Defined.NotificationsMessage,
            Method.Defined.NotificationsToolsListChanged,
            Method.Defined.NotificationsPromptsListChanged,
            Method.Defined.NotificationsResourcesListChanged,
        )

        both.forEach { method ->
            ProtocolEra.entries.forEach { era ->
                assertTrue(method.isAvailableIn(era), "${method.value} is absent from $era")
            }
        }
    }

    @Test
    fun `the eras should partition the method table`() {
        val counted = Method.Defined.entries.groupingBy { it.eras }.eachCount()

        counted.values.sum() shouldBe Method.Defined.entries.size
    }

    @Test
    fun `a custom method should route in either lifecycle`() {
        // The protocol says nothing about methods it does not define, so no revision removed them.
        val custom = Method.Custom("com.example/doThing")

        ProtocolEra.entries.forEach { era -> custom.isAvailableIn(era) shouldBe true }
    }
}
