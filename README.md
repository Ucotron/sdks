# Ucotron SDKs

Official client libraries for [Ucotron](https://github.com/Ucotron/ucotron) — open-source cognitive trust infrastructure for AI.

| Language | Package | Install |
|----------|---------|---------|
| TypeScript | `@ucotron/sdk` | `npm install @ucotron/sdk` |
| Python | `ucotron-sdk` | `pip install ucotron-sdk` |
| Go | `github.com/ucotron/ucotron-go` | `go get github.com/ucotron/ucotron-go` |
| Java | `com.ucotron:ucotron-sdk` | Gradle / Maven |
| PHP | `ucotron/sdk` | `composer require ucotron/sdk` |

## Quick Example

```ts
import { Ucotron } from "@ucotron/sdk";

const ucotron = new Ucotron("http://localhost:8420");

// Store knowledge
await ucotron.learn("The user prefers dark mode and speaks Spanish.");

// Retrieve context for your LLM prompt
const { context_text } = await ucotron.augment("User settings");
```

## Documentation

Full docs at [docs.ucotron.com](https://docs.ucotron.com).

## License

MIT
