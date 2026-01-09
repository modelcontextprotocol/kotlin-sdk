package io.modelcontextprotocol.kotlin.sdk.integration.utils

import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
annotation class DisabledOnCI
