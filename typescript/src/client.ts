import {
  ClientConfig,
  RetryConfig,
  AugmentOptions,
  AugmentResponse,
  LearnOptions,
  LearnResponse,
  SearchOptions,
  SearchResponse,
  AddMemoryOptions,
  CreateMemoryResponse,
  EntityOptions,
  EntityResponse,
  MemoryResponse,
  HealthResponse,
  MetricsResponse,
  ListMemoriesOptions,
  ListEntitiesOptions,
  ApiErrorBody,
} from "./types";
import {
  UcotronServerError,
  UcotronConnectionError,
  UcotronRetriesExhaustedError,
} from "./errors";

const DEFAULT_RETRY: RetryConfig = {
  maxRetries: 3,
  baseDelayMs: 100,
  maxDelayMs: 5000,
};

const DEFAULT_TIMEOUT_MS = 30_000;
const NAMESPACE_HEADER = "X-Ucotron-Namespace";

/**
 * Ucotron TypeScript SDK client.
 *
 * Uses the native `fetch` API (available in Node.js 18+ and all modern browsers).
 * All methods are async and return typed responses.
 *
 * @example
 * ```ts
 * const ucotron = new Ucotron("http://localhost:8420");
 * const ctx = await ucotron.augment("Tell me about Juan");
 * console.log(ctx.context_text);
 * ```
 */
export class Ucotron {
  private readonly baseUrl: string;
  private readonly timeoutMs: number;
  private readonly retry: RetryConfig;
  private readonly defaultNamespace?: string;

  constructor(serverUrl: string, config?: ClientConfig) {
    // Strip trailing slash
    this.baseUrl = serverUrl.replace(/\/+$/, "");
    this.timeoutMs = config?.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    this.retry = { ...DEFAULT_RETRY, ...config?.retry };
    this.defaultNamespace = config?.defaultNamespace;
  }

  /** Returns the configured server URL. */
  get serverUrl(): string {
    return this.baseUrl;
  }

  // ─── Core API Methods ──────────────────────────────────────

  /**
   * Context augmentation — returns relevant memories for a given context.
   * Inject the returned `context_text` into your LLM prompt.
   */
  async augment(
    context: string,
    opts?: AugmentOptions
  ): Promise<AugmentResponse> {
    return this.post<AugmentResponse>("/api/v1/augment", {
      body: { context, limit: opts?.limit },
      namespace: opts?.namespace,
    });
  }

  /**
   * Learn from agent output — extracts and stores memories automatically.
   */
  async learn(output: string, opts?: LearnOptions): Promise<LearnResponse> {
    return this.post<LearnResponse>("/api/v1/learn", {
      body: { output, metadata: opts?.metadata },
      namespace: opts?.namespace,
    });
  }

  /**
   * Semantic search for memories matching a query.
   */
  async search(query: string, opts?: SearchOptions): Promise<SearchResponse> {
    return this.post<SearchResponse>("/api/v1/memories/search", {
      body: {
        query,
        limit: opts?.limit,
        node_type: opts?.nodeType,
        time_range: opts?.timeRange,
      },
      namespace: opts?.namespace,
    });
  }

  /**
   * Ingest a text as a new memory.
   */
  async addMemory(
    text: string,
    opts?: AddMemoryOptions
  ): Promise<CreateMemoryResponse> {
    return this.post<CreateMemoryResponse>("/api/v1/memories", {
      body: { text, metadata: opts?.metadata },
      namespace: opts?.namespace,
    });
  }

  /**
   * Get a single entity by ID with its relations.
   */
  async getEntity(
    id: number,
    opts?: EntityOptions
  ): Promise<EntityResponse> {
    return this.get<EntityResponse>(`/api/v1/entities/${id}`, {
      namespace: opts?.namespace,
    });
  }

  /**
   * List entities in the knowledge graph.
   */
  async listEntities(opts?: ListEntitiesOptions): Promise<EntityResponse[]> {
    const params = new URLSearchParams();
    if (opts?.limit !== undefined) params.set("limit", String(opts.limit));
    if (opts?.offset !== undefined) params.set("offset", String(opts.offset));
    const query = params.toString();
    const path = query ? `/api/v1/entities?${query}` : "/api/v1/entities";
    return this.get<EntityResponse[]>(path, {
      namespace: opts?.namespace,
    });
  }

  /**
   * Get a single memory by ID.
   */
  async getMemory(id: number): Promise<MemoryResponse> {
    return this.get<MemoryResponse>(`/api/v1/memories/${id}`);
  }

  /**
   * List memories with optional filters.
   */
  async listMemories(opts?: ListMemoriesOptions): Promise<MemoryResponse[]> {
    const params = new URLSearchParams();
    if (opts?.nodeType) params.set("node_type", opts.nodeType);
    if (opts?.limit !== undefined) params.set("limit", String(opts.limit));
    if (opts?.offset !== undefined) params.set("offset", String(opts.offset));
    const query = params.toString();
    const path = query ? `/api/v1/memories?${query}` : "/api/v1/memories";
    return this.get<MemoryResponse[]>(path, {
      namespace: opts?.namespace,
    });
  }

  /**
   * Update a memory's content or metadata.
   */
  async updateMemory(
    id: number,
    update: { content?: string; metadata?: Record<string, unknown> }
  ): Promise<MemoryResponse> {
    return this.requestJson<MemoryResponse>(`/api/v1/memories/${id}`, {
      method: "PUT",
      body: update,
    });
  }

  /**
   * Delete a memory by ID (soft delete).
   */
  async deleteMemory(id: number): Promise<void> {
    await this.requestJson<unknown>(`/api/v1/memories/${id}`, {
      method: "DELETE",
    });
  }

  /**
   * Health check — returns server status and component info.
   */
  async health(): Promise<HealthResponse> {
    return this.get<HealthResponse>("/api/v1/health");
  }

  /**
   * Server metrics — returns request counts and uptime.
   */
  async metrics(): Promise<MetricsResponse> {
    return this.get<MetricsResponse>("/api/v1/metrics");
  }

  // ─── HTTP Helpers ──────────────────────────────────────────

  private async get<T>(
    path: string,
    opts?: { namespace?: string }
  ): Promise<T> {
    return this.requestJson<T>(path, {
      method: "GET",
      namespace: opts?.namespace,
    });
  }

  private async post<T>(
    path: string,
    opts: { body: unknown; namespace?: string }
  ): Promise<T> {
    return this.requestJson<T>(path, {
      method: "POST",
      body: opts.body,
      namespace: opts.namespace,
    });
  }

  private async requestJson<T>(
    path: string,
    opts: {
      method: string;
      body?: unknown;
      namespace?: string;
    }
  ): Promise<T> {
    const url = `${this.baseUrl}${path}`;
    const namespace = opts.namespace ?? this.defaultNamespace;

    const headers: Record<string, string> = {
      Accept: "application/json",
    };
    if (namespace) {
      headers[NAMESPACE_HEADER] = namespace;
    }

    const fetchOpts: RequestInit = {
      method: opts.method,
      headers,
      signal: AbortSignal.timeout(this.timeoutMs),
    };

    if (opts.body !== undefined) {
      headers["Content-Type"] = "application/json";
      fetchOpts.body = JSON.stringify(opts.body);
    }

    return this.withRetry(async () => {
      let response: Response;
      try {
        response = await fetch(url, fetchOpts);
      } catch (err) {
        throw new UcotronConnectionError(
          `Failed to connect to ${this.baseUrl}`,
          err instanceof Error ? err : undefined
        );
      }

      if (!response.ok) {
        let code = "UNKNOWN_ERROR";
        let message = response.statusText;
        try {
          const body = (await response.json()) as ApiErrorBody;
          code = body.code ?? code;
          message = body.message ?? message;
        } catch {
          // response body not JSON — use status text
        }
        throw new UcotronServerError(response.status, code, message);
      }

      // DELETE may return empty body
      if (
        response.status === 204 ||
        response.headers.get("content-length") === "0"
      ) {
        return undefined as unknown as T;
      }

      return (await response.json()) as T;
    });
  }

  private async withRetry<T>(fn: () => Promise<T>): Promise<T> {
    let lastError: Error | undefined;

    for (let attempt = 0; attempt <= this.retry.maxRetries; attempt++) {
      try {
        return await fn();
      } catch (err) {
        lastError = err instanceof Error ? err : new Error(String(err));

        // Don't retry client errors (4xx)
        if (err instanceof UcotronServerError && err.status < 500) {
          throw err;
        }

        // Last attempt — don't sleep
        if (attempt === this.retry.maxRetries) {
          break;
        }

        const delay = Math.min(
          this.retry.baseDelayMs * Math.pow(2, attempt),
          this.retry.maxDelayMs
        );
        await sleep(delay);
      }
    }

    throw new UcotronRetriesExhaustedError(
      this.retry.maxRetries + 1,
      lastError!
    );
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
