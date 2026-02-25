# @ucotron/sdk

TypeScript/Node.js client for [Ucotron](https://github.com/Ucotron/ucotron) — cognitive trust infrastructure for AI.

## Install

```bash
npm install @ucotron/sdk
```

Requires Node.js 18+ (uses native `fetch`).

## Usage

```ts
import { Ucotron } from "@ucotron/sdk";

const ucotron = new Ucotron("http://localhost:8420");

// Augment — get relevant context for your LLM prompt
const { context_text, memories } = await ucotron.augment("Tell me about the user");

// Learn — extract and store knowledge from agent output
await ucotron.learn("The user's name is Juan and he lives in Buenos Aires.");

// Search — semantic search across memories
const { results } = await ucotron.search("user preferences");

// CRUD
await ucotron.addMemory("Important fact to remember");
const memories = await ucotron.listMemories({ limit: 10 });
const entity = await ucotron.getEntity(42);
```

## Configuration

```ts
const ucotron = new Ucotron("http://localhost:8420", {
  timeoutMs: 10_000,
  defaultNamespace: "production",
  retry: {
    maxRetries: 5,
    baseDelayMs: 200,
    maxDelayMs: 10_000,
  },
});
```

## Namespaces

All methods accept an optional `namespace` parameter for multi-tenant isolation:

```ts
await ucotron.augment("context", { namespace: "tenant-a" });
```

## License

MIT
