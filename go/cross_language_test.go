package ucotron

import (
	"context"
	"os"
	"testing"
)

// TestCrossLanguage* tests run against a live Ucotron server.
// Set UCOTRON_TEST_SERVER_URL to the server URL (e.g., "http://127.0.0.1:8420").
// If the env var is not set, tests are skipped.

func getTestServerURL(t *testing.T) string {
	t.Helper()
	url := os.Getenv("UCOTRON_TEST_SERVER_URL")
	if url == "" {
		t.Skip("UCOTRON_TEST_SERVER_URL not set — skipping integration test")
	}
	return url
}

func testClient(t *testing.T) *Client {
	t.Helper()
	url := getTestServerURL(t)
	return NewClient(url, nil)
}

func TestCrossLanguageHealth(t *testing.T) {
	client := testClient(t)
	ctx := context.Background()

	health, err := client.Health(ctx)
	if err != nil {
		t.Fatalf("Health() failed: %v", err)
	}

	if health.Status != "ok" {
		t.Errorf("expected status 'ok', got %q", health.Status)
	}
	if health.Version == "" {
		t.Error("expected non-empty version")
	}
	if health.InstanceID == "" {
		t.Error("expected non-empty instance_id")
	}
	if health.InstanceRole == "" {
		t.Error("expected non-empty instance_role")
	}
	if health.StorageMode == "" {
		t.Error("expected non-empty storage_mode")
	}
}

func TestCrossLanguageMetrics(t *testing.T) {
	client := testClient(t)
	ctx := context.Background()

	metrics, err := client.Metrics(ctx)
	if err != nil {
		t.Fatalf("Metrics() failed: %v", err)
	}

	if metrics.InstanceID == "" {
		t.Error("expected non-empty instance_id")
	}
	if metrics.UptimeSecs < 0 {
		t.Errorf("expected uptime_secs >= 0, got %d", metrics.UptimeSecs)
	}
}

func TestCrossLanguageAddMemory(t *testing.T) {
	client := testClient(t)
	ctx := context.Background()

	result, err := client.AddMemory(ctx, "Go SDK test: Mount Everest is the tallest mountain.", &AddMemoryOptions{
		Namespace: "go_test",
	})
	if err != nil {
		t.Fatalf("AddMemory() failed: %v", err)
	}

	if result.ChunkNodeIDs == nil {
		t.Error("expected chunk_node_ids to be present")
	}
	if result.EntityNodeIDs == nil {
		t.Error("expected entity_node_ids to be present")
	}
}

func TestCrossLanguageSearch(t *testing.T) {
	client := testClient(t)
	ctx := context.Background()

	limit := 5
	result, err := client.Search(ctx, "tallest mountain", &SearchOptions{
		Limit:     &limit,
		Namespace: "go_search",
	})
	if err != nil {
		t.Fatalf("Search() failed: %v", err)
	}

	if result.Results == nil {
		t.Error("expected results array")
	}
	if result.Query == "" {
		t.Error("expected non-empty query echo")
	}
}

func TestCrossLanguageAugment(t *testing.T) {
	client := testClient(t)
	ctx := context.Background()

	result, err := client.Augment(ctx, "What do you know about artificial intelligence?", &AugmentOptions{
		Namespace: "go_aug",
	})
	if err != nil {
		t.Fatalf("Augment() failed: %v", err)
	}

	if result.Memories == nil {
		t.Error("expected memories array")
	}
	if result.Entities == nil {
		t.Error("expected entities array")
	}
	// context_text is always a string (possibly empty)
}

func TestCrossLanguageLearn(t *testing.T) {
	client := testClient(t)
	ctx := context.Background()

	result, err := client.Learn(ctx, "The user mentioned they prefer dark mode and use VSCode.", &LearnOptions{
		Namespace: "go_learn",
	})
	if err != nil {
		t.Fatalf("Learn() failed: %v", err)
	}

	if result.MemoriesCreated < 0 {
		t.Errorf("expected memories_created >= 0, got %d", result.MemoriesCreated)
	}
}

func TestCrossLanguageListMemories(t *testing.T) {
	client := testClient(t)
	ctx := context.Background()

	result, err := client.ListMemories(ctx, nil)
	if err != nil {
		t.Fatalf("ListMemories() failed: %v", err)
	}

	// Should return a slice (possibly empty)
	if result == nil {
		t.Error("expected non-nil result")
	}
}

func TestCrossLanguageListEntities(t *testing.T) {
	client := testClient(t)
	ctx := context.Background()

	result, err := client.ListEntities(ctx, nil)
	if err != nil {
		t.Fatalf("ListEntities() failed: %v", err)
	}

	// Should return a slice (possibly empty)
	if result == nil {
		t.Error("expected non-nil result")
	}
}
