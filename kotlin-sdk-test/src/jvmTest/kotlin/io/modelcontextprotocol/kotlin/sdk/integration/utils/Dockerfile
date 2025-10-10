# Docker image for running TypeScript SDK test servers/clients with SSE over HTTP and STDIO

FROM --platform=linux/amd64 node:20-alpine AS base

# 1) System deps
RUN apk add --no-cache git

# 2) Fetch the official MCP TypeScript SDK and install its dependencies
WORKDIR /opt/typescript-sdk
RUN git clone --depth 1 https://github.com/modelcontextprotocol/typescript-sdk.git . \
    && npm ci || npm install

# 3) Set working directory
WORKDIR /app

# 4) Runtime configuration
ENV NODE_PATH=/opt/typescript-sdk/node_modules
ENV TYPESCRIPT_SDK_DIR=/opt/typescript-sdk
