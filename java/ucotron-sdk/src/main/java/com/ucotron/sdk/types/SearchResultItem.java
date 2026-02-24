package com.ucotron.sdk.types;

/**
 * A single search result with relevance scores.
 */
public class SearchResultItem {
    private long id;
    private String content;
    private String node_type;
    private float score;
    private float vector_sim;
    private float graph_centrality;
    private float recency;

    public long getId() { return id; }
    public String getContent() { return content; }
    public String getNodeType() { return node_type; }
    public float getScore() { return score; }
    public float getVectorSim() { return vector_sim; }
    public float getGraphCentrality() { return graph_centrality; }
    public float getRecency() { return recency; }
}
