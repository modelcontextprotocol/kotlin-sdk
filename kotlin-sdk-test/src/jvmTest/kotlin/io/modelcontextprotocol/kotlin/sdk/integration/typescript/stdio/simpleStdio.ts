// @ts-nocheck
import { z } from 'zod';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const SDK_DIR = process.env.TYPESCRIPT_SDK_DIR;
if (!SDK_DIR) {
  throw new Error('TYPESCRIPT_SDK_DIR environment variable is not set. It should point to the cloned typescript-sdk directory.');
}

async function importFromSdk(rel: string): Promise<any> {
  const full = path.resolve(SDK_DIR!, rel);
  const url = pathToFileURL(full).href;
  return await import(url);
}

async function main() {
  const { McpServer } = await importFromSdk('src/server/mcp.ts');
  const { StdioServerTransport } = await importFromSdk('src/server/stdio.ts');

  const server = new McpServer({
    name: 'simple-stdio-server',
    version: '1.0.0',
  }, { capabilities: { logging: {} } });

  // Simple tools mirroring ones from HTTP test server
  server.registerTool('greet', {
    title: 'Greeting Tool',
    description: 'A simple greeting tool',
    inputSchema: { name: z.string().describe('Name to greet') },
  }, async ({ name }): Promise<CallToolResult> => {
    return { content: [{ type: 'text', text: `Hello, ${name}!` }] };
  });

  server.tool('multi-greet', 'A tool that sends different greetings with delays between them',
    { name: z.string().describe('Name to greet') },
    { title: 'Multiple Greeting Tool', readOnlyHint: true, openWorldHint: false },
    async ({ name }, extra): Promise<CallToolResult> => {
      const sleep = (ms: number) => new Promise(r => setTimeout(r, ms));
      await server.sendLoggingMessage({ level: 'debug', data: `Starting multi-greet for ${name}` }, extra.sessionId);
      await sleep(200);
      await server.sendLoggingMessage({ level: 'info', data: `Sending first greeting to ${name}` }, extra.sessionId);
      await sleep(200);
      await server.sendLoggingMessage({ level: 'info', data: `Sending second greeting to ${name}` }, extra.sessionId);
      return { content: [{ type: 'text', text: `Good morning, ${name}!` }] };
    }
  );

  server.registerPrompt('greeting-template', {
    title: 'Greeting Template',
    description: 'A simple greeting prompt template',
    argsSchema: { name: z.string().describe('Name to include in greeting') },
  }, async ({ name }): Promise<GetPromptResult> => {
    return {
      messages: [{ role: 'user', content: { type: 'text', text: `Please greet ${name} in a friendly manner.` } }],
    };
  });

  server.registerResource('greeting-resource', 'https://example.com/greetings/default', {
    title: 'Default Greeting',
    description: 'A simple greeting resource',
    mimeType: 'text/plain',
  }, async (): Promise<ReadResourceResult> => {
    return { contents: [{ uri: 'https://example.com/greetings/default', text: 'Hello, world!' }] };
  });

  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((err) => {
  console.error('Failed to start stdio server:', err);
  process.exit(1);
});
