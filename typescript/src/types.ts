// ============================================================
// Ucotron TypeScript SDK — Types
// Generated to match server API (ucotron_server/src/types.rs)
// ============================================================

// --- Memory Types ---

export interface CreateMemoryRequest {
  text: string;
  metadata?: Record<string, unknown>;
}

export interface IngestionMetricsResponse {
  chunks_processed: number;
  entities_extracted: number;
  relations_extracted: number;
  contradictions_detected: number;
  total_us: number;
}

export interface CreateMemoryResponse {
  chunk_node_ids: number[];
  entity_node_ids: number[];
  edges_created: number;
  metrics: IngestionMetricsResponse;
}

export interface MemoryResponse {
  id: number;
  content: string;
  node_type: string;
  timestamp: number;
  metadata: Record<string, unknown>;
}

export interface UpdateMemoryRequest {
  content?: string;
  metadata?: Record<string, unknown>;
}

// --- Search Types ---

export interface SearchRequest {
  query: string;
  limit?: number;
  node_type?: string;
  time_range?: [number, number];
}

export interface SearchResultItem {
  id: number;
  content: string;
  node_type: string;
  score: number;
  vector_sim: number;
  graph_centrality: number;
  recency: number;
}

export interface SearchResponse {
  results: SearchResultItem[];
  total: number;
  query: string;
}

// --- Entity Types ---

export interface NeighborResponse {
  node_id: number;
  content: string;
  edge_type: string;
  weight: number;
}

export interface EntityResponse {
  id: number;
  content: string;
  node_type: string;
  timestamp: number;
  metadata: Record<string, unknown>;
  neighbors?: NeighborResponse[];
}

// --- Augment & Learn Types ---

export interface AugmentRequest {
  context: string;
  limit?: number;
}

export interface AugmentResponse {
  memories: SearchResultItem[];
  entities: EntityResponse[];
  context_text: string;
}

export interface LearnRequest {
  output: string;
  metadata?: Record<string, unknown>;
}

export interface LearnResponse {
  memories_created: number;
  entities_found: number;
  conflicts_found: number;
}

// --- Health & Metrics Types ---

export interface ModelStatus {
  embedder_loaded: boolean;
  embedding_model: string;
  ner_loaded: boolean;
  relation_extractor_loaded: boolean;
  transcriber_loaded: boolean;
}

export interface HealthResponse {
  status: string;
  version: string;
  instance_id: string;
  instance_role: string;
  storage_mode: string;
  vector_backend: string;
  graph_backend: string;
  models: ModelStatus;
}

export interface MetricsResponse {
  instance_id: string;
  total_requests: number;
  total_ingestions: number;
  total_searches: number;
  uptime_secs: number;
}

// --- Client Configuration ---

export interface RetryConfig {
  /** Maximum number of retry attempts (default: 3) */
  maxRetries: number;
  /** Base delay in milliseconds for exponential backoff (default: 100) */
  baseDelayMs: number;
  /** Maximum delay in milliseconds (default: 5000) */
  maxDelayMs: number;
}

export interface ClientConfig {
  /** Request timeout in milliseconds (default: 30000) */
  timeoutMs?: number;
  /** Retry configuration */
  retry?: Partial<RetryConfig>;
  /** Default namespace for all requests */
  defaultNamespace?: string;
}

// --- Options Types ---

export interface AugmentOptions {
  limit?: number;
  namespace?: string;
}

export interface LearnOptions {
  namespace?: string;
  metadata?: Record<string, unknown>;
}

export interface SearchOptions {
  limit?: number;
  namespace?: string;
  nodeType?: string;
  timeRange?: [number, number];
}

export interface AddMemoryOptions {
  namespace?: string;
  metadata?: Record<string, unknown>;
}

export interface EntityOptions {
  namespace?: string;
}

export interface ListMemoriesOptions {
  nodeType?: string;
  limit?: number;
  offset?: number;
  namespace?: string;
}

export interface ListEntitiesOptions {
  limit?: number;
  offset?: number;
  namespace?: string;
}

// --- Error Types ---

export interface ApiErrorBody {
  code: string;
  message: string;
}
