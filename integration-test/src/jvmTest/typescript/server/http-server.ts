import express, {Request, Response} from 'express';
import {randomUUID} from 'node:crypto';
import cors from 'cors';
import {McpServer} from '@modelcontextprotocol/sdk/server/mcp';
import {StreamableHTTPServerTransport} from '@modelcontextprotocol/sdk/server/streamableHttp';
import {registerTestUtils} from './server-common';
import {
    getOAuthProtectedResourceMetadataUrl,
    mcpAuthMetadataRouter
} from '@modelcontextprotocol/sdk/server/auth/router';
import {requireBearerAuth} from '@modelcontextprotocol/sdk/server/auth/middleware/bearerAuth';
import {isInitializeRequest} from '@modelcontextprotocol/sdk/types';
import {InMemoryEventStore} from '@modelcontextprotocol/sdk/examples/shared/inMemoryEventStore';
import {setupAuthServer} from '@modelcontextprotocol/sdk/examples/server/demoInMemoryOAuthProvider';
import {checkResourceAllowed} from '@modelcontextprotocol/sdk/shared/auth-utils';
import {OAuthMetadata} from "@modelcontextprotocol/sdk/shared/auth";

async function main() {
    const useOAuth = process.argv.includes('--oauth');
    const strictOAuth = process.argv.includes('--oauth-strict');

    const getServer = () => {
        const server = new McpServer({
            name: 'simple-streamable-http-server',
            version: '1.0.0'
        }, {capabilities: {logging: {}}});

        registerTestUtils(server);

        return server;
    };

    const MCP_PORT = process.env.MCP_PORT ? parseInt(process.env.MCP_PORT, 10) : 3000;
    const AUTH_PORT = process.env.MCP_AUTH_PORT ? parseInt(process.env.MCP_AUTH_PORT, 10) : 3001;

    const app = express();
    app.use(express.json());

    // Allow CORS all domains, expose the Mcp-Session-Id header
    app.use(cors({
        origin: '*',
        exposedHeaders: ["Mcp-Session-Id"]
    }));

    // Set up OAuth if enabled
    let authMiddleware = null;
    if (useOAuth) {
        const mcpServerUrl = new URL(`http://localhost:${MCP_PORT}/mcp`);
        const authServerUrl = new URL(`http://localhost:${AUTH_PORT}`);

        const oauthMetadata: OAuthMetadata = setupAuthServer({
            authServerUrl,
            mcpServerUrl,
            strictResource: strictOAuth
        });

        const tokenVerifier = {
            verifyAccessToken: async (token: string) => {
                const endpoint = oauthMetadata.introspection_endpoint;

                if (!endpoint) {
                    throw new Error('No token verification endpoint available in metadata');
                }

                const response = await fetch(endpoint, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                    },
                    body: new URLSearchParams({
                        token: token
                    }).toString()
                });

                if (!response.ok) {
                    throw new Error(`Invalid or expired token: ${await response.text()}`);
                }

                const data = await response.json();

                if (strictOAuth) {
                    if (!data.aud) {
                        throw new Error(`Resource Indicator (RFC8707) missing`);
                    }
                    if (!checkResourceAllowed({requestedResource: data.aud, configuredResource: mcpServerUrl})) {
                        throw new Error(`Expected resource indicator ${mcpServerUrl}, got: ${data.aud}`);
                    }
                }

                return {
                    token,
                    clientId: data.client_id,
                    scopes: data.scope ? data.scope.split(' ') : [],
                    expiresAt: data.exp,
                };
            }
        }

        app.use(mcpAuthMetadataRouter({
            oauthMetadata,
            resourceServerUrl: mcpServerUrl,
            scopesSupported: ['mcp:tools'],
            resourceName: 'MCP Demo Server',
        }));

        authMiddleware = requireBearerAuth({
            verifier: tokenVerifier,
            requiredScopes: [],
            resourceMetadataUrl: getOAuthProtectedResourceMetadataUrl(mcpServerUrl),
        });
    }

    // Map to store transports by session ID
    const transports: { [sessionId: string]: StreamableHTTPServerTransport } = {};

    // MCP POST endpoint with optional auth
    const mcpPostHandler = async (req: Request, res: Response) => {
        const sessionId = req.headers['mcp-session-id'] as string | undefined;
        if (sessionId) {
            console.log(`Received MCP request for session: ${sessionId}`);
        } else {
            console.log('Request body:', req.body);
        }

        if (useOAuth && req.auth) {
            console.log('Authenticated user:', req.auth);
        }
        try {
            let transport: StreamableHTTPServerTransport;
            if (sessionId && transports[sessionId]) {
                transport = transports[sessionId];
            } else if (!sessionId && isInitializeRequest(req.body)) {
                const eventStore = new InMemoryEventStore();
                transport = new StreamableHTTPServerTransport({
                    sessionIdGenerator: () => randomUUID(),
                    eventStore,
                    onsessioninitialized: (sessionId) => {
                        console.log(`Session initialized with ID: ${sessionId}`);
                        transports[sessionId] = transport;
                    }
                });

                transport.onclose = () => {
                    const sid = transport.sessionId;
                    if (sid && transports[sid]) {
                        console.log(`Transport closed for session ${sid}, removing from transports map`);
                        delete transports[sid];
                    }
                };

                const server = getServer();
                await server.connect(transport);

                await transport.handleRequest(req, res, req.body);
                return;
            } else {
                res.status(400).json({
                    jsonrpc: '2.0',
                    error: {
                        code: -32000,
                        message: 'Bad Request: No valid session ID provided',
                    },
                    id: null,
                });
                return;
            }

            await transport.handleRequest(req, res, req.body);
        } catch (error) {
            console.error('Error handling MCP request:', error);
            if (!res.headersSent) {
                res.status(500).json({
                    jsonrpc: '2.0',
                    error: {
                        code: -32603,
                        message: 'Internal server error',
                    },
                    id: null,
                });
            }
        }
    };

    if (useOAuth && authMiddleware) {
        app.post('/mcp', authMiddleware, mcpPostHandler);
    } else {
        app.post('/mcp', mcpPostHandler);
    }

    // Handle GET requests for event streams
    const mcpGetHandler = async (req: Request, res: Response) => {
        const sessionId = req.headers['mcp-session-id'] as string | undefined;
        if (!sessionId || !transports[sessionId]) {
            res.status(400).send('Invalid or missing session ID');
            return;
        }

        if (useOAuth && req.auth) {
            console.log('Authenticated event stream connection from user:', req.auth);
        }

        const lastEventId = req.headers['last-event-id'] as string | undefined;
        if (lastEventId) {
            console.log(`Client reconnecting with Last-Event-ID: ${lastEventId}`);
        } else {
            console.log(`Establishing new event stream for session ${sessionId}`);
        }

        const transport = transports[sessionId];
        await transport.handleRequest(req, res);
    };

    if (useOAuth && authMiddleware) {
        app.get('/mcp', authMiddleware, mcpGetHandler);
    } else {
        app.get('/mcp', mcpGetHandler);
    }

    // Handle DELETE requests for session termination (according to MCP spec)
    const mcpDeleteHandler = async (req: Request, res: Response) => {
        const sessionId = req.headers['mcp-session-id'] as string | undefined;
        if (!sessionId || !transports[sessionId]) {
            res.status(400).send('Invalid or missing session ID');
            return;
        }

        console.log(`Received session termination request for session ${sessionId}`);

        try {
            const transport = transports[sessionId];
            await transport.handleRequest(req, res);
        } catch (error) {
            console.error('Error handling session termination:', error);
            if (!res.headersSent) {
                res.status(500).send('Error processing session termination');
            }
        }
    };

    if (useOAuth && authMiddleware) {
        app.delete('/mcp', authMiddleware, mcpDeleteHandler);
    } else {
        app.delete('/mcp', mcpDeleteHandler);
    }

    app.listen(MCP_PORT, (error) => {
        if (error) {
            console.error('Failed to start server:', error);
            process.exit(1);
        }
        console.log(`MCP Streamable HTTP Server listening on port ${MCP_PORT}`);
    });

    // Handle server shutdown
    process.on('SIGINT', async () => {
        console.log('Shutting down server...');

        for (const sessionId in transports) {
            try {
                console.log(`Closing transport for session ${sessionId}`);
                await transports[sessionId].close();
                delete transports[sessionId];
            } catch (error) {
                console.error(`Error closing transport for session ${sessionId}:`, error);
            }
        }
        console.log('Server shutdown complete');
        process.exit(0);
    });
}

main().catch((err) => {
    console.error('Failed to start server:', err);
    process.exit(1);
});
