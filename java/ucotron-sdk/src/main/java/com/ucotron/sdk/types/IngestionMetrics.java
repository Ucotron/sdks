package com.ucotron.sdk.types;

/**
 * Metrics from the ingestion pipeline.
 */
public class IngestionMetrics {
    private int chunks_processed;
    private int entities_extracted;
    private int relations_extracted;
    private int contradictions_detected;
    private long total_us;

    public int getChunksProcessed() { return chunks_processed; }
    public int getEntitiesExtracted() { return entities_extracted; }
    public int getRelationsExtracted() { return relations_extracted; }
    public int getContradictionsDetected() { return contradictions_detected; }
    public long getTotalUs() { return total_us; }
}
