# ucotron-sdk

Python client for [Ucotron](https://github.com/Ucotron/ucotron) — cognitive trust infrastructure for AI.

## Install

```bash
pip install ucotron-sdk
```

Requires Python 3.9+.

## Usage

```python
from ucotron_sdk import Ucotron

ucotron = Ucotron("http://localhost:8420")

# Augment — get relevant context for your LLM prompt
result = ucotron.augment("Tell me about the user")
print(result.context_text)

# Learn — extract and store knowledge
ucotron.learn("The user's name is Juan and he lives in Buenos Aires.")

# Search — semantic search
results = ucotron.search("user preferences")
for item in results.results:
    print(f"{item.content} (score: {item.score:.2f})")

# CRUD
ucotron.add_memory("Important fact to remember")
memories = ucotron.list_memories(limit=10)
entity = ucotron.get_entity(42)
```

### Async

```python
from ucotron_sdk import AsyncUcotron

async with AsyncUcotron("http://localhost:8420") as ucotron:
    result = await ucotron.augment("context")
```

## Configuration

```python
ucotron = Ucotron(
    "http://localhost:8420",
    timeout=10.0,
    default_namespace="production",
    max_retries=5,
)
```

## License

MIT
