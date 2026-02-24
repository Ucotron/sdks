import {
  Ucotron,
  UcotronServerError,
  UcotronConnectionError,
  UcotronRetriesExhaustedError,
} from "../src";
import type {
  AugmentResponse,
  LearnResponse,
  SearchResponse,
  CreateMemoryResponse,
  EntityResponse,
  MemoryResponse,
  HealthResponse,
  MetricsResponse,
} from "../src";

// ─── Mock fetch globally ─────────────────────────────────────

const mockFetch = jest.fn();
global.fetch = mockFetch as unknown as typeof fetch;

// Helper to create a successful JSON response
function jsonResponse(data: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: "OK",
    headers: new Headers({ "content-type": "application/json" }),
    json: async () => data,
  } as unknown as Response;
}

// Helper to create an error response
function errorResponse(
  status: number,
  code: string,
  message: string
): Response {
  return {
    ok: false,
    status,
    statusText: message,
    headers: new Headers({ "content-type": "application/json" }),
    json: async () => ({ code, message }),
  } as unknown as Response;
}

beforeEach(() => {
  mockFetch.mockReset();
});

// ─── Constructor Tests ──────────────────────────────────────

describe("Ucotron constructor", () => {
  test("strips trailing slashes from server URL", () => {
    const client = new Ucotron("http://localhost:8420///");
    expect(client.serverUrl).toBe("http://localhost:8420");
  });

  test("accepts config options", () => {
    const client = new Ucotron("http://localhost:8420", {
      timeoutMs: 5000,
      retry: { maxRetries: 5 },
      defaultNamespace: "prod",
    });
    expect(client.serverUrl).toBe("http://localhost:8420");
  });
});

// ─── augment() ──────────────────────────────────────────────

describe("augment", () => {
  test("sends POST to /api/v1/augment with context", async () => {
    const mockResponse: AugmentResponse = {
      memories: [],
      entities: [],
      context_text: "Relevant context here",
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(mockResponse));

    const client = new Ucotron("http://localhost:8420");
    const result = await client.augment("Tell me about Juan");

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [url, opts] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:8420/api/v1/augment");
    expect(opts.method).toBe("POST");
    expect(JSON.parse(opts.body)).toEqual({ context: "Tell me about Juan" });
    expect(result.context_text).toBe("Relevant context here");
  });

  test("passes namespace header", async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ memories: [], entities: [], context_text: "" })
    );

    const client = new Ucotron("http://localhost:8420");
    await client.augment("test", { namespace: "myns" });

    const [, opts] = mockFetch.mock.calls[0];
    expect(opts.headers["X-Ucotron-Namespace"]).toBe("myns");
  });

  test("uses default namespace from config", async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ memories: [], entities: [], context_text: "" })
    );

    const client = new Ucotron("http://localhost:8420", {
      defaultNamespace: "global",
    });
    await client.augment("test");

    const [, opts] = mockFetch.mock.calls[0];
    expect(opts.headers["X-Ucotron-Namespace"]).toBe("global");
  });

  test("option namespace overrides default", async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ memories: [], entities: [], context_text: "" })
    );

    const client = new Ucotron("http://localhost:8420", {
      defaultNamespace: "global",
    });
    await client.augment("test", { namespace: "override" });

    const [, opts] = mockFetch.mock.calls[0];
    expect(opts.headers["X-Ucotron-Namespace"]).toBe("override");
  });

  test("passes limit option", async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ memories: [], entities: [], context_text: "" })
    );

    const client = new Ucotron("http://localhost:8420");
    await client.augment("test", { limit: 5 });

    const [, opts] = mockFetch.mock.calls[0];
    expect(JSON.parse(opts.body)).toEqual({ context: "test", limit: 5 });
  });
});

// ─── learn() ────────────────────────────────────────────────

describe("learn", () => {
  test("sends POST to /api/v1/learn", async () => {
    const mockResponse: LearnResponse = {
      memories_created: 2,
      entities_found: 3,
      conflicts_found: 0,
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(mockResponse));

    const client = new Ucotron("http://localhost:8420");
    const result = await client.learn("Agent output text");

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [url, opts] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:8420/api/v1/learn");
    expect(JSON.parse(opts.body)).toEqual({ output: "Agent output text" });
    expect(result.memories_created).toBe(2);
  });

  test("includes metadata when provided", async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ memories_created: 0, entities_found: 0, conflicts_found: 0 })
    );

    const client = new Ucotron("http://localhost:8420");
    await client.learn("output", { metadata: { source: "agent" } });

    const [, opts] = mockFetch.mock.calls[0];
    expect(JSON.parse(opts.body)).toEqual({
      output: "output",
      metadata: { source: "agent" },
    });
  });
});

// ─── search() ───────────────────────────────────────────────

describe("search", () => {
  test("sends POST to /api/v1/memories/search", async () => {
    const mockResponse: SearchResponse = {
      results: [
        {
          id: 1,
          content: "Juan lives in Berlin",
          node_type: "Entity",
          score: 0.95,
          vector_sim: 0.9,
          graph_centrality: 0.8,
          recency: 0.7,
        },
      ],
      total: 1,
      query: "Where does Juan live?",
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(mockResponse));

    const client = new Ucotron("http://localhost:8420");
    const result = await client.search("Where does Juan live?");

    expect(result.results).toHaveLength(1);
    expect(result.results[0].content).toBe("Juan lives in Berlin");
  });

  test("passes search options", async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ results: [], total: 0, query: "q" })
    );

    const client = new Ucotron("http://localhost:8420");
    await client.search("q", {
      limit: 5,
      nodeType: "Entity",
      timeRange: [1000, 2000],
    });

    const [, opts] = mockFetch.mock.calls[0];
    expect(JSON.parse(opts.body)).toEqual({
      query: "q",
      limit: 5,
      node_type: "Entity",
      time_range: [1000, 2000],
    });
  });
});

// ─── addMemory() ────────────────────────────────────────────

describe("addMemory", () => {
  test("sends POST to /api/v1/memories", async () => {
    const mockResponse: CreateMemoryResponse = {
      chunk_node_ids: [1000001],
      entity_node_ids: [1000002, 1000003],
      edges_created: 3,
      metrics: {
        chunks_processed: 1,
        entities_extracted: 2,
        relations_extracted: 1,
        contradictions_detected: 0,
        total_us: 5000,
      },
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(mockResponse));

    const client = new Ucotron("http://localhost:8420");
    const result = await client.addMemory("Juan moved to Berlin");

    expect(result.chunk_node_ids).toEqual([1000001]);
    expect(result.entity_node_ids).toHaveLength(2);
  });
});

// ─── getEntity() / listEntities() ──────────────────────────

describe("entities", () => {
  test("getEntity sends GET to /api/v1/entities/:id", async () => {
    const mockEntity: EntityResponse = {
      id: 42,
      content: "Juan",
      node_type: "Entity",
      timestamp: 1000,
      metadata: {},
      neighbors: [
        { node_id: 43, content: "Berlin", edge_type: "MOVED_TO", weight: 1.0 },
      ],
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(mockEntity));

    const client = new Ucotron("http://localhost:8420");
    const result = await client.getEntity(42);

    const [url] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:8420/api/v1/entities/42");
    expect(result.neighbors).toHaveLength(1);
  });

  test("listEntities sends GET with query params", async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse([]));

    const client = new Ucotron("http://localhost:8420");
    await client.listEntities({ limit: 10, offset: 5 });

    const [url] = mockFetch.mock.calls[0];
    expect(url).toBe(
      "http://localhost:8420/api/v1/entities?limit=10&offset=5"
    );
  });

  test("listEntities omits params when not set", async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse([]));

    const client = new Ucotron("http://localhost:8420");
    await client.listEntities();

    const [url] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:8420/api/v1/entities");
  });
});

// ─── Memory CRUD ────────────────────────────────────────────

describe("memory CRUD", () => {
  test("getMemory sends GET to /api/v1/memories/:id", async () => {
    const mock: MemoryResponse = {
      id: 1,
      content: "test",
      node_type: "Event",
      timestamp: 1000,
      metadata: {},
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(mock));

    const client = new Ucotron("http://localhost:8420");
    const result = await client.getMemory(1);

    const [url] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:8420/api/v1/memories/1");
    expect(result.content).toBe("test");
  });

  test("listMemories sends GET with query params", async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse([]));

    const client = new Ucotron("http://localhost:8420");
    await client.listMemories({ nodeType: "Entity", limit: 20 });

    const [url] = mockFetch.mock.calls[0];
    expect(url).toContain("node_type=Entity");
    expect(url).toContain("limit=20");
  });

  test("updateMemory sends PUT", async () => {
    const mock: MemoryResponse = {
      id: 1,
      content: "updated",
      node_type: "Event",
      timestamp: 1000,
      metadata: {},
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(mock));

    const client = new Ucotron("http://localhost:8420");
    const result = await client.updateMemory(1, { content: "updated" });

    const [url, opts] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:8420/api/v1/memories/1");
    expect(opts.method).toBe("PUT");
    expect(result.content).toBe("updated");
  });

  test("deleteMemory sends DELETE", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 204,
      statusText: "No Content",
      headers: new Headers({ "content-length": "0" }),
      json: async () => undefined,
    } as unknown as Response);

    const client = new Ucotron("http://localhost:8420");
    await client.deleteMemory(1);

    const [url, opts] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:8420/api/v1/memories/1");
    expect(opts.method).toBe("DELETE");
  });
});

// ─── Health & Metrics ───────────────────────────────────────

describe("health and metrics", () => {
  test("health sends GET to /api/v1/health", async () => {
    const mock: HealthResponse = {
      status: "ok",
      version: "0.1.0",
      instance_id: "test-001",
      instance_role: "standalone",
      storage_mode: "embedded",
      vector_backend: "helix_hnsw",
      graph_backend: "helix",
      models: {
        embedder_loaded: true,
        embedding_model: "all-MiniLM-L6-v2",
        ner_loaded: true,
        relation_extractor_loaded: false,
        transcriber_loaded: false,
      },
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(mock));

    const client = new Ucotron("http://localhost:8420");
    const result = await client.health();

    expect(result.status).toBe("ok");
    expect(result.models.embedder_loaded).toBe(true);
  });

  test("metrics sends GET to /api/v1/metrics", async () => {
    const mock: MetricsResponse = {
      instance_id: "test-001",
      total_requests: 100,
      total_ingestions: 25,
      total_searches: 50,
      uptime_secs: 3600,
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(mock));

    const client = new Ucotron("http://localhost:8420");
    const result = await client.metrics();

    expect(result.total_requests).toBe(100);
  });
});

// ─── Error Handling ─────────────────────────────────────────

describe("error handling", () => {
  test("throws UcotronServerError on 4xx", async () => {
    mockFetch.mockResolvedValueOnce(
      errorResponse(404, "NOT_FOUND", "Memory not found")
    );

    const client = new Ucotron("http://localhost:8420");
    await expect(client.getMemory(999)).rejects.toThrow(UcotronServerError);
    await expect(client.getMemory(999)).rejects.toThrow(); // second call for coverage
  });

  test("UcotronServerError has status and code", async () => {
    mockFetch.mockResolvedValueOnce(
      errorResponse(400, "VALIDATION_ERROR", "Invalid input")
    );

    const client = new Ucotron("http://localhost:8420");
    try {
      await client.search("");
      fail("Should have thrown");
    } catch (err) {
      expect(err).toBeInstanceOf(UcotronServerError);
      const serverErr = err as UcotronServerError;
      expect(serverErr.status).toBe(400);
      expect(serverErr.code).toBe("VALIDATION_ERROR");
    }
  });

  test("does NOT retry 4xx errors", async () => {
    mockFetch.mockResolvedValue(
      errorResponse(404, "NOT_FOUND", "Not found")
    );

    const client = new Ucotron("http://localhost:8420", {
      retry: { maxRetries: 3 },
    });

    await expect(client.getMemory(999)).rejects.toThrow(UcotronServerError);
    // Should only call once — no retries for 4xx
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  test("retries 5xx errors", async () => {
    mockFetch
      .mockResolvedValueOnce(
        errorResponse(500, "INTERNAL_ERROR", "Server error")
      )
      .mockResolvedValueOnce(
        errorResponse(500, "INTERNAL_ERROR", "Server error")
      )
      .mockResolvedValueOnce(
        jsonResponse({ memories: [], entities: [], context_text: "" })
      );

    const client = new Ucotron("http://localhost:8420", {
      retry: { maxRetries: 3, baseDelayMs: 1 },
    });

    const result = await client.augment("test");
    expect(result.context_text).toBe("");
    // 2 failures + 1 success = 3 calls
    expect(mockFetch).toHaveBeenCalledTimes(3);
  });

  test("throws UcotronRetriesExhaustedError when all retries fail", async () => {
    mockFetch.mockResolvedValue(
      errorResponse(503, "UNAVAILABLE", "Service unavailable")
    );

    const client = new Ucotron("http://localhost:8420", {
      retry: { maxRetries: 2, baseDelayMs: 1 },
    });

    await expect(client.augment("test")).rejects.toThrow(
      UcotronRetriesExhaustedError
    );
    // 1 initial + 2 retries = 3 calls
    expect(mockFetch).toHaveBeenCalledTimes(3);
  });

  test("wraps connection errors in UcotronRetriesExhaustedError", async () => {
    mockFetch.mockRejectedValueOnce(new Error("ECONNREFUSED"));

    const client = new Ucotron("http://localhost:8420", {
      retry: { maxRetries: 0 },
    });

    try {
      await client.health();
      fail("Should have thrown");
    } catch (err) {
      expect(err).toBeInstanceOf(UcotronRetriesExhaustedError);
      const retriesErr = err as UcotronRetriesExhaustedError;
      expect(retriesErr.attempts).toBe(1);
      expect(retriesErr.lastError).toBeInstanceOf(UcotronConnectionError);
    }
  });

  test("retries connection errors", async () => {
    mockFetch
      .mockRejectedValueOnce(new Error("ECONNREFUSED"))
      .mockResolvedValueOnce(
        jsonResponse({ status: "ok", version: "0.1.0", instance_id: "x", instance_role: "standalone", storage_mode: "embedded", vector_backend: "helix", graph_backend: "helix", models: { embedder_loaded: false, embedding_model: "", ner_loaded: false, relation_extractor_loaded: false, transcriber_loaded: false } })
      );

    const client = new Ucotron("http://localhost:8420", {
      retry: { maxRetries: 2, baseDelayMs: 1 },
    });

    const result = await client.health();
    expect(result.status).toBe("ok");
    expect(mockFetch).toHaveBeenCalledTimes(2);
  });
});

// ─── Type Exports ───────────────────────────────────────────

describe("type exports", () => {
  test("all types are importable", () => {
    // This test verifies at compile-time that all types are exported
    const _augmentOpts: import("../src").AugmentOptions = {};
    const _learnOpts: import("../src").LearnOptions = {};
    const _searchOpts: import("../src").SearchOptions = {};
    const _addOpts: import("../src").AddMemoryOptions = {};
    const _entityOpts: import("../src").EntityOptions = {};
    const _listMemOpts: import("../src").ListMemoriesOptions = {};
    const _listEntOpts: import("../src").ListEntitiesOptions = {};
    const _config: import("../src").ClientConfig = {};
    expect(true).toBe(true);
  });
});
