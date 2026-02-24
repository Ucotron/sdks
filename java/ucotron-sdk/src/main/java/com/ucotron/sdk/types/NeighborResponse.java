package com.ucotron.sdk.types;

/**
 * A neighbor node connected via an edge.
 */
public class NeighborResponse {
    private long node_id;
    private String content;
    private String edge_type;
    private float weight;

    public long getNodeId() { return node_id; }
    public String getContent() { return content; }
    public String getEdgeType() { return edge_type; }
    public float getWeight() { return weight; }
}
