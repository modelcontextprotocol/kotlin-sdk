package io.modelcontextprotocol.kotlin.test.utils

import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
public annotation class DisabledOnCI
