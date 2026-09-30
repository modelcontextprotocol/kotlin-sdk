import {McpServer} from '@modelcontextprotocol/sdk/server/mcp';
import {StdioServerTransport} from '@modelcontextprotocol/sdk/server/stdio';
import {z} from 'zod';

async function main() {
    const server = new McpServer({name: 'typescript-stdio-server', version: '1.0.0'});

    server.registerTool(
        'greet',
        {
            description: 'Greets the caller by name',
            inputSchema: {name: z.string().describe('Name to greet')},
        },
        async ({name}) => ({content: [{type: 'text', text: `Hello, ${name}!`}]}),
    );

    await server.connect(new StdioServerTransport());
}

main().catch((err) => {
    console.error('Failed to start stdio server:', err);
    process.exit(1);
});
