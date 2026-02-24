// Package ucotron provides a Go client for the Ucotron cognitive memory server.
//
// Ucotron is a cognitive memory framework for LLMs that provides hybrid
// vector + graph search, entity resolution, and contradiction detection.
//
// Usage:
//
//	client := ucotron.NewClient("http://localhost:8420", nil)
//	defer client.Close()
//
//	result, err := client.Augment(ctx, "What does Juan do?", nil)
//	if err != nil { ... }
//	fmt.Println(result.ContextText)
package ucotron

// --- Memory Types ---

// CreateMemoryRequest is the request body for creating a new memory.
type CreateMemoryRequest struct {
	Text     string                 `json:"text"`
	Metadata map[string]interface{} `json:"metadata,omitempty"`
}

// IngestionMetrics contains timing and count metrics from ingestion.
type IngestionMetrics struct {
	ChunksProcessed        int `json:"chunks_processed"`
	EntitiesExtracted      int `json:"entities_extracted"`
	RelationsExtracted     int `json:"relations_extracted"`
	ContradictionsDetected int `json:"contradictions_detected"`
	TotalUs                int `json:"total_us"`
}

// CreateMemoryResponse is returned after successfully creating a memory.
type CreateMemoryResponse struct {
	ChunkNodeIDs  []int64          `json:"chunk_node_ids"`
	EntityNodeIDs []int64          `json:"entity_node_ids"`
	EdgesCreated  int              `json:"edges_created"`
	Metrics       IngestionMetrics `json:"metrics"`
}

// MemoryResponse represents a single memory node.
type MemoryResponse struct {
	ID        int64                  `json:"id"`
	Content   string                 `json:"content"`
	NodeType  string                 `json:"node_type"`
	Timestamp int64                  `json:"timestamp"`
	Metadata  map[string]interface{} `json:"metadata"`
}

// UpdateMemoryRequest is the request body for updating an existing memory.
type UpdateMemoryRequest struct {
	Content  *string                `json:"content,omitempty"`
	Metadata map[string]interface{} `json:"metadata,omitempty"`
}

// --- Search Types ---

// SearchRequest is the request body for semantic search.
type SearchRequest struct {
	Query     string   `json:"query"`
	Limit     *int     `json:"limit,omitempty"`
	NodeType  *string  `json:"node_type,omitempty"`
	TimeRange *[2]int64 `json:"time_range,omitempty"`
}

// SearchResultItem represents a single search result with scoring breakdown.
type SearchResultItem struct {
	ID              int64   `json:"id"`
	Content         string  `json:"content"`
	NodeType        string  `json:"node_type"`
	Score           float64 `json:"score"`
	VectorSim       float64 `json:"vector_sim"`
	GraphCentrality float64 `json:"graph_centrality"`
	Recency         float64 `json:"recency"`
}

// SearchResponse is the response from a semantic search.
type SearchResponse struct {
	Results []SearchResultItem `json:"results"`
	Total   int                `json:"total"`
	Query   string             `json:"query"`
}

// --- Entity Types ---

// NeighborResponse represents a neighboring node connected by an edge.
type NeighborResponse struct {
	NodeID   int64   `json:"node_id"`
	Content  string  `json:"content"`
	EdgeType string  `json:"edge_type"`
	Weight   float64 `json:"weight"`
}

// EntityResponse represents an entity node with optional neighbors.
type EntityResponse struct {
	ID        int64                  `json:"id"`
	Content   string                 `json:"content"`
	NodeType  string                 `json:"node_type"`
	Timestamp int64                  `json:"timestamp"`
	Metadata  map[string]interface{} `json:"metadata"`
	Neighbors []NeighborResponse     `json:"neighbors,omitempty"`
}

// --- Augment & Learn Types ---

// AugmentRequest is the request body for context augmentation.
type AugmentRequest struct {
	Context string `json:"context"`
	Limit   *int   `json:"limit,omitempty"`
}

// AugmentResponse contains memories, entities, and formatted context text.
type AugmentResponse struct {
	Memories    []SearchResultItem `json:"memories"`
	Entities    []EntityResponse   `json:"entities"`
	ContextText string             `json:"context_text"`
}

// LearnRequest is the request body for learning from agent output.
type LearnRequest struct {
	Output   string                 `json:"output"`
	Metadata map[string]interface{} `json:"metadata,omitempty"`
}

// LearnResponse contains counts of items created during learning.
type LearnResponse struct {
	MemoriesCreated int `json:"memories_created"`
	EntitiesFound   int `json:"entities_found"`
	ConflictsFound  int `json:"conflicts_found"`
}

// --- Health & Metrics Types ---

// ModelStatus reports the status of loaded ML models.
type ModelStatus struct {
	EmbedderLoaded           bool   `json:"embedder_loaded"`
	EmbeddingModel           string `json:"embedding_model"`
	NerLoaded                bool   `json:"ner_loaded"`
	RelationExtractorLoaded  bool   `json:"relation_extractor_loaded"`
	TranscriberLoaded        bool   `json:"transcriber_loaded"`
}

// HealthResponse is the response from the health check endpoint.
type HealthResponse struct {
	Status        string      `json:"status"`
	Version       string      `json:"version"`
	InstanceID    string      `json:"instance_id"`
	InstanceRole  string      `json:"instance_role"`
	StorageMode   string      `json:"storage_mode"`
	VectorBackend string      `json:"vector_backend"`
	GraphBackend  string      `json:"graph_backend"`
	Models        ModelStatus `json:"models"`
}

// MetricsResponse contains server metrics.
type MetricsResponse struct {
	InstanceID      string `json:"instance_id"`
	TotalRequests   int64  `json:"total_requests"`
	TotalIngestions int64  `json:"total_ingestions"`
	TotalSearches   int64  `json:"total_searches"`
	UptimeSecs      int64  `json:"uptime_secs"`
}

// --- API Error Body ---

// APIErrorBody represents the JSON error response from the server.
type APIErrorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}
