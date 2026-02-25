# ucotron-go

Go client for [Ucotron](https://github.com/Ucotron/ucotron) — cognitive trust infrastructure for AI.

## Install

```bash
go get github.com/ucotron/ucotron-go
```

## Usage

```go
package main

import (
    "context"
    "fmt"
    ucotron "github.com/ucotron/ucotron-go"
)

func main() {
    client := ucotron.NewClient("http://localhost:8420", nil)

    // Augment
    result, _ := client.Augment(context.Background(), "Tell me about the user", nil)
    fmt.Println(result.ContextText)

    // Learn
    client.Learn(context.Background(), "User prefers dark mode.", nil)

    // Search
    searchResult, _ := client.Search(context.Background(), "preferences", nil)
    for _, item := range searchResult.Results {
        fmt.Printf("%s (score: %.2f)\n", item.Content, item.Score)
    }
}
```

## Configuration

```go
client := ucotron.NewClient("http://localhost:8420", &ucotron.ClientConfig{
    Timeout:          10 * time.Second,
    DefaultNamespace: "production",
    Retry: &ucotron.RetryConfig{
        MaxRetries:  5,
        BaseDelayMs: 200,
        MaxDelayMs:  10000,
    },
})
```

## License

MIT
