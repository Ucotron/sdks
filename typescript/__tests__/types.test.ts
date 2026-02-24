/**
 * Type validation and serialization tests for Ucotron TypeScript SDK.
 * Verifies that all response types correctly parse server JSON responses.
 */

import type {
  AugmentResponse,
  CreateMemoryResponse,
  EntityResponse,
  HealthResponse,
  IngestionMetricsResponse,
  LearnResponse,
  MemoryResponse,
  MetricsResponse,
  ModelStatus,
  NeighborResponse,
  SearchResponse,
  SearchResultItem,
  ClientConfig,
  RetryConfig,
  AugmentOptions,
  LearnOptions,
  SearchOptions,
  AddMemoryOptions,
  ListMemoriesOptions,
  ListEntitiesOptions,
  EntityOptions,
} from "../src";

// ─── HealthResponse ─────────────────────────────────────────

describe("HealthResponse", () => {
  test("parses full server health JSON", () => {
    const json: HealthResponse = {
      status: "ok",
      version: "0.1.0",
      instance_id: "inst-abc",
      instance_role: "writer",
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
    expect(json.status).toBe("ok");
    expect(json.models.embedder_loaded).toBe(true);
    expect(json.models.relation_extractor_loaded).toBe(false);
    expect(json.instance_role).toBe("writer");
    expect(json.storage_mode).toBe("embedded");
  });

  test("models status fields are all booleans", () => {
    const models: ModelStatus = {
      embedder_loaded: false,
      embedding_model: "",
      ner_loaded: false,
      relation_extractor_loaded: false,
      transcriber_loaded: false,
    };
    expect(typeof models.embedder_loaded).toBe("boolean");
    expect(typeof models.ner_loaded).toBe("boolean");
    expect(typeof models.relation_extractor_loaded).toBe("boolean");
    expect(typeof models.embedding_model).toBe("string");
  });
});

// ─── MetricsResponse ────────────────────────────────────────

describe("MetricsResponse", () => {
  test("parses metrics with zero counts", () => {
    const json: MetricsResponse = {
      instance_id: "fresh-instance",
      total_requests: 0,
      total_ingestions: 0,
      total_searches: 0,
      uptime_secs: 0,
    };
    expect(json.total_requests).toBe(0);
    expect(json.uptime_secs).toBe(0);
  });

  test("parses metrics with high counts", () => {
    const json: MetricsResponse = {
      instance_id: "prod-001",
      total_requests: 1_000_000,
      total_ingestions: 250_000,
      total_searches: 500_000,
      uptime_secs: 86400,
    };
    expect(json.total_requests).toBe(1_000_000);
    expect(json.uptime_secs).toBe(86400);
  });
});

// ─── MemoryResponse ─────────────────────────────────────────

describe("MemoryResponse", () => {
  test("parses memory with empty metadata", () => {
    const json: MemoryResponse = {
      id: 1,
      content: "Hello world",
      node_type: "Entity",
      timestamp: 1700000000,
      metadata: {},
    };
    expect(json.metadata).toEqual({});
  });

  test("parses memory with rich metadata", () => {
    const json: MemoryResponse = {
      id: 42,
      content: "Juan lives in Berlin",
      node_type: "Fact",
      timestamp: 1700000000,
      metadata: {
        source: "conversation",
        confidence: 0.95,
        tags: ["location", "personal"],
      },
    };
    expect(json.metadata.source).toBe("conversation");
    expect(json.metadata.confidence).toBe(0.95);
    expect(json.metadata.tags).toEqual(["location", "personal"]);
  });

  test("supports all node types", () => {
    const types = ["Entity", "Event", "Fact", "Skill"];
    types.forEach((t) => {
      const mem: MemoryResponse = {
        id: 1,
        content: "x",
        node_type: t,
        timestamp: 0,
        metadata: {},
      };
      expect(mem.node_type).toBe(t);
    });
  });
});

// ─── SearchResponse & SearchResultItem ──────────────────────

describe("SearchResponse", () => {
  test("parses empty results", () => {
    const json: SearchResponse = {
      results: [],
      total: 0,
      query: "nonexistent",
    };
    expect(json.results).toHaveLength(0);
    expect(json.total).toBe(0);
  });

  test("parses multiple results with score breakdown", () => {
    const item: SearchResultItem = {
      id: 5,
      content: "Machine learning basics",
      node_type: "Fact",
      score: 0.87,
      vector_sim: 0.92,
      graph_centrality: 0.75,
      recency: 0.65,
    };
    expect(item.score).toBeCloseTo(0.87);
    expect(item.vector_sim).toBeGreaterThan(item.graph_centrality);
  });
});

// ─── EntityResponse & NeighborResponse ──────────────────────

describe("EntityResponse", () => {
  test("parses entity without neighbors", () => {
    const json: EntityResponse = {
      id: 10,
      content: "Apple",
      node_type: "Entity",
      timestamp: 1700000000,
      metadata: {},
    };
    expect(json.neighbors).toBeUndefined();
  });

  test("parses entity with multiple neighbors", () => {
    const neighbors: NeighborResponse[] = [
      { node_id: 11, content: "Tim Cook", edge_type: "RELATES_TO", weight: 0.9 },
      { node_id: 12, content: "iPhone", edge_type: "HAS_PROPERTY", weight: 0.8 },
      { node_id: 13, content: "Cupertino", edge_type: "RELATES_TO", weight: 0.7 },
    ];
    const json: EntityResponse = {
      id: 10,
      content: "Apple",
      node_type: "Entity",
      timestamp: 1700000000,
      metadata: {},
      neighbors,
    };
    expect(json.neighbors).toHaveLength(3);
    expect(json.neighbors![0].edge_type).toBe("RELATES_TO");
  });
});

// ─── CreateMemoryResponse & IngestionMetrics ────────────────

describe("CreateMemoryResponse", () => {
  test("parses ingestion with no entities", () => {
    const json: CreateMemoryResponse = {
      chunk_node_ids: [1001],
      entity_node_ids: [],
      edges_created: 0,
      metrics: {
        chunks_processed: 1,
        entities_extracted: 0,
        relations_extracted: 0,
        contradictions_detected: 0,
        total_us: 1500,
      },
    };
    expect(json.entity_node_ids).toHaveLength(0);
    expect(json.metrics.entities_extracted).toBe(0);
  });

  test("ingestion metrics total_us is in microseconds", () => {
    const metrics: IngestionMetricsResponse = {
      chunks_processed: 5,
      entities_extracted: 3,
      relations_extracted: 2,
      contradictions_detected: 1,
      total_us: 15000,
    };
    expect(metrics.total_us).toBe(15000);
    expect(metrics.contradictions_detected).toBe(1);
  });
});

// ─── AugmentResponse ────────────────────────────────────────

describe("AugmentResponse", () => {
  test("parses augment with empty context", () => {
    const json: AugmentResponse = {
      memories: [],
      entities: [],
      context_text: "",
    };
    expect(json.context_text).toBe("");
    expect(json.memories).toHaveLength(0);
  });
});

// ─── LearnResponse ──────────────────────────────────────────

describe("LearnResponse", () => {
  test("parses learn with conflicts", () => {
    const json: LearnResponse = {
      memories_created: 3,
      entities_found: 5,
      conflicts_found: 2,
    };
    expect(json.conflicts_found).toBe(2);
    expect(json.memories_created).toBe(3);
  });
});

// ─── Options Types ──────────────────────────────────────────

describe("Options types", () => {
  test("AugmentOptions allows empty object", () => {
    const opts: AugmentOptions = {};
    expect(opts.limit).toBeUndefined();
    expect(opts.namespace).toBeUndefined();
  });

  test("SearchOptions accepts all filters", () => {
    const opts: SearchOptions = {
      limit: 10,
      namespace: "prod",
      nodeType: "Entity",
      timeRange: [1000, 2000],
    };
    expect(opts.timeRange).toEqual([1000, 2000]);
  });

  test("LearnOptions with metadata", () => {
    const opts: LearnOptions = {
      namespace: "test",
      metadata: { key: "value" },
    };
    expect(opts.metadata).toEqual({ key: "value" });
  });

  test("ListMemoriesOptions with all fields", () => {
    const opts: ListMemoriesOptions = {
      nodeType: "Event",
      limit: 50,
      offset: 100,
      namespace: "ns",
    };
    expect(opts.offset).toBe(100);
  });

  test("ListEntitiesOptions with all fields", () => {
    const opts: ListEntitiesOptions = {
      limit: 25,
      offset: 0,
      namespace: "default",
    };
    expect(opts.limit).toBe(25);
  });

  test("AddMemoryOptions with metadata", () => {
    const opts: AddMemoryOptions = {
      namespace: "dev",
      metadata: { source: "test" },
    };
    expect(opts.metadata!.source).toBe("test");
  });

  test("EntityOptions namespace only", () => {
    const opts: EntityOptions = { namespace: "prod" };
    expect(opts.namespace).toBe("prod");
  });
});

// ─── ClientConfig & RetryConfig ─────────────────────────────

describe("ClientConfig", () => {
  test("all fields optional", () => {
    const config: ClientConfig = {};
    expect(config.timeoutMs).toBeUndefined();
    expect(config.retry).toBeUndefined();
    expect(config.defaultNamespace).toBeUndefined();
  });

  test("partial retry config", () => {
    const config: ClientConfig = {
      retry: { maxRetries: 5 },
    };
    expect(config.retry!.maxRetries).toBe(5);
    expect(config.retry!.baseDelayMs).toBeUndefined();
  });

  test("full config", () => {
    const config: ClientConfig = {
      timeoutMs: 10000,
      retry: { maxRetries: 2, baseDelayMs: 50, maxDelayMs: 1000 },
      defaultNamespace: "production",
    };
    expect(config.timeoutMs).toBe(10000);
    expect(config.retry!.maxDelayMs).toBe(1000);
    expect(config.defaultNamespace).toBe("production");
  });
});
