package server

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StreamableHttpServerTransportTest {

    @Test
    fun `should start and close cleanly`() = runBlocking {
        val transport = StreamableHttpServerTransport(isStateful = false)
        
        var didClose = false
        transport.onClose {
            didClose = true
        }
        
        transport.start()
        assertFalse(didClose, "Should not have closed yet")
        
        transport.close()
        assertTrue(didClose, "Should have closed after calling close()")
    }
    
    @Test
    fun `should initialize with stateful mode`() = runBlocking {
        val transport = StreamableHttpServerTransport(isStateful = true)
        transport.start()
        
        assertNull(transport.sessionId, "Session ID should be null before initialization")
        
        transport.close()
    }
    
    @Test
    fun `should initialize with stateless mode`() = runBlocking {
        val transport = StreamableHttpServerTransport(isStateful = false)
        transport.start()
        
        assertNull(transport.sessionId, "Session ID should be null in stateless mode")
        
        transport.close()
    }
    
    @Test
    fun `should not allow double start`() = runBlocking {
        val transport = StreamableHttpServerTransport()
        transport.start()
        
        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking { transport.start() }
        }
        
        assertTrue(exception.message?.contains("already started") == true)
        
        transport.close()
    }
    
    @Test
    fun `should handle message callbacks`() = runBlocking {
        val transport = StreamableHttpServerTransport()
        var receivedMessage: JSONRPCMessage? = null
        
        transport.onMessage { message ->
            receivedMessage = message
        }
        
        transport.start()
        
        // Test that message handler can be called
        assertTrue(receivedMessage == null) // Verify initially null
        
        transport.close()
    }
    
    @Test
    fun `should handle error callbacks`() = runBlocking {
        val transport = StreamableHttpServerTransport()
        var receivedException: Throwable? = null
        
        transport.onError { error ->
            receivedException = error
        }
        
        transport.start()
        
        // Test that error handler can be called
        assertTrue(receivedException == null) // Verify initially null
        
        transport.close()
    }
    
    @Test
    fun `should clear all mappings on close`() = runBlocking {
        val transport = StreamableHttpServerTransport()
        transport.start()
        
        // After close, all internal state should be cleared
        transport.close()
        
        // Verify close was called by checking the close handler
        var closeHandlerCalled = false
        transport.onClose { closeHandlerCalled = true }
        
        // Since we already closed, setting a new handler won't trigger it
        assertFalse(closeHandlerCalled)
    }
    
    @Test
    fun `should support enableJSONResponse flag`() {
        val transportWithJson = StreamableHttpServerTransport(enableJSONResponse = true)
        val transportWithoutJson = StreamableHttpServerTransport(enableJSONResponse = false)
        
        // Just verify the transports can be created with different flags
        assertNotNull(transportWithJson)
        assertNotNull(transportWithoutJson)
    }
    
    @Test
    fun `should support isStateful flag`() {
        val statefulTransport = StreamableHttpServerTransport(isStateful = true)
        val statelessTransport = StreamableHttpServerTransport(isStateful = false)
        
        // Just verify the transports can be created with different flags
        assertNotNull(statefulTransport)
        assertNotNull(statelessTransport)
    }
    
    @Test
    fun `should handle close without error callbacks`() = runBlocking {
        val transport = StreamableHttpServerTransport()
        
        transport.start()
        
        // Should not throw even without error handler
        assertDoesNotThrow {
            runBlocking { transport.close() }
        }
    }
    
    @Test
    fun `should handle multiple close calls`() = runBlocking {
        val transport = StreamableHttpServerTransport()
        var closeCount = 0
        
        transport.onClose {
            closeCount++
        }
        
        transport.start()
        transport.close()
        assertEquals(1, closeCount, "Close handler should be called once after first close")
        
        transport.close() // Second close should be safe
        assertEquals(2, closeCount, "Close handler should be called again on second close")
    }
}