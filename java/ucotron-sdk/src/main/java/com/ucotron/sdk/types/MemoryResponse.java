package com.ucotron.sdk.types;

import java.util.Map;

/**
 * A single memory node response.
 */
public class MemoryResponse {
    private long id;
    private String content;
    private String node_type;
    private long timestamp;
    private Map<String, Object> metadata;

    public long getId() { return id; }
    public String getContent() { return content; }
    public String getNodeType() { return node_type; }
    public long getTimestamp() { return timestamp; }
    public Map<String, Object> getMetadata() { return metadata; }
}
