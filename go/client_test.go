package ucotron

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

// --- Helper functions ---

func jsonHandler(statusCode int, body interface{}) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(statusCode)
		if body != nil {
			json.NewEncoder(w).Encode(body)
		}
	}
}

func intPtr(v int) *int       { return &v }
func strPtr(v string) *string { return &v }

// --- Config Tests ---

func TestNewClientDefaults(t *testing.T) {
	c := NewClient("http://localhost:8420", nil)
	defer c.Close()

	if c.baseURL != "http://localhost:8420" {
		t.Errorf("expected baseURL http://localhost:8420, got %s", c.baseURL)
	}
	if c.defaultNamespace != "default" {
		t.Errorf("expected default namespace 'default', got %s", c.defaultNamespace)
	}
	if c.retryConfig.MaxRetries != 3 {
		t.Errorf("expected maxRetries 3, got %d", c.retryConfig.MaxRetries)
	}
	if c.retryConfig.BaseDelayMs != 100 {
		t.Errorf("expected baseDelayMs 100, got %d", c.retryConfig.BaseDelayMs)
	}
	if c.retryConfig.MaxDelayMs != 5000 {
		t.Errorf("expected maxDelayMs 5000, got %d", c.retryConfig.MaxDelayMs)
	}
}

func TestNewClientCustomConfig(t *testing.T) {
	c := NewClient("http://example.com:9999/", &ClientConfig{
		TimeoutMs:        5000,
		DefaultNamespace: "test-ns",
		Retry: &RetryConfig{
			MaxRetries:  5,
			BaseDelayMs: 200,
			MaxDelayMs:  10000,
		},
	})
	defer c.Close()

	if c.baseURL != "http://example.com:9999" {
		t.Errorf("expected trailing slash stripped, got %s", c.baseURL)
	}
	if c.defaultNamespace != "test-ns" {
		t.Errorf("expected namespace 'test-ns', got %s", c.defaultNamespace)
	}
	if c.retryConfig.MaxRetries != 5 {
		t.Errorf("expected maxRetries 5, got %d", c.retryConfig.MaxRetries)
	}
}

func TestRetryDelay(t *testing.T) {
	c := NewClient("http://localhost:8420", &ClientConfig{
		Retry: &RetryConfig{
			BaseDelayMs: 100,
			MaxDelayMs:  1000,
		},
	})

	// attempt 0: 100ms
	if d := c.retryDelay(0); d != 100*time.Millisecond {
		t.Errorf("attempt 0: expected 100ms, got %v", d)
	}
	// attempt 1: 200ms
	if d := c.retryDelay(1); d != 200*time.Millisecond {
		t.Errorf("attempt 1: expected 200ms, got %v", d)
	}
	// attempt 2: 400ms
	if d := c.retryDelay(2); d != 400*time.Millisecond {
		t.Errorf("attempt 2: expected 400ms, got %v", d)
	}
	// attempt 4: capped at 1000ms
	if d := c.retryDelay(4); d != 1000*time.Millisecond {
		t.Errorf("attempt 4: expected 1000ms (capped), got %v", d)
	}
}

// --- Type Serialization Tests ---

func TestCreateMemoryRequestSerialization(t *testing.T) {
	req := CreateMemoryRequest{
		Text:     "Hello world",
		Metadata: map[string]interface{}{"key": "value"},
	}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatal(err)
	}

	var parsed map[string]interface{}
	json.Unmarshal(data, &parsed)

	if parsed["text"] != "Hello world" {
		t.Errorf("expected text 'Hello world', got %v", parsed["text"])
	}
	meta := parsed["metadata"].(map[string]interface{})
	if meta["key"] != "value" {
		t.Errorf("expected metadata.key 'value', got %v", meta["key"])
	}
}

func TestSearchRequestOmitsNil(t *testing.T) {
	req := SearchRequest{Query: "test"}
	data, err := json.Marshal(req)
	if err != nil {
		t.Fatal(err)
	}
	s := string(data)
	if strings.Contains(s, "limit") {
		t.Error("expected limit to be omitted when nil")
	}
	if strings.Contains(s, "node_type") {
		t.Error("expected node_type to be omitted when nil")
	}
	if strings.Contains(s, "time_range") {
		t.Error("expected time_range to be omitted when nil")
	}
}

func TestSearchResponseDeserialization(t *testing.T) {
	jsonStr := `{
		"results": [
			{"id": 1, "content": "test", "node_type": "entity", "score": 0.95, "vector_sim": 0.8, "graph_centrality": 0.1, "recency": 0.05}
		],
		"total": 1,
		"query": "test query"
	}`

	var resp SearchResponse
	if err := json.Unmarshal([]byte(jsonStr), &resp); err != nil {
		t.Fatal(err)
	}
	if resp.Total != 1 {
		t.Errorf("expected total 1, got %d", resp.Total)
	}
	if resp.Results[0].Score != 0.95 {
		t.Errorf("expected score 0.95, got %f", resp.Results[0].Score)
	}
	if resp.Results[0].Content != "test" {
		t.Errorf("expected content 'test', got %s", resp.Results[0].Content)
	}
}

func TestHealthResponseDeserialization(t *testing.T) {
	jsonStr := `{
		"status": "ok",
		"version": "0.1.0",
		"instance_id": "abc-123",
		"instance_role": "standalone",
		"storage_mode": "embedded",
		"vector_backend": "helix",
		"graph_backend": "helix",
		"models": {
			"embedder_loaded": true,
			"embedding_model": "all-MiniLM-L6-v2",
			"ner_loaded": false,
			"relation_extractor_loaded": false
		}
	}`

	var resp HealthResponse
	if err := json.Unmarshal([]byte(jsonStr), &resp); err != nil {
		t.Fatal(err)
	}
	if resp.Status != "ok" {
		t.Errorf("expected status 'ok', got %s", resp.Status)
	}
	if !resp.Models.EmbedderLoaded {
		t.Error("expected embedder_loaded true")
	}
	if resp.Models.EmbeddingModel != "all-MiniLM-L6-v2" {
		t.Errorf("expected model 'all-MiniLM-L6-v2', got %s", resp.Models.EmbeddingModel)
	}
}

func TestAugmentResponseDeserialization(t *testing.T) {
	jsonStr := `{
		"memories": [{"id": 1, "content": "fact", "node_type": "entity", "score": 0.9, "vector_sim": 0.7, "graph_centrality": 0.1, "recency": 0.1}],
		"entities": [{"id": 2, "content": "Juan", "node_type": "entity", "timestamp": 1000, "metadata": {}}],
		"context_text": "Relevant context here"
	}`

	var resp AugmentResponse
	if err := json.Unmarshal([]byte(jsonStr), &resp); err != nil {
		t.Fatal(err)
	}
	if len(resp.Memories) != 1 {
		t.Errorf("expected 1 memory, got %d", len(resp.Memories))
	}
	if resp.ContextText != "Relevant context here" {
		t.Errorf("unexpected context_text: %s", resp.ContextText)
	}
}

func TestLearnResponseDeserialization(t *testing.T) {
	jsonStr := `{"memories_created": 3, "entities_found": 2, "conflicts_found": 1}`
	var resp LearnResponse
	if err := json.Unmarshal([]byte(jsonStr), &resp); err != nil {
		t.Fatal(err)
	}
	if resp.MemoriesCreated != 3 || resp.EntitiesFound != 2 || resp.ConflictsFound != 1 {
		t.Errorf("unexpected values: %+v", resp)
	}
}

func TestMetricsResponseDeserialization(t *testing.T) {
	jsonStr := `{"instance_id": "test", "total_requests": 100, "total_ingestions": 50, "total_searches": 30, "uptime_secs": 3600}`
	var resp MetricsResponse
	if err := json.Unmarshal([]byte(jsonStr), &resp); err != nil {
		t.Fatal(err)
	}
	if resp.TotalRequests != 100 {
		t.Errorf("expected 100 requests, got %d", resp.TotalRequests)
	}
}

// --- Error Type Tests ---

func TestUcotronServerError(t *testing.T) {
	err := &UcotronServerError{StatusCode: 404, Code: "NOT_FOUND", Message: "memory not found"}
	s := err.Error()
	if !strings.Contains(s, "404") || !strings.Contains(s, "NOT_FOUND") || !strings.Contains(s, "memory not found") {
		t.Errorf("error message missing expected parts: %s", s)
	}
}

func TestUcotronConnectionError(t *testing.T) {
	cause := fmt.Errorf("dial tcp: connection refused")
	err := &UcotronConnectionError{Message: "request failed", Cause: cause}
	s := err.Error()
	if !strings.Contains(s, "request failed") || !strings.Contains(s, "connection refused") {
		t.Errorf("error message missing expected parts: %s", s)
	}
	if !errors.Is(err, cause) {
		t.Error("expected Unwrap to return cause")
	}
}

func TestUcotronRetriesExhaustedError(t *testing.T) {
	lastErr := &UcotronServerError{StatusCode: 500, Code: "INTERNAL", Message: "oops"}
	err := &UcotronRetriesExhaustedError{Attempts: 4, LastError: lastErr}
	s := err.Error()
	if !strings.Contains(s, "4 attempts") {
		t.Errorf("error message missing attempt count: %s", s)
	}
	var serverErr *UcotronServerError
	if !errors.As(err, &serverErr) {
		t.Error("expected Unwrap chain to contain UcotronServerError")
	}
}

// --- Client Method Tests (using httptest) ---

func TestHealth(t *testing.T) {
	srv := httptest.NewServer(jsonHandler(200, HealthResponse{
		Status:  "ok",
		Version: "0.1.0",
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	defer c.Close()

	resp, err := c.Health(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if resp.Status != "ok" {
		t.Errorf("expected status 'ok', got %s", resp.Status)
	}
}

func TestMetrics(t *testing.T) {
	srv := httptest.NewServer(jsonHandler(200, MetricsResponse{
		InstanceID:    "test-1",
		TotalRequests: 42,
		UptimeSecs:    100,
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	resp, err := c.Metrics(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if resp.TotalRequests != 42 {
		t.Errorf("expected 42 requests, got %d", resp.TotalRequests)
	}
}

func TestAugment(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.URL.Path != "/api/v1/augment" {
			t.Errorf("expected /api/v1/augment, got %s", r.URL.Path)
		}

		body, _ := io.ReadAll(r.Body)
		var req AugmentRequest
		json.Unmarshal(body, &req)
		if req.Context != "test context" {
			t.Errorf("expected 'test context', got %s", req.Context)
		}

		json.NewEncoder(w).Encode(AugmentResponse{
			ContextText: "augmented context",
			Memories:    []SearchResultItem{},
			Entities:    []EntityResponse{},
		})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	resp, err := c.Augment(context.Background(), "test context", nil)
	if err != nil {
		t.Fatal(err)
	}
	if resp.ContextText != "augmented context" {
		t.Errorf("expected 'augmented context', got %s", resp.ContextText)
	}
}

func TestLearn(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/learn" {
			t.Errorf("expected /api/v1/learn, got %s", r.URL.Path)
		}
		body, _ := io.ReadAll(r.Body)
		var req LearnRequest
		json.Unmarshal(body, &req)
		if req.Output != "agent output" {
			t.Errorf("expected 'agent output', got %s", req.Output)
		}

		json.NewEncoder(w).Encode(LearnResponse{
			MemoriesCreated: 2,
			EntitiesFound:   3,
			ConflictsFound:  0,
		})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	resp, err := c.Learn(context.Background(), "agent output", nil)
	if err != nil {
		t.Fatal(err)
	}
	if resp.MemoriesCreated != 2 {
		t.Errorf("expected 2 memories created, got %d", resp.MemoriesCreated)
	}
}

func TestSearch(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/memories/search" {
			t.Errorf("expected /api/v1/memories/search, got %s", r.URL.Path)
		}
		json.NewEncoder(w).Encode(SearchResponse{
			Results: []SearchResultItem{
				{ID: 1, Content: "found", Score: 0.95},
			},
			Total: 1,
			Query: "test",
		})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	resp, err := c.Search(context.Background(), "test", &SearchOptions{Limit: intPtr(5)})
	if err != nil {
		t.Fatal(err)
	}
	if resp.Total != 1 {
		t.Errorf("expected 1 result, got %d", resp.Total)
	}
	if resp.Results[0].Content != "found" {
		t.Errorf("expected content 'found', got %s", resp.Results[0].Content)
	}
}

func TestAddMemory(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/memories" || r.Method != http.MethodPost {
			t.Errorf("expected POST /api/v1/memories, got %s %s", r.Method, r.URL.Path)
		}
		w.WriteHeader(201)
		json.NewEncoder(w).Encode(CreateMemoryResponse{
			ChunkNodeIDs:  []int64{100},
			EntityNodeIDs: []int64{200},
			EdgesCreated:  1,
		})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	resp, err := c.AddMemory(context.Background(), "new memory", nil)
	if err != nil {
		t.Fatal(err)
	}
	if len(resp.ChunkNodeIDs) != 1 || resp.ChunkNodeIDs[0] != 100 {
		t.Errorf("expected chunk_node_ids [100], got %v", resp.ChunkNodeIDs)
	}
}

func TestGetMemory(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/memories/42" {
			t.Errorf("expected /api/v1/memories/42, got %s", r.URL.Path)
		}
		json.NewEncoder(w).Encode(MemoryResponse{
			ID:      42,
			Content: "stored memory",
		})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	resp, err := c.GetMemory(context.Background(), 42, nil)
	if err != nil {
		t.Fatal(err)
	}
	if resp.ID != 42 || resp.Content != "stored memory" {
		t.Errorf("unexpected response: %+v", resp)
	}
}

func TestListMemories(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasPrefix(r.URL.Path, "/api/v1/memories") {
			t.Errorf("expected /api/v1/memories, got %s", r.URL.Path)
		}
		if r.URL.Query().Get("limit") != "10" {
			t.Errorf("expected limit=10, got %s", r.URL.Query().Get("limit"))
		}
		json.NewEncoder(w).Encode([]MemoryResponse{
			{ID: 1, Content: "mem1"},
			{ID: 2, Content: "mem2"},
		})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	resp, err := c.ListMemories(context.Background(), &ListMemoriesOptions{Limit: intPtr(10)})
	if err != nil {
		t.Fatal(err)
	}
	if len(resp) != 2 {
		t.Errorf("expected 2 memories, got %d", len(resp))
	}
}

func TestUpdateMemory(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPut || r.URL.Path != "/api/v1/memories/5" {
			t.Errorf("expected PUT /api/v1/memories/5, got %s %s", r.Method, r.URL.Path)
		}
		json.NewEncoder(w).Encode(MemoryResponse{ID: 5, Content: "updated"})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	content := "updated"
	resp, err := c.UpdateMemory(context.Background(), 5, &UpdateMemoryRequest{Content: &content}, nil)
	if err != nil {
		t.Fatal(err)
	}
	if resp.Content != "updated" {
		t.Errorf("expected 'updated', got %s", resp.Content)
	}
}

func TestDeleteMemory(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodDelete || r.URL.Path != "/api/v1/memories/99" {
			t.Errorf("expected DELETE /api/v1/memories/99, got %s %s", r.Method, r.URL.Path)
		}
		w.WriteHeader(200)
		w.Write([]byte(`{}`))
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	err := c.DeleteMemory(context.Background(), 99, nil)
	if err != nil {
		t.Fatal(err)
	}
}

func TestGetEntity(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/entities/10" {
			t.Errorf("expected /api/v1/entities/10, got %s", r.URL.Path)
		}
		json.NewEncoder(w).Encode(EntityResponse{
			ID:      10,
			Content: "Juan",
			Neighbors: []NeighborResponse{
				{NodeID: 11, Content: "Madrid", EdgeType: "LIVES_IN", Weight: 0.9},
			},
		})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	resp, err := c.GetEntity(context.Background(), 10, nil)
	if err != nil {
		t.Fatal(err)
	}
	if resp.Content != "Juan" {
		t.Errorf("expected 'Juan', got %s", resp.Content)
	}
	if len(resp.Neighbors) != 1 || resp.Neighbors[0].Content != "Madrid" {
		t.Errorf("unexpected neighbors: %+v", resp.Neighbors)
	}
}

func TestListEntities(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]EntityResponse{
			{ID: 1, Content: "Entity1"},
		})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	resp, err := c.ListEntities(context.Background(), &ListEntitiesOptions{Limit: intPtr(5)})
	if err != nil {
		t.Fatal(err)
	}
	if len(resp) != 1 {
		t.Errorf("expected 1 entity, got %d", len(resp))
	}
}

// --- Namespace Tests ---

func TestNamespaceHeader(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ns := r.Header.Get("X-Ucotron-Namespace")
		if ns != "custom-ns" {
			t.Errorf("expected namespace 'custom-ns', got '%s'", ns)
		}
		json.NewEncoder(w).Encode(HealthResponse{Status: "ok"})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, &ClientConfig{DefaultNamespace: "custom-ns"})
	_, err := c.Health(context.Background())
	if err != nil {
		t.Fatal(err)
	}
}

func TestNamespaceOverride(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ns := r.Header.Get("X-Ucotron-Namespace")
		if ns != "override-ns" {
			t.Errorf("expected namespace 'override-ns', got '%s'", ns)
		}
		json.NewEncoder(w).Encode(SearchResponse{Results: []SearchResultItem{}, Total: 0, Query: "q"})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, &ClientConfig{DefaultNamespace: "default-ns"})
	_, err := c.Search(context.Background(), "q", &SearchOptions{Namespace: "override-ns"})
	if err != nil {
		t.Fatal(err)
	}
}

// --- Error Handling Tests ---

func TestServerError4xxNotRetried(t *testing.T) {
	var callCount int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&callCount, 1)
		w.WriteHeader(404)
		json.NewEncoder(w).Encode(APIErrorBody{Code: "NOT_FOUND", Message: "not found"})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, &ClientConfig{
		Retry: &RetryConfig{MaxRetries: 3, BaseDelayMs: 1, MaxDelayMs: 1},
	})
	_, err := c.GetMemory(context.Background(), 999, nil)
	if err == nil {
		t.Fatal("expected error")
	}

	var serverErr *UcotronServerError
	if !errors.As(err, &serverErr) {
		t.Fatalf("expected UcotronServerError, got %T: %v", err, err)
	}
	if serverErr.StatusCode != 404 {
		t.Errorf("expected 404, got %d", serverErr.StatusCode)
	}

	// 4xx should NOT be retried
	if atomic.LoadInt32(&callCount) != 1 {
		t.Errorf("expected exactly 1 call (no retries), got %d", atomic.LoadInt32(&callCount))
	}
}

func TestServerError5xxRetried(t *testing.T) {
	var callCount int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		count := atomic.AddInt32(&callCount, 1)
		if count <= 2 {
			w.WriteHeader(500)
			json.NewEncoder(w).Encode(APIErrorBody{Code: "INTERNAL", Message: "server error"})
			return
		}
		json.NewEncoder(w).Encode(HealthResponse{Status: "ok"})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, &ClientConfig{
		Retry: &RetryConfig{MaxRetries: 3, BaseDelayMs: 1, MaxDelayMs: 1},
	})
	resp, err := c.Health(context.Background())
	if err != nil {
		t.Fatalf("expected success after retries, got: %v", err)
	}
	if resp.Status != "ok" {
		t.Errorf("expected 'ok', got %s", resp.Status)
	}
	if atomic.LoadInt32(&callCount) != 3 {
		t.Errorf("expected 3 calls (2 retries + 1 success), got %d", atomic.LoadInt32(&callCount))
	}
}

func TestRetriesExhausted(t *testing.T) {
	var callCount int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&callCount, 1)
		w.WriteHeader(503)
		json.NewEncoder(w).Encode(APIErrorBody{Code: "UNAVAILABLE", Message: "service unavailable"})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, &ClientConfig{
		Retry: &RetryConfig{MaxRetries: 2, BaseDelayMs: 1, MaxDelayMs: 1},
	})
	_, err := c.Health(context.Background())
	if err == nil {
		t.Fatal("expected error")
	}

	var retriesErr *UcotronRetriesExhaustedError
	if !errors.As(err, &retriesErr) {
		t.Fatalf("expected UcotronRetriesExhaustedError, got %T: %v", err, err)
	}
	if retriesErr.Attempts != 3 {
		t.Errorf("expected 3 attempts, got %d", retriesErr.Attempts)
	}
	// MaxRetries=2 means 3 total attempts (1 initial + 2 retries)
	if atomic.LoadInt32(&callCount) != 3 {
		t.Errorf("expected 3 calls, got %d", atomic.LoadInt32(&callCount))
	}
}

func TestConnectionError(t *testing.T) {
	// Connect to a port where nothing is listening
	c := NewClient("http://127.0.0.1:1", &ClientConfig{
		TimeoutMs: 100,
		Retry:     &RetryConfig{MaxRetries: 0, BaseDelayMs: 1, MaxDelayMs: 1},
	})
	_, err := c.Health(context.Background())
	if err == nil {
		t.Fatal("expected connection error")
	}

	var retriesErr *UcotronRetriesExhaustedError
	if !errors.As(err, &retriesErr) {
		t.Fatalf("expected UcotronRetriesExhaustedError wrapping connection error, got %T: %v", err, err)
	}
	var connErr *UcotronConnectionError
	if !errors.As(retriesErr.LastError, &connErr) {
		t.Fatalf("expected inner UcotronConnectionError, got %T: %v", retriesErr.LastError, retriesErr.LastError)
	}
}

// --- Context Cancellation Test ---

func TestContextCancellation(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Block until the request context is done (client cancels)
		<-r.Context().Done()
	}))
	defer srv.Close()

	c := NewClient(srv.URL, &ClientConfig{
		TimeoutMs: 30000,
		Retry:     &RetryConfig{MaxRetries: 0, BaseDelayMs: 1, MaxDelayMs: 1},
	})

	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()

	_, err := c.Health(ctx)
	if err == nil {
		t.Fatal("expected error from cancelled context")
	}
}

// --- Content-Type Header Test ---

func TestContentTypeHeaders(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		accept := r.Header.Get("Accept")
		if accept != "application/json" {
			t.Errorf("expected Accept: application/json, got %s", accept)
		}

		if r.Method == http.MethodPost {
			ct := r.Header.Get("Content-Type")
			if ct != "application/json" {
				t.Errorf("expected Content-Type: application/json for POST, got %s", ct)
			}
		}

		json.NewEncoder(w).Encode(SearchResponse{Results: []SearchResultItem{}, Total: 0, Query: "q"})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	c.Search(context.Background(), "test", nil)
}

// --- Search with Options Test ---

func TestSearchWithAllOptions(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		var req SearchRequest
		json.Unmarshal(body, &req)

		if req.Query != "search query" {
			t.Errorf("expected 'search query', got %s", req.Query)
		}
		if req.Limit == nil || *req.Limit != 20 {
			t.Error("expected limit 20")
		}
		if req.NodeType == nil || *req.NodeType != "entity" {
			t.Error("expected node_type 'entity'")
		}
		if req.TimeRange == nil || req.TimeRange[0] != 1000 || req.TimeRange[1] != 2000 {
			t.Error("expected time_range [1000, 2000]")
		}

		json.NewEncoder(w).Encode(SearchResponse{Results: []SearchResultItem{}, Total: 0, Query: "search query"})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	tr := [2]int64{1000, 2000}
	_, err := c.Search(context.Background(), "search query", &SearchOptions{
		Limit:     intPtr(20),
		NodeType:  strPtr("entity"),
		TimeRange: &tr,
	})
	if err != nil {
		t.Fatal(err)
	}
}

// --- Learn with Metadata Test ---

func TestLearnWithMetadata(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		var req LearnRequest
		json.Unmarshal(body, &req)

		if req.Metadata == nil {
			t.Error("expected metadata")
		}
		if req.Metadata["source"] != "agent" {
			t.Errorf("expected source=agent, got %v", req.Metadata["source"])
		}

		json.NewEncoder(w).Encode(LearnResponse{MemoriesCreated: 1})
	}))
	defer srv.Close()

	c := NewClient(srv.URL, nil)
	_, err := c.Learn(context.Background(), "output", &LearnOptions{
		Metadata: map[string]interface{}{"source": "agent"},
	})
	if err != nil {
		t.Fatal(err)
	}
}
