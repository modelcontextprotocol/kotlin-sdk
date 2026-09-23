package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import kotlin.test.Test
import kotlin.test.assertEquals

class ResourceNotFoundErrorCodeTest {

    @Test
    fun `uses legacy code before 2026 protocol`() {
        assertEquals(RPCError.ErrorCode.RESOURCE_NOT_FOUND, resourceNotFoundErrorCode(null))
        assertEquals(RPCError.ErrorCode.RESOURCE_NOT_FOUND, resourceNotFoundErrorCode("2025-11-25"))
    }

    @Test
    fun `uses invalid params from 2026 protocol onward`() {
        assertEquals(RPCError.ErrorCode.INVALID_PARAMS, resourceNotFoundErrorCode("2026-07-28"))
        assertEquals(RPCError.ErrorCode.INVALID_PARAMS, resourceNotFoundErrorCode("2027-01-01"))
    }
}
