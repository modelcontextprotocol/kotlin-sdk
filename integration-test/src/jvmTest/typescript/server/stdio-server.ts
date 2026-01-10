import {McpServer} from '@modelcontextprotocol/sdk/server/mcp';
import {StdioServerTransport} from '@modelcontextprotocol/sdk/server/stdio';
import {registerTestUtils} from './server-common';

async function main() {
    const server = new McpServer({
        name: 'simple-stdio-server',
        version: '1.0.0',
    }, {capabilities: {logging: {}}});

    registerTestUtils(server);

    const transport = new StdioServerTransport();
    await server.connect(transport);
}

main().catch((err) => {
    console.error('Failed to start stdio server:', err);
    process.exit(1);
});
