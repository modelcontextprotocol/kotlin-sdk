import {McpServer} from '@modelcontextprotocol/sdk/server/mcp';
import {z} from 'zod';
import {
    type CallToolResult,
    type GetPromptResult,
    type PrimitiveSchemaDefinition,
    type ReadResourceResult,
    type ResourceLink,
} from '@modelcontextprotocol/sdk/types';

export function registerTestUtils(server: McpServer) {
    // Register a simple tool that returns a greeting
    server.registerTool(
        'greet',
        {
            title: 'Greeting Tool',
            description: 'A simple greeting tool',
            inputSchema: {
                name: z.string().describe('Name to greet'),
            },
        },
        async ({name}): Promise<CallToolResult> => {
            return {
                content: [
                    {
                        type: 'text',
                        text: `Hello, ${name}!`,
                    },
                ],
            };
        }
    );

    // Register a tool that sends multiple greetings with notifications
    server.tool(
        'multi-greet',
        'A tool that sends different greetings with delays between them',
        {
            name: z.string().describe('Name to greet'),
        },
        {
            title: 'Multiple Greeting Tool',
            readOnlyHint: true,
            openWorldHint: false
        },
        async ({name}, extra): Promise<CallToolResult> => {
            const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

            await server.sendLoggingMessage({
                level: "debug",
                data: `Starting multi-greet for ${name}`
            }, extra.sessionId);

            await sleep(200);

            await server.sendLoggingMessage({
                level: "info",
                data: `Sending first greeting to ${name}`
            }, extra.sessionId);

            await sleep(200);

            await server.sendLoggingMessage({
                level: "info",
                data: `Sending second greeting to ${name}`
            }, extra.sessionId);

            return {
                content: [
                    {
                        type: 'text',
                        text: `Good morning, ${name}!`,
                    }
                ],
            };
        }
    );

    // Register a tool that demonstrates elicitation (user input collection)
    server.tool(
        'collect-user-info',
        'A tool that collects user information through elicitation',
        {
            infoType: z.enum(['contact', 'preferences', 'feedback']).describe('Type of information to collect'),
        },
        async ({infoType}): Promise<CallToolResult> => {
            let message: string;
            let requestedSchema: {
                type: 'object';
                properties: Record<string, PrimitiveSchemaDefinition>;
                required?: string[];
            };

            switch (infoType) {
                case 'contact':
                    message = 'Please provide your contact information';
                    requestedSchema = {
                        type: 'object',
                        properties: {
                            name: {
                                type: 'string',
                                title: 'Full Name',
                                description: 'Your full name',
                            },
                            email: {
                                type: 'string',
                                title: 'Email Address',
                                description: 'Your email address',
                                format: 'email',
                            },
                            phone: {
                                type: 'string',
                                title: 'Phone Number',
                                description: 'Your phone number (optional)',
                            },
                        },
                        required: ['name', 'email'],
                    };
                    break;
                case 'preferences':
                    message = 'Please set your preferences';
                    requestedSchema = {
                        type: 'object',
                        properties: {
                            theme: {
                                type: 'string',
                                title: 'Theme',
                                description: 'Choose your preferred theme',
                                enum: ['light', 'dark', 'auto'],
                                enumNames: ['Light', 'Dark', 'Auto'],
                            },
                            notifications: {
                                type: 'boolean',
                                title: 'Enable Notifications',
                                description: 'Would you like to receive notifications?',
                                default: true,
                            },
                            frequency: {
                                type: 'string',
                                title: 'Notification Frequency',
                                description: 'How often would you like notifications?',
                                enum: ['daily', 'weekly', 'monthly'],
                                enumNames: ['Daily', 'Weekly', 'Monthly'],
                            },
                        },
                        required: ['theme'],
                    };
                    break;
                case 'feedback':
                    message = 'Please provide your feedback';
                    requestedSchema = {
                        type: 'object',
                        properties: {
                            rating: {
                                type: 'integer',
                                title: 'Rating',
                                description: 'Rate your experience (1-5)',
                                minimum: 1,
                                maximum: 5,
                            },
                            comments: {
                                type: 'string',
                                title: 'Comments',
                                description: 'Additional comments (optional)',
                                maxLength: 500,
                            },
                            recommend: {
                                type: 'boolean',
                                title: 'Would you recommend this?',
                                description: 'Would you recommend this to others?',
                            },
                        },
                        required: ['rating', 'recommend'],
                    };
                    break;
                default:
                    throw new Error(`Unknown info type: ${infoType}`);
            }

            try {
                // Use the underlying server instance to elicit input from the client
                const result = await server.server.elicitInput({
                    message,
                    requestedSchema,
                });

                if (result.action === 'accept') {
                    return {
                        content: [
                            {
                                type: 'text',
                                text: `Thank you! Collected ${infoType} information: ${JSON.stringify(result.content, null, 2)}`,
                            },
                        ],
                    };
                } else if (result.action === 'decline') {
                    return {
                        content: [
                            {
                                type: 'text',
                                text: `No information was collected. User declined ${infoType} information request.`,
                            },
                        ],
                    };
                } else {
                    return {
                        content: [
                            {
                                type: 'text',
                                text: `Information collection was cancelled by the user.`,
                            },
                        ],
                    };
                }
            } catch (error) {
                return {
                    content: [
                        {
                            type: 'text',
                            text: `Error collecting ${infoType} information: ${error}`,
                        },
                    ],
                };
            }
        }
    );

    // Register a simple prompt
    server.registerPrompt(
        'greeting-template',
        {
            title: 'Greeting Template',
            description: 'A simple greeting prompt template',
            argsSchema: {
                name: z.string().describe('Name to include in greeting'),
            },
        },
        async ({name}): Promise<GetPromptResult> => {
            return {
                messages: [
                    {
                        role: 'user',
                        content: {
                            type: 'text',
                            text: `Please greet ${name} in a friendly manner.`,
                        },
                    },
                ],
            };
        }
    );

    // Register a tool specifically for testing resumability
    server.tool(
        'start-notification-stream',
        'Starts sending periodic notifications for testing resumability',
        {
            interval: z.number().describe('Interval in milliseconds between notifications').default(100),
            count: z.number().describe('Number of notifications to send (0 for 100)').default(50),
        },
        async ({interval, count}, extra): Promise<CallToolResult> => {
            const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
            let counter = 0;

            while (count === 0 || counter < count) {
                counter++;
                try {
                    await server.sendLoggingMessage({
                        level: "info",
                        data: `Periodic notification #${counter} at ${new Date().toISOString()}`
                    }, extra.sessionId);
                } catch (error) {
                    console.error("Error sending notification:", error);
                }
                // Wait for the specified interval
                await sleep(interval);
            }

            return {
                content: [
                    {
                        type: 'text',
                        text: `Started sending periodic notifications every ${interval}ms`,
                    }
                ],
            };
        }
    );

    // Create a simple resource at a fixed URI
    server.registerResource(
        'greeting-resource',
        'https://example.com/greetings/default',
        {
            title: 'Default Greeting',
            description: 'A simple greeting resource',
            mimeType: 'text/plain'
        },
        async (): Promise<ReadResourceResult> => {
            return {
                contents: [
                    {
                        uri: 'https://example.com/greetings/default',
                        text: 'Hello, world!',
                    },
                ],
            };
        }
    );

    // Create additional resources
    server.registerResource(
        'example-file-1',
        'file:///example/file1.txt',
        {
            title: 'Example File 1',
            description: 'First example file for ResourceLink demonstration',
            mimeType: 'text/plain'
        },
        async (): Promise<ReadResourceResult> => {
            return {
                contents: [
                    {
                        uri: 'file:///example/file1.txt',
                        text: 'This is the content of file 1',
                    },
                ],
            };
        }
    );

    server.registerResource(
        'example-file-2',
        'file:///example/file2.txt',
        {
            title: 'Example File 2',
            description: 'Second example file for ResourceLink demonstration',
            mimeType: 'text/plain'
        },
        async (): Promise<ReadResourceResult> => {
            return {
                contents: [
                    {
                        uri: 'file:///example/file2.txt',
                        text: 'This is the content of file 2',
                    },
                ],
            };
        }
    );

    // Register a tool that returns ResourceLinks
    server.registerTool(
        'list-files',
        {
            title: 'List Files with ResourceLinks',
            description: 'Returns a list of files as ResourceLinks without embedding their content',
            inputSchema: {
                includeDescriptions: z.boolean().optional().describe('Whether to include descriptions in the resource links'),
            },
        },
        async ({includeDescriptions = true}): Promise<CallToolResult> => {
            const resourceLinks: ResourceLink[] = [
                {
                    type: 'resource_link',
                    uri: 'https://example.com/greetings/default',
                    name: 'Default Greeting',
                    mimeType: 'text/plain',
                    ...(includeDescriptions && {description: 'A simple greeting resource'})
                },
                {
                    type: 'resource_link',
                    uri: 'file:///example/file1.txt',
                    name: 'Example File 1',
                    mimeType: 'text/plain',
                    ...(includeDescriptions && {description: 'First example file for ResourceLink demonstration'})
                },
                {
                    type: 'resource_link',
                    uri: 'file:///example/file2.txt',
                    name: 'Example File 2',
                    mimeType: 'text/plain',
                    ...(includeDescriptions && {description: 'Second example file for ResourceLink demonstration'})
                }
            ];

            return {
                content: [
                    {
                        type: 'text',
                        text: 'Here are the available files as resource links:',
                    },
                    ...resourceLinks,
                    {
                        type: 'text',
                        text: '\nYou can read any of these resources using their URI.',
                    }
                ],
            };
        }
    );
}
