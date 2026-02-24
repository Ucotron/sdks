package com.ucotron.sdk.types;

import java.util.List;
import java.util.Map;

/**
 * Response representing an entity node with optional neighbors.
 */
public class EntityResponse {
    private long id;
    private String content;
    private String node_type;
    private long timestamp;
    private Map<String, Object> metadata;
    private List<NeighborResponse> neighbors;

    public long getId() { return id; }
    public String getContent() { return content; }
    public String getNodeType() { return node_type; }
    public long getTimestamp() { return timestamp; }
    public Map<String, Object> getMetadata() { return metadata; }
    public List<NeighborResponse> getNeighbors() { return neighbors; }
}
