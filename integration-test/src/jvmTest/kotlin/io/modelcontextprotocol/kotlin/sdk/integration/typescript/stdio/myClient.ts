// @ts-nocheck
const args = process.argv.slice(2);
const toolName = args[0];
const toolArgs = args.slice(1);
const PROTOCOL_VERSION = "2024-11-05";

async function main() {
    const sdkDirRaw = process.env.TYPESCRIPT_SDK_DIR;
    const sdkDir = sdkDirRaw ? sdkDirRaw.trim() : undefined;

    let Client: any;

    if (!sdkDir) {
        throw new Error('TYPESCRIPT_SDK_DIR environment variable is not set. It should point to the cloned typescript-sdk directory.');
    }

    const path = await import('path');
    const { pathToFileURL } = await import('url');
    const clientUrl = pathToFileURL(path.join(sdkDir, 'packages', 'client', 'src', 'index.ts')).href;
    ({ Client } = await import(clientUrl));

    if (!toolName) {
        console.error('Available utils will be listed after connection');
    } else {
        console.error(`Will call tool: ${toolName} with args: ${toolArgs.join(', ')}`);
    }

    class MinimalStdioClientTransport {
        onmessage: ((msg: any) => void) | undefined;
        private buffer: string = '';
        private closed = false;

        async start(): Promise<void> {
            process.stdin.setEncoding('utf8');
            process.stdin.resume();
            process.stdin.on('data', (chunk: string) => {
                if (this.closed) return;
                this.buffer += chunk;
                this.processBuffer();
            });
        }

        private processBuffer() {
            while (true) {
                const idx = this.buffer.indexOf('\n');
                if (idx === -1) break;
                const line = this.buffer.slice(0, idx);
                this.buffer = this.buffer.slice(idx + 1);
                const trimmed = line.trim();
                if (!trimmed) continue;
                try {
                    const msg = JSON.parse(trimmed);
                    this.onmessage && this.onmessage(msg);
                } catch (e) {
                    console.error('Parse error in client stdio (line):', trimmed, e);
                }
            }
        }

        async send(msg: any): Promise<void> {
            if (this.closed) return;
            const json = JSON.stringify(msg);
            const payload = json + '\n';
            await new Promise<void>((resolve, reject) => {
                process.stdout.write(payload, 'utf8', (err?: Error | null) => err ? reject(err) : resolve());
            });
        }

        async close(): Promise<void> {
            this.closed = true;
            try { process.stdin.pause(); } catch {}
        }
    }

    const client = new Client({
        name: 'test-client',
        version: '1.0.0'
    });

    const transport = new MinimalStdioClientTransport();

    try {
        await client.connect(transport, { protocolVersion: PROTOCOL_VERSION });
        console.error('Connected to server over stdio');

        try {
            if (typeof (client as any).on === 'function') {
                (client as any).on('notification', (n: any) => {
                    try {
                        const method = (n && (n.method || (n.params && n.params.method))) || 'unknown';
                        console.error('Notification:', method, JSON.stringify(n));
                    } catch {
                        console.error('Notification: <unparsable>');
                    }
                });
            }
        } catch {
            // ignore
        }

        const toolsResult = await client.listTools();
        const tools = toolsResult.tools;
        console.error('Available utils:', tools.map((t: { name: any; }) => t.name).join(', '));

        if (!toolName) {
            await client.close();
            return;
        }

        const tool = tools.find((t: { name: string; }) => t.name === toolName);
        if (!tool) {
            console.error(`Tool "${toolName}" not found`);
            process.exit(1);
        }

        const toolArguments: any = {};

        if (toolName === 'greet' && toolArgs.length > 0) {
            toolArguments['name'] = toolArgs[0];
        } else if (tool.input && tool.input.properties) {
            const propNames = Object.keys(tool.input.properties);
            if (propNames.length > 0 && toolArgs.length > 0) {
                toolArguments[propNames[0]] = toolArgs[0];
            }
        }

        console.error(`Calling tool ${toolName} with arguments:`, toolArguments);

        const result = await client.callTool({
            name: toolName,
            arguments: toolArguments
        });
        console.error('Tool result:', JSON.stringify(result));

        if (result.content) {
            for (const content of result.content) {
                if (content.type === 'text') {
                    console.error('Text content:', content.text);
                }
            }
        }

        if (result.structuredContent) {
            console.error('Structured content:', JSON.stringify(result.structuredContent, null, 2));
        }

    } catch (error) {
        console.error('Error:', error);
        process.exit(1);
    } finally {
        await client.close();
        console.error('Disconnected from server');
    }
}

main().catch(error => {
    console.error('Unhandled error:', error);
    process.exit(1);
});
