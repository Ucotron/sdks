package com.ucotron.sdk.types;

/**
 * Response from the metrics endpoint.
 */
public class MetricsResponse {
    private String instance_id;
    private long total_requests;
    private long total_ingestions;
    private long total_searches;
    private long uptime_secs;

    public String getInstanceId() { return instance_id; }
    public long getTotalRequests() { return total_requests; }
    public long getTotalIngestions() { return total_ingestions; }
    public long getTotalSearches() { return total_searches; }
    public long getUptimeSecs() { return uptime_secs; }
}
