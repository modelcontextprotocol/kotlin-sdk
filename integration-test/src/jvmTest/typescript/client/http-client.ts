import {Client} from '@modelcontextprotocol/sdk/client/index';
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp';

const args = process.argv.slice(2);
const serverUrl = args[0] || 'http://localhost:3000/mcp';
const toolName = args[1];
const toolArgs = args.slice(2);
const PROTOCOL_VERSION = "2025-11-25";

async function main() {
    if (!toolName) {
        console.log('Usage: npx tsx client/http-client.ts [server-url] <tool-name> [tool-args...]');
        console.log('Using default server URL:', serverUrl);
        console.log('Available utils will be listed after connection');
    }

    console.log(`Connecting to server at ${serverUrl}`);
    if (toolName) {
        console.log(`Will call tool: ${toolName} with args: ${toolArgs.join(', ')}`);
    }

    const client = new Client({
        name: 'test-client',
        version: '1.0.0'
    }, {
        fallbackNotificationHandler: async (notification) => {
            console.log('Notification:', notification.method, JSON.stringify(notification));
        }
    });

    const transport = new StreamableHTTPClientTransport(new URL(serverUrl));

    try {
        await client.connect(transport, {protocolVersion: PROTOCOL_VERSION});
        console.log('Connected to server');

        const toolsResult = await client.listTools();
        const tools = toolsResult.tools;
        console.log('Available utils:', tools.map((t) => t.name).join(', '));

        if (!toolName) {
            return;
        }

        const tool = tools.find((t) => t.name === toolName);
        if (!tool) {
            throw new Error(`Tool "${toolName}" not found`);
        }

        const toolArguments: Record<string, string> = {};

        if (toolName === "greet" && toolArgs.length > 0) {
            toolArguments["name"] = toolArgs[0];
        } else if (tool.inputSchema && tool.inputSchema.properties) {
            const propNames = Object.keys(tool.inputSchema.properties);
            if (propNames.length > 0 && toolArgs.length > 0) {
                toolArguments[propNames[0]] = toolArgs[0];
            }
        }

        console.log(`Calling tool ${toolName} with arguments:`, toolArguments);

        const result = await client.callTool({
            name: toolName,
            arguments: toolArguments
        });
        console.log('Tool result:', result);

        if (result.content) {
            for (const content of result.content) {
                if (content.type === 'text') {
                    console.log('Text content:', content.text);
                }
            }
        }

        if (result.structuredContent) {
            console.log('Structured content:', JSON.stringify(result.structuredContent, null, 2));
        }

    } catch (error) {
        console.error('Error:', error);
        process.exitCode = 1;
    } finally {
        await client.close();
        console.log('Disconnected from server');
    }
}

main().catch(error => {
    console.error('Unhandled error:', error);
    process.exit(1);
});
