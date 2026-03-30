# Module kotlin-sdk-client-enterprise-auth

Enterprise Managed Authorization (SEP-990) support for Kotlin MCP clients. Implements the
two-step OAuth 2.0 flow — RFC 8693 token exchange for a JWT Authorization Grant followed by
RFC 7523 JWT Bearer grant exchange — as a standalone Ktor [HttpClientPlugin].

**Platform Support:** Multiplatform • Kotlin 2.2+

## Key Classes

- [EnterpriseAuthProvider] — Ktor `HttpClientPlugin`; intercepts outgoing requests, performs
  full enterprise auth flow, caches the resulting access token, and injects
  `Authorization: Bearer` headers automatically
  - [EnterpriseAuthProviderOptions] — configuration: `clientId`, `assertionCallback`, etc.
  - [EnterpriseAuthAssertionContext] — context passed to the assertion callback
- [EnterpriseAuth] — low-level utility object with individual `suspend` functions for each
  auth step; use when you need fine-grained control
  - [AuthServerMetadata] — RFC 8414 discovery response model
  - [RequestJwtAuthGrantOptions] / [DiscoverAndRequestJwtAuthGrantOptions] — Step 1 options
  - [ExchangeJwtBearerGrantOptions] — Step 2 options
  - [JagTokenExchangeResponse] / [JwtBearerAccessTokenResponse] — response models
- [EnterpriseAuthException] — thrown on any auth flow failure

## Example

```kotlin
val httpClient = HttpClient(CIO) {
    install(SSE)
    install(EnterpriseAuthProvider) {
        clientId = "my-mcp-client"
        assertionCallback = { ctx ->
            EnterpriseAuth.requestJwtAuthorizationGrant(
                RequestJwtAuthGrantOptions(
                    tokenEndpoint = "https://idp.example.com/token",
                    idToken = myIdTokenSupplier(),
                    clientId = "my-idp-client",
                    clientSecret = "idp-client-secret",
                    audience = ctx.authorizationServerUrl,
                    resource = ctx.resourceUrl,
                ),
                authHttpClient,
            )
        }
    }
}

val transport = StreamableHttpClientTransport(client = httpClient, url = serverUrl)
```

# Package io.modelcontextprotocol.kotlin.sdk.client.auth

Enterprise Managed Authorization types and utilities (SEP-990).
