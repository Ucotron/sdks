package com.ucotron.sdk.types;

import java.util.List;

/**
 * Result of creating a new memory.
 */
public class CreateMemoryResult {
    private List<Long> chunk_node_ids;
    private List<Long> entity_node_ids;
    private int edges_created;
    private IngestionMetrics metrics;

    public List<Long> getChunkNodeIds() { return chunk_node_ids; }
    public List<Long> getEntityNodeIds() { return entity_node_ids; }
    public int getEdgesCreated() { return edges_created; }
    public IngestionMetrics getMetrics() { return metrics; }
}
