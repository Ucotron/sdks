package ucotron

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"strings"
	"time"
)

const (
	namespaceHeader    = "X-Ucotron-Namespace"
	defaultTimeoutMs   = 30000
	defaultMaxRetries  = 3
	defaultBaseDelayMs = 100
	defaultMaxDelayMs  = 5000
)

// RetryConfig controls retry behavior for transient errors.
type RetryConfig struct {
	// MaxRetries is the maximum number of retry attempts (default: 3).
	MaxRetries int
	// BaseDelayMs is the base delay in milliseconds for exponential backoff (default: 100).
	BaseDelayMs int
	// MaxDelayMs is the maximum delay in milliseconds (default: 5000).
	MaxDelayMs int
}

// ClientConfig configures the Ucotron client.
type ClientConfig struct {
	// TimeoutMs is the request timeout in milliseconds (default: 30000).
	TimeoutMs int
	// Retry configures retry behavior. If nil, defaults are used.
	Retry *RetryConfig
	// DefaultNamespace is the default namespace for all requests.
	DefaultNamespace string
}

// Client is a Ucotron server HTTP client.
type Client struct {
	baseURL          string
	httpClient       *http.Client
	retryConfig      RetryConfig
	defaultNamespace string
}

// NewClient creates a new Ucotron client connected to the given server URL.
// If config is nil, default configuration is used.
func NewClient(serverURL string, config *ClientConfig) *Client {
	serverURL = strings.TrimRight(serverURL, "/")

	timeoutMs := defaultTimeoutMs
	retry := RetryConfig{
		MaxRetries:  defaultMaxRetries,
		BaseDelayMs: defaultBaseDelayMs,
		MaxDelayMs:  defaultMaxDelayMs,
	}
	defaultNS := "default"

	if config != nil {
		if config.TimeoutMs > 0 {
			timeoutMs = config.TimeoutMs
		}
		if config.Retry != nil {
			if config.Retry.MaxRetries >= 0 {
				retry.MaxRetries = config.Retry.MaxRetries
			}
			if config.Retry.BaseDelayMs > 0 {
				retry.BaseDelayMs = config.Retry.BaseDelayMs
			}
			if config.Retry.MaxDelayMs > 0 {
				retry.MaxDelayMs = config.Retry.MaxDelayMs
			}
		}
		if config.DefaultNamespace != "" {
			defaultNS = config.DefaultNamespace
		}
	}

	return &Client{
		baseURL: serverURL,
		httpClient: &http.Client{
			Timeout: time.Duration(timeoutMs) * time.Millisecond,
		},
		retryConfig:      retry,
		defaultNamespace: defaultNS,
	}
}

// Close releases resources held by the client.
func (c *Client) Close() {
	c.httpClient.CloseIdleConnections()
}

// resolveNamespace returns the namespace to use: override > default.
func (c *Client) resolveNamespace(override string) string {
	if override != "" {
		return override
	}
	return c.defaultNamespace
}

// retryDelay computes the delay for the given attempt using exponential backoff.
func (c *Client) retryDelay(attempt int) time.Duration {
	delay := float64(c.retryConfig.BaseDelayMs) * math.Pow(2, float64(attempt))
	if delay > float64(c.retryConfig.MaxDelayMs) {
		delay = float64(c.retryConfig.MaxDelayMs)
	}
	return time.Duration(delay) * time.Millisecond
}

// doRequest executes an HTTP request with retry logic.
// It retries on 5xx errors and connection errors, but NOT on 4xx errors.
func (c *Client) doRequest(ctx context.Context, method, path string, body interface{}, namespace string) ([]byte, error) {
	url := c.baseURL + path
	ns := c.resolveNamespace(namespace)

	var lastErr error
	maxAttempts := c.retryConfig.MaxRetries + 1

	for attempt := 0; attempt < maxAttempts; attempt++ {
		var reqBody io.Reader
		if body != nil {
			jsonBytes, err := json.Marshal(body)
			if err != nil {
				return nil, fmt.Errorf("failed to marshal request body: %w", err)
			}
			reqBody = bytes.NewReader(jsonBytes)
		}

		req, err := http.NewRequestWithContext(ctx, method, url, reqBody)
		if err != nil {
			return nil, fmt.Errorf("failed to create request: %w", err)
		}

		req.Header.Set("Accept", "application/json")
		if body != nil {
			req.Header.Set("Content-Type", "application/json")
		}
		req.Header.Set(namespaceHeader, ns)

		resp, err := c.httpClient.Do(req)
		if err != nil {
			lastErr = &UcotronConnectionError{
				Message: fmt.Sprintf("request to %s %s failed", method, path),
				Cause:   err,
			}
			if attempt < maxAttempts-1 {
				select {
				case <-ctx.Done():
					return nil, ctx.Err()
				case <-time.After(c.retryDelay(attempt)):
				}
			}
			continue
		}

		respBody, err := io.ReadAll(resp.Body)
		resp.Body.Close()
		if err != nil {
			lastErr = &UcotronConnectionError{
				Message: "failed to read response body",
				Cause:   err,
			}
			if attempt < maxAttempts-1 {
				select {
				case <-ctx.Done():
					return nil, ctx.Err()
				case <-time.After(c.retryDelay(attempt)):
				}
			}
			continue
		}

		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			return respBody, nil
		}

		// 4xx errors are NOT retryable — return immediately
		if resp.StatusCode >= 400 && resp.StatusCode < 500 {
			serverErr := parseServerError(resp.StatusCode, respBody)
			return nil, serverErr
		}

		// 5xx errors are retryable
		lastErr = parseServerError(resp.StatusCode, respBody)
		if attempt < maxAttempts-1 {
			select {
			case <-ctx.Done():
				return nil, ctx.Err()
			case <-time.After(c.retryDelay(attempt)):
			}
		}
	}

	return nil, &UcotronRetriesExhaustedError{
		Attempts:  maxAttempts,
		LastError: lastErr,
	}
}

// parseServerError attempts to parse the error body as APIErrorBody, falling back to status text.
func parseServerError(statusCode int, body []byte) *UcotronServerError {
	var apiErr APIErrorBody
	if err := json.Unmarshal(body, &apiErr); err == nil && apiErr.Message != "" {
		return &UcotronServerError{
			StatusCode: statusCode,
			Code:       apiErr.Code,
			Message:    apiErr.Message,
		}
	}
	return &UcotronServerError{
		StatusCode: statusCode,
		Code:       http.StatusText(statusCode),
		Message:    string(body),
	}
}

// --- Options Types ---

// AugmentOptions are optional parameters for Augment.
type AugmentOptions struct {
	Limit     *int
	Namespace string
}

// LearnOptions are optional parameters for Learn.
type LearnOptions struct {
	Namespace string
	Metadata  map[string]interface{}
}

// SearchOptions are optional parameters for Search.
type SearchOptions struct {
	Limit     *int
	Namespace string
	NodeType  *string
	TimeRange *[2]int64
}

// AddMemoryOptions are optional parameters for AddMemory.
type AddMemoryOptions struct {
	Namespace string
	Metadata  map[string]interface{}
}

// EntityOptions are optional parameters for entity operations.
type EntityOptions struct {
	Namespace string
}

// ListMemoriesOptions are optional parameters for ListMemories.
type ListMemoriesOptions struct {
	NodeType  *string
	Limit     *int
	Offset    *int
	Namespace string
}

// ListEntitiesOptions are optional parameters for ListEntities.
type ListEntitiesOptions struct {
	Limit     *int
	Offset    *int
	Namespace string
}

// --- Client Methods (12 total) ---

// Augment returns context-augmented memories relevant to the given context text.
func (c *Client) Augment(ctx context.Context, contextText string, opts *AugmentOptions) (*AugmentResponse, error) {
	reqBody := AugmentRequest{Context: contextText}
	namespace := ""
	if opts != nil {
		reqBody.Limit = opts.Limit
		namespace = opts.Namespace
	}

	data, err := c.doRequest(ctx, http.MethodPost, "/api/v1/augment", reqBody, namespace)
	if err != nil {
		return nil, err
	}

	var result AugmentResponse
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse augment response: %w", err)
	}
	return &result, nil
}

// Learn extracts and stores memories from agent output text.
func (c *Client) Learn(ctx context.Context, output string, opts *LearnOptions) (*LearnResponse, error) {
	reqBody := LearnRequest{Output: output}
	namespace := ""
	if opts != nil {
		reqBody.Metadata = opts.Metadata
		namespace = opts.Namespace
	}

	data, err := c.doRequest(ctx, http.MethodPost, "/api/v1/learn", reqBody, namespace)
	if err != nil {
		return nil, err
	}

	var result LearnResponse
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse learn response: %w", err)
	}
	return &result, nil
}

// Search performs a semantic search for memories matching the query.
func (c *Client) Search(ctx context.Context, query string, opts *SearchOptions) (*SearchResponse, error) {
	reqBody := SearchRequest{Query: query}
	namespace := ""
	if opts != nil {
		reqBody.Limit = opts.Limit
		reqBody.NodeType = opts.NodeType
		reqBody.TimeRange = opts.TimeRange
		namespace = opts.Namespace
	}

	data, err := c.doRequest(ctx, http.MethodPost, "/api/v1/memories/search", reqBody, namespace)
	if err != nil {
		return nil, err
	}

	var result SearchResponse
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse search response: %w", err)
	}
	return &result, nil
}

// AddMemory ingests a text as a new memory.
func (c *Client) AddMemory(ctx context.Context, text string, opts *AddMemoryOptions) (*CreateMemoryResponse, error) {
	reqBody := CreateMemoryRequest{Text: text}
	namespace := ""
	if opts != nil {
		reqBody.Metadata = opts.Metadata
		namespace = opts.Namespace
	}

	data, err := c.doRequest(ctx, http.MethodPost, "/api/v1/memories", reqBody, namespace)
	if err != nil {
		return nil, err
	}

	var result CreateMemoryResponse
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse create memory response: %w", err)
	}
	return &result, nil
}

// GetMemory retrieves a single memory by ID.
func (c *Client) GetMemory(ctx context.Context, id int64, opts *EntityOptions) (*MemoryResponse, error) {
	namespace := ""
	if opts != nil {
		namespace = opts.Namespace
	}

	data, err := c.doRequest(ctx, http.MethodGet, fmt.Sprintf("/api/v1/memories/%d", id), nil, namespace)
	if err != nil {
		return nil, err
	}

	var result MemoryResponse
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse memory response: %w", err)
	}
	return &result, nil
}

// ListMemories returns a paginated list of memories.
func (c *Client) ListMemories(ctx context.Context, opts *ListMemoriesOptions) ([]MemoryResponse, error) {
	path := "/api/v1/memories"
	namespace := ""
	params := []string{}

	if opts != nil {
		namespace = opts.Namespace
		if opts.Limit != nil {
			params = append(params, fmt.Sprintf("limit=%d", *opts.Limit))
		}
		if opts.Offset != nil {
			params = append(params, fmt.Sprintf("offset=%d", *opts.Offset))
		}
		if opts.NodeType != nil {
			params = append(params, fmt.Sprintf("node_type=%s", *opts.NodeType))
		}
	}

	if len(params) > 0 {
		path += "?" + strings.Join(params, "&")
	}

	data, err := c.doRequest(ctx, http.MethodGet, path, nil, namespace)
	if err != nil {
		return nil, err
	}

	var result []MemoryResponse
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse list memories response: %w", err)
	}
	return result, nil
}

// UpdateMemory updates a memory's content and/or metadata.
func (c *Client) UpdateMemory(ctx context.Context, id int64, req *UpdateMemoryRequest, opts *EntityOptions) (*MemoryResponse, error) {
	namespace := ""
	if opts != nil {
		namespace = opts.Namespace
	}

	data, err := c.doRequest(ctx, http.MethodPut, fmt.Sprintf("/api/v1/memories/%d", id), req, namespace)
	if err != nil {
		return nil, err
	}

	var result MemoryResponse
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse update memory response: %w", err)
	}
	return &result, nil
}

// DeleteMemory soft-deletes a memory by ID.
func (c *Client) DeleteMemory(ctx context.Context, id int64, opts *EntityOptions) error {
	namespace := ""
	if opts != nil {
		namespace = opts.Namespace
	}

	_, err := c.doRequest(ctx, http.MethodDelete, fmt.Sprintf("/api/v1/memories/%d", id), nil, namespace)
	return err
}

// GetEntity retrieves an entity by ID with its neighbors.
func (c *Client) GetEntity(ctx context.Context, id int64, opts *EntityOptions) (*EntityResponse, error) {
	namespace := ""
	if opts != nil {
		namespace = opts.Namespace
	}

	data, err := c.doRequest(ctx, http.MethodGet, fmt.Sprintf("/api/v1/entities/%d", id), nil, namespace)
	if err != nil {
		return nil, err
	}

	var result EntityResponse
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse entity response: %w", err)
	}
	return &result, nil
}

// ListEntities returns a paginated list of entities.
func (c *Client) ListEntities(ctx context.Context, opts *ListEntitiesOptions) ([]EntityResponse, error) {
	path := "/api/v1/entities"
	namespace := ""
	params := []string{}

	if opts != nil {
		namespace = opts.Namespace
		if opts.Limit != nil {
			params = append(params, fmt.Sprintf("limit=%d", *opts.Limit))
		}
		if opts.Offset != nil {
			params = append(params, fmt.Sprintf("offset=%d", *opts.Offset))
		}
	}

	if len(params) > 0 {
		path += "?" + strings.Join(params, "&")
	}

	data, err := c.doRequest(ctx, http.MethodGet, path, nil, namespace)
	if err != nil {
		return nil, err
	}

	var result []EntityResponse
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse list entities response: %w", err)
	}
	return result, nil
}

// Health returns the server health status.
func (c *Client) Health(ctx context.Context) (*HealthResponse, error) {
	data, err := c.doRequest(ctx, http.MethodGet, "/api/v1/health", nil, "")
	if err != nil {
		return nil, err
	}

	var result HealthResponse
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse health response: %w", err)
	}
	return &result, nil
}

// Metrics returns server metrics (request counts, uptime).
func (c *Client) Metrics(ctx context.Context) (*MetricsResponse, error) {
	data, err := c.doRequest(ctx, http.MethodGet, "/api/v1/metrics", nil, "")
	if err != nil {
		return nil, err
	}

	var result MetricsResponse
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse metrics response: %w", err)
	}
	return &result, nil
}
