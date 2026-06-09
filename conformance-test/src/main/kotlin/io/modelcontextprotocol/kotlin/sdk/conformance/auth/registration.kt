package io.modelcontextprotocol.kotlin.sdk.conformance.auth

import io.modelcontextprotocol.kotlin.sdk.conformance.scenarioHandlers

// Registration
fun registerAuthScenarios() {
    val authScenarios = listOf(
        "auth/metadata-default",
        "auth/metadata-var1",
        "auth/metadata-var2",
        "auth/metadata-var3",
        "auth/basic-cimd",
        "auth/scope-from-www-authenticate",
        "auth/scope-from-scopes-supported",
        "auth/scope-omitted-when-undefined",
        "auth/scope-step-up",
        "auth/scope-retry-limit",
        "auth/token-endpoint-auth-basic",
        "auth/token-endpoint-auth-post",
        "auth/token-endpoint-auth-none",
        "auth/resource-mismatch",
        "auth/pre-registration",
        "auth/2025-03-26-oauth-metadata-backcompat",
        "auth/2025-03-26-oauth-endpoint-fallback",
    )
    for (name in authScenarios) {
        scenarioHandlers[name] = ::runAuthClient
    }
    scenarioHandlers["auth/client-credentials-jwt"] = ::runClientCredentialsJwt
    scenarioHandlers["auth/client-credentials-basic"] = ::runClientCredentialsBasic
    scenarioHandlers["auth/cross-app-access-complete-flow"] = ::runCrossAppAccess
    // SEP-990 scenarios that exercise the EnterpriseAuthProvider Ktor plugin and the
    // discoverAndRequestJwtAuthorizationGrant combined call.
    scenarioHandlers["auth/cross-app-access-enterprise-auth-provider"] =
        ::runCrossAppAccessViaEnterpriseAuthProvider
    scenarioHandlers["auth/cross-app-access-discover-and-request"] =
        ::runCrossAppAccessViaDiscoverAndRequest
}
