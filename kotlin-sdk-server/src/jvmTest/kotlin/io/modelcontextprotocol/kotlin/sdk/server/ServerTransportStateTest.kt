package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("ServerTransportState")
class ServerTransportStateTest {

    @Nested
    @DisplayName("Valid Transitions")
    inner class ValidTransitions {

        @ParameterizedTest
        @CsvSource(
            "New, Initializing",
            "New, Stopped",
            "Initializing, Active",
            "Initializing, InitializationFailed",
            "Active, ShuttingDown",
            "ShuttingDown, Stopped",
            "ShuttingDown, ShutdownFailed",
        )
        fun `should allow valid transitions`(fromName: String, toName: String) {
            val from = ServerTransportState.valueOf(fromName)
            val to = ServerTransportState.valueOf(toName)

            val allowed = ServerTransportState.VALID_TRANSITIONS.getValue(from)
            (to in allowed) shouldBe true
        }
    }

    @Nested
    @DisplayName("Invalid Transitions")
    inner class InvalidTransitions {

        @ParameterizedTest
        @CsvSource(
            "New, Active",
            "New, InitializationFailed",
            "New, ShuttingDown",
            "New, ShutdownFailed",
            "Initializing, New",
            "Initializing, ShuttingDown",
            "Initializing, Stopped",
            "Active, New",
            "Active, Initializing",
            "Active, Active",
            "Active, Stopped",
            "ShuttingDown, New",
            "ShuttingDown, Active",
            "ShuttingDown, ShuttingDown",
        )
        fun `should reject invalid transitions`(fromName: String, toName: String) {
            val from = ServerTransportState.valueOf(fromName)
            val to = ServerTransportState.valueOf(toName)

            val allowed = ServerTransportState.VALID_TRANSITIONS.getValue(from)
            (to in allowed) shouldBe false
        }
    }

    @Nested
    @DisplayName("Terminal States")
    inner class TerminalStates {

        @ParameterizedTest
        @EnumSource(
            value = ServerTransportState::class,
            names = ["InitializationFailed", "Stopped", "ShutdownFailed"],
        )
        fun `terminal states should have no outgoing transitions`(state: ServerTransportState) {
            ServerTransportState.VALID_TRANSITIONS.getValue(state).shouldBeEmpty()
        }
    }
}
