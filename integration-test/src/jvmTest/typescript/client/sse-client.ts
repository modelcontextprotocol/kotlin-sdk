import {Client} from '@modelcontextprotocol/sdk/client/index';
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp';

const args = process.argv.slice(2);
const serverUrl = args[0] || 'http://localhost:3001/mcp';
const toolName = args[1];
const toolArgs = args.slice(2);
const PROTOCOL_VERSION = "2024-11-05";

async function main() {
    if (!toolName) {
        console.log('Usage: npx tsx client/sse-client.ts [server-url] <tool-name> [tool-args...]');
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
    });

    const transport = new StreamableHTTPClientTransport(new URL(serverUrl));

    try {
        await client.connect(transport, {protocolVersion: PROTOCOL_VERSION});
        console.log('Connected to server');

        try {
            if (typeof (client as any).on === 'function') {
                (client as any).on('notification', (n: any) => {
                    try {
                        const method = (n && (n.method || (n.params && n.params.method))) || 'unknown';
                        console.log('Notification:', method, JSON.stringify(n));
                    } catch {
                        console.log('Notification: <unparsable>');
                    }
                });
            }
        } catch {
        }

        const toolsResult = await client.listTools();
        const tools = toolsResult.tools;
        console.log('Available utils:', tools.map((t: { name: any; }) => t.name).join(', '));

        if (!toolName) {
            await client.close();
            return;
        }

        const tool = tools.find((t: { name: string; }) => t.name === toolName);
        if (!tool) {
            console.error(`Tool "${toolName}" not found`);
            // @ts-ignore
            process.exit(1);
        }

        const toolArguments = {};

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
        // @ts-ignore
        process.exit(1);
    } finally {
        await client.close();
        console.log('Disconnected from server');
    }
}

main().catch(error => {
    console.error('Unhandled error:', error);
    // @ts-ignore
    process.exit(1);
});
