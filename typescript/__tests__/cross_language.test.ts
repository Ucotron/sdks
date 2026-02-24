/**
 * Cross-language SDK integration tests for TypeScript.
 *
 * These tests run against a live Ucotron server. Set UCOTRON_TEST_SERVER_URL
 * to the server URL (e.g., "http://127.0.0.1:8420"). If the env var is not
 * set, tests are skipped.
 */

import { Ucotron } from "../src/client";

const SERVER_URL = process.env.UCOTRON_TEST_SERVER_URL;

const describeIfServer = SERVER_URL ? describe : describe.skip;

describeIfServer("Cross-language integration tests (TypeScript)", () => {
  let client: Ucotron;

  beforeAll(() => {
    client = new Ucotron(SERVER_URL!);
  });

  test("health check returns ok status", async () => {
    const health = await client.health();
    expect(health.status).toBe("ok");
    expect(health.version).toBeTruthy();
    expect(health.instance_id).toBeTruthy();
    expect(health.instance_role).toBeTruthy();
    expect(health.storage_mode).toBeTruthy();
    expect(health.models).toBeDefined();
  });

  test("metrics returns server stats", async () => {
    const metrics = await client.metrics();
    expect(metrics.instance_id).toBeTruthy();
    expect(typeof metrics.total_requests).toBe("number");
    expect(typeof metrics.uptime_secs).toBe("number");
    expect(metrics.uptime_secs).toBeGreaterThanOrEqual(0);
  });

  test("add_memory creates a memory and returns IDs", async () => {
    const ns = `ts_test_${process.pid}`;
    const result = await client.addMemory(
      "TypeScript SDK test: The Eiffel Tower is in Paris, France.",
      { namespace: ns }
    );

    // Response should have the expected shape
    expect(result).toHaveProperty("chunk_node_ids");
    expect(result).toHaveProperty("entity_node_ids");
    expect(result).toHaveProperty("edges_created");
    expect(result).toHaveProperty("metrics");
    expect(Array.isArray(result.chunk_node_ids)).toBe(true);
  });

  test("search returns results with scoring breakdown", async () => {
    const ns = `ts_search_${process.pid}`;
    const result = await client.search("Eiffel Tower in Paris", {
      limit: 5,
      namespace: ns,
    });

    expect(result).toHaveProperty("results");
    expect(result).toHaveProperty("total");
    expect(result).toHaveProperty("query");
    expect(Array.isArray(result.results)).toBe(true);
    expect(result.query).toBeTruthy();
  });

  test("augment returns context for LLM injection", async () => {
    const ns = `ts_aug_${process.pid}`;
    const result = await client.augment(
      "What do you know about artificial intelligence?",
      { namespace: ns }
    );

    expect(result).toHaveProperty("memories");
    expect(result).toHaveProperty("entities");
    expect(result).toHaveProperty("context_text");
    expect(Array.isArray(result.memories)).toBe(true);
    expect(Array.isArray(result.entities)).toBe(true);
    expect(typeof result.context_text).toBe("string");
  });

  test("learn extracts memories from agent output", async () => {
    const ns = `ts_learn_${process.pid}`;
    const result = await client.learn(
      "The user mentioned they prefer dark mode and use VSCode.",
      { namespace: ns }
    );

    expect(result).toHaveProperty("memories_created");
    expect(result).toHaveProperty("entities_found");
    expect(result).toHaveProperty("conflicts_found");
    expect(typeof result.memories_created).toBe("number");
  });

  test("list_memories returns an array", async () => {
    const result = await client.listMemories();
    expect(Array.isArray(result)).toBe(true);
  });

  test("list_entities returns an array", async () => {
    const result = await client.listEntities();
    expect(Array.isArray(result)).toBe(true);
  });
});
