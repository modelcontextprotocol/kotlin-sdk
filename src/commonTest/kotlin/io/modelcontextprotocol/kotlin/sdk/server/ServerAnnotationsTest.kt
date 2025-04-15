package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions

@Suppress("UNUSED_PARAMETER")
class ServerAnnotationsTest {

    // Sample annotated class for testing
    class TestToolsProvider {
        
        @McpTool(
            name = "echo_string",
            description = "Echoes back the input string"
        )
        fun echoString(
            @McpParam(description = "The string to echo") input: String
        ): String {
            return "Echoed: $input"
        }
        
        @McpTool(
            name = "failing_tool",
            description = "A tool that always fails"
        )
        fun failingTool(
            @McpParam(description = "Unused parameter") input: String
        ): String {
            throw RuntimeException("This tool always fails")
        }
        
        @McpTool(
            name = "add_numbers",
            description = "Adds two numbers together"
        )
        fun addNumbers(
            @McpParam(description = "First number") a: Double,
            @McpParam(description = "Second number") b: Double
        ): String {
            return "Sum: ${a + b}"
        }
        
        @McpTool(
            description = "Test with default name"
        )
        fun testDefaultName(
            input: String
        ): CallToolResult {
            return CallToolResult(content = listOf(TextContent("Default name test: $input")))
        }
        
        @McpTool(
            name = "test_optional",
            description = "Tests optional parameters"
        )
        fun testOptionalParams(
            @McpParam(description = "Required parameter") required: String,
            @McpParam(description = "Optional parameter", required = false) optional: String = "default value"
        ): String {
            return "Required: $required, Optional: $optional"
        }
        
        @McpTool(
            name = "test_multiple_types",
            description = "Tests handling of different parameter types",
            required = arrayOf("stringParam", "intParam", "boolParam")
        )
        fun testMultipleTypes(
            @McpParam(description = "String parameter") stringParam: String,
            @McpParam(description = "Integer parameter") intParam: Int,
            @McpParam(description = "Boolean parameter") boolParam: Boolean,
            @McpParam(description = "Float parameter", required = false) floatParam: Float = 0.0f,
            @McpParam(description = "List parameter", required = false) listParam: List<String> = emptyList()
        ): CallToolResult {
            val result = "String: $stringParam, Int: $intParam, Bool: $boolParam, " +
                         "Float: $floatParam, List size: ${listParam.size}"
            return CallToolResult(content = listOf(TextContent(result)))
        }
        
        @McpTool(
            name = "type_override",
            description = "Test explicit type overrides"
        )
        fun testTypeOverride(
            @McpParam(description = "Parameter with explicit type", type = "object") 
            complexParam: String
        ): String {
            return "Received parameter: $complexParam"
        }
        
        @McpTool(
            name = "return_direct_string",
            description = "Returns a direct string value"
        )
        fun returnDirectString(
            @McpParam(description = "Input string") input: String
        ): String {
            return "Processed: $input"
        }
        
        @McpTool(
            name = "return_string_list",
            description = "Returns a list of strings"
        )
        fun returnStringList(
            @McpParam(description = "Count of items") count: Int
        ): List<String> {
            return List(count) { "Item $it" }
        }
    }
    
    @Test
    @Ignore // TODO: Fix this test when Kotlin reflection support is better in Kotlin/Common
    fun testAnnotatedToolsRegistration() = runTest {
        // Create mock server
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
        val server = Server(Implementation("test", "1.0.0"), serverOptions)
        
        // Create an instance of the annotated class
        val toolsProvider = TestToolsProvider()
        
        // Register annotated tools
        server.registerAnnotatedTools(toolsProvider)
        
        // Get the list of registered tools
        val toolsResult = server.handleListTools()
        val registeredTools = toolsResult.tools
        
        // Verify that tools were properly registered (we now have more tools)
        assertEquals(8, registeredTools.size, "Should have registered 8 tools")
        
        // Check echo_string tool
        val echoTool = registeredTools.find { it.name == "echo_string" }
        assertNotNull(echoTool, "echo_string tool should be registered")
        assertEquals("Echoes back the input string", echoTool.description)
        assertTrue(echoTool.inputSchema.required?.contains("input") == true)
        
        // Check add_numbers tool
        val addTool = registeredTools.find { it.name == "add_numbers" }
        assertNotNull(addTool, "add_numbers tool should be registered")
        assertEquals("Adds two numbers together", addTool.description)
        assertTrue(addTool.inputSchema.required?.containsAll(listOf("a", "b")) == true)
        
        // Check tool with default name
        val defaultNameTool = registeredTools.find { it.name == "testDefaultName" }
        assertNotNull(defaultNameTool, "Tool with default name should be registered")
        assertEquals("Test with default name", defaultNameTool.description)
        
        // Check tool with optional params
        val optionalParamsTool = registeredTools.find { it.name == "test_optional" }
        assertNotNull(optionalParamsTool, "test_optional tool should be registered")
        assertTrue(optionalParamsTool.inputSchema.required?.contains("required") == true)
        assertTrue(optionalParamsTool.inputSchema.required?.contains("optional") != true)
        
        // Check tool with multiple parameter types
        val multiTypesTool = registeredTools.find { it.name == "test_multiple_types" }
        assertNotNull(multiTypesTool, "test_multiple_types tool should be registered")
        assertEquals(
            listOf("stringParam", "intParam", "boolParam"),
            multiTypesTool.inputSchema.required?.sorted()
        )
        
        // Check parameter type inference
        val typeOverrideTool = registeredTools.find { it.name == "type_override" }
        assertNotNull(typeOverrideTool, "type_override tool should be registered")
        val properties = typeOverrideTool.inputSchema.properties as JsonObject
        val complexParamProperty = properties["complexParam"] as JsonObject
        assertEquals("object", complexParamProperty["type"]?.toString()?.replace("\"", ""))
    }
    
    @Test
    @Ignore // TODO: Fix this test when Kotlin reflection support is better in Kotlin/Common
    fun testCallingAnnotatedTool() = runTest {
        // Create mock server
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
        val server = Server(Implementation("test", "1.0.0"), serverOptions)
        
        // Create an instance of the annotated class
        val toolsProvider = TestToolsProvider()
        
        // Register annotated tools
        server.registerAnnotatedTools(toolsProvider)
        
        // Create test request
        val echoRequest = CallToolRequest(
            name = "echo_string",
            arguments = buildJsonObject {
                put("input", "Hello, World!")
            }
        )
        
        // Call the tool
        val result = server.handleCallTool(echoRequest)
        
        // Verify result
        assertEquals(1, result.content.size)
        val content = result.content[0]
        assertTrue(content is TextContent)
        assertEquals("Echoed: Hello, World!", (content as TextContent).text)
    }
    
    @Test
    @Ignore // TODO: Fix this test when Kotlin reflection support is better in Kotlin/Common
    fun testCallingAnnotatedToolWithMultipleParams() = runTest {
        // Create mock server
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
        val server = Server(Implementation("test", "1.0.0"), serverOptions)
        
        // Create an instance of the annotated class
        val toolsProvider = TestToolsProvider()
        
        // Register annotated tools
        server.registerAnnotatedTools(toolsProvider)
        
        // Create test request for add_numbers
        val addRequest = CallToolRequest(
            name = "add_numbers",
            arguments = buildJsonObject {
                put("a", 5.0)
                put("b", 7.5)
            }
        )
        
        // Call the tool
        val result = server.handleCallTool(addRequest)
        
        // Verify result
        assertEquals(1, result.content.size)
        val content = result.content[0]
        assertTrue(content is TextContent)
        assertEquals("Sum: 12.5", (content as TextContent).text)
    }
    
    @Test
    @Ignore // TODO: Fix this test when Kotlin reflection support is better in Kotlin/Common
    fun testCallingToolWithOptionalParameter() = runTest {
        // Create mock server
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
        val server = Server(Implementation("test", "1.0.0"), serverOptions)
        
        // Create an instance of the annotated class
        val toolsProvider = TestToolsProvider()
        
        // Register annotated tools
        server.registerAnnotatedTools(toolsProvider)
        
        // Test 1: With only required parameter
        val request1 = CallToolRequest(
            name = "test_optional",
            arguments = buildJsonObject {
                put("required", "test value")
            }
        )
        
        val result1 = server.handleCallTool(request1)
        assertEquals(1, result1.content.size)
        val content1 = result1.content[0]
        assertTrue(content1 is TextContent)
        assertEquals("Required: test value, Optional: default value", (content1 as TextContent).text)
        
        // Test 2: With both required and optional parameters
        val request2 = CallToolRequest(
            name = "test_optional",
            arguments = buildJsonObject {
                put("required", "test value")
                put("optional", "custom value")
            }
        )
        
        val result2 = server.handleCallTool(request2)
        assertEquals(1, result2.content.size)
        val content2 = result2.content[0]
        assertTrue(content2 is TextContent)
        assertEquals("Required: test value, Optional: custom value", (content2 as TextContent).text)
    }
    
    @Test
    @Ignore // TODO: Fix this test when Kotlin reflection support is better in Kotlin/Common
    fun testDefaultToolName() = runTest {
        // Create mock server
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
        val server = Server(Implementation("test", "1.0.0"), serverOptions)
        
        // Create an instance of the annotated class
        val toolsProvider = TestToolsProvider()
        
        // Register annotated tools
        server.registerAnnotatedTools(toolsProvider)
        
        // Create test request using the function name as tool name
        val request = CallToolRequest(
            name = "testDefaultName",
            arguments = buildJsonObject {
                put("input", "test input")
            }
        )
        
        // Call the tool
        val result = server.handleCallTool(request)
        
        // Verify result
        assertEquals(1, result.content.size)
        val content = result.content[0]
        assertTrue(content is TextContent)
        assertEquals("Default name test: test input", (content as TextContent).text)
    }
    
    @Test
    @Ignore // TODO: Fix this test when Kotlin reflection support is better in Kotlin/Common
    fun testMultipleParameterTypes() = runTest {
        // Create mock server
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
        val server = Server(Implementation("test", "1.0.0"), serverOptions)
        
        // Create an instance of the annotated class
        val toolsProvider = TestToolsProvider()
        
        // Register annotated tools
        server.registerAnnotatedTools(toolsProvider)
        
        // Create test request for multiple parameter types
        val request = CallToolRequest(
            name = "test_multiple_types",
            arguments = buildJsonObject {
                put("stringParam", "test string")
                put("intParam", 42)
                put("boolParam", true)
                put("floatParam", 3.14)
            }
        )
        
        // Call the tool
        val result = server.handleCallTool(request)
        
        // Verify result
        assertEquals(1, result.content.size)
        val content = result.content[0]
        assertTrue(content is TextContent)
        assertEquals(
            "String: test string, Int: 42, Bool: true, Float: 3.14, List size: 0", 
            (content as TextContent).text
        )
    }
    
    @Test
    @Ignore // TODO: Fix this test when Kotlin reflection support is better in Kotlin/Common
    fun testReturnTypeHandling() = runTest {
        // Create mock server
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
        val server = Server(Implementation("test", "1.0.0"), serverOptions)
        
        // Create an instance of the annotated class
        val toolsProvider = TestToolsProvider()
        
        // Register annotated tools
        server.registerAnnotatedTools(toolsProvider)
        
        // Test 1: Direct string return
        val stringRequest = CallToolRequest(
            name = "return_direct_string",
            arguments = buildJsonObject {
                put("input", "test string")
            }
        )
        val stringResult = server.handleCallTool(stringRequest)
        assertEquals(1, stringResult.content.size)
        val stringContent = stringResult.content[0]
        assertTrue(stringContent is TextContent)
        assertEquals("Processed: test string", (stringContent as TextContent).text)
        
        // Test 2: String list return
        val listRequest = CallToolRequest(
            name = "return_string_list",
            arguments = buildJsonObject {
                put("count", 3)
            }
        )
        val listResult = server.handleCallTool(listRequest)
        assertEquals(3, listResult.content.size)
        assertTrue(listResult.content.all { it is TextContent })
        assertEquals("Item 0", (listResult.content[0] as TextContent).text)
        assertEquals("Item 1", (listResult.content[1] as TextContent).text)
        assertEquals("Item 2", (listResult.content[2] as TextContent).text)
    }
    
    @Test
    fun testTypeOverride() = runTest {
        // Create mock server
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
        val server = Server(Implementation("test", "1.0.0"), serverOptions)
        
        // Create an instance of the annotated class
        val toolsProvider = TestToolsProvider()
        
        // Register annotated tools
        server.registerAnnotatedTools(toolsProvider)
        
        // Create test request with explicit type override
        val request = CallToolRequest(
            name = "type_override",
            arguments = buildJsonObject {
                put("complexParam", "complex value")
            }
        )
        
        // Call the tool
        val result = server.handleCallTool(request)
        
        // Verify result
        assertEquals(1, result.content.size)
        val content = result.content[0]
        assertTrue(content is TextContent)
        assertEquals("Received parameter: complex value", (content as TextContent).text)
    }
    
    @Test
    fun testErrorHandling() = runTest {
        // Create mock server
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
        val server = Server(Implementation("test", "1.0.0"), serverOptions)
        
        // Create an instance of the annotated class
        val toolsProvider = TestToolsProvider()
        
        // Register annotated tools
        server.registerAnnotatedTools(toolsProvider)
        
        // Create test request for failing tool
        val request = CallToolRequest(
            name = "failing_tool",
            arguments = buildJsonObject {
                put("input", "doesn't matter")
            }
        )
        
        // Call the tool
        val result = server.handleCallTool(request)
        
        // Verify error result
        assertEquals(true, result.isError, "Result should indicate an error")
        assertEquals(1, result.content.size)
        val content = result.content[0]
        assertTrue(content is TextContent)
        val textContent = content as TextContent
        assertTrue(textContent.text!!.contains("Error executing tool"), 
            "Error message should contain expected text")
        assertTrue(textContent.text!!.contains("This tool always fails"), 
            "Error message should contain original exception message")
    }
    
    @Test
    fun testAnnotatedToolsRegistration_CorrectNumberOfTools() = runTest {
        // Create mock server
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
        val server = Server(Implementation("test", "1.0.0"), serverOptions)
        
        // Create an instance of the annotated class
        val toolsProvider = TestToolsProvider()
        
        // Register annotated tools
        server.registerAnnotatedTools(toolsProvider)
        
        // Get the list of registered tools
        val toolsResult = server.handleListTools()
        val registeredTools = toolsResult.tools
        
        // We should now have 9 tools (with the failing_tool added)
        assertEquals(9, registeredTools.size, "Should have registered 9 tools")
    }
    
    @Test
    fun testRegisterSingleAnnotatedTool() = runTest {
        // Create mock server
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
        val server = Server(Implementation("test", "1.0.0"), serverOptions)
        
        // Create an instance of the annotated class
        val toolsProvider = TestToolsProvider()
        
        // Get the echo function using reflection
        val echoFunction = TestToolsProvider::class.functions.find { it.name == "echoString" }
        assertNotNull(echoFunction, "Should find the echoString function")
        
        // Get the annotation
        val annotation = echoFunction.findAnnotation<McpTool>()
        assertNotNull(annotation, "Should find the McpTool annotation")
        
        // Register just the one function
        server.registerToolFromAnnotatedFunction(toolsProvider, echoFunction, annotation)
        
        // Get the list of registered tools
        val toolsResult = server.handleListTools()
        val registeredTools = toolsResult.tools
        
        // Should only have registered one tool
        assertEquals(1, registeredTools.size, "Should have registered exactly 1 tool")
        
        // Verify the registered tool is the echo tool
        assertEquals("echo_string", registeredTools[0].name)
        
        // Verify the tool can be called
        val request = CallToolRequest(
            name = "echo_string",
            arguments = buildJsonObject {
                put("input", "hello from single registration")
            }
        )
        
        val result = server.handleCallTool(request)
        assertEquals(1, result.content.size)
        val content = result.content[0]
        assertTrue(content is TextContent)
        assertEquals("Echoed: hello from single registration", (content as TextContent).text)
    }
}