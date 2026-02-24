package com.ucotron.sdk.types;

/**
 * Response from the health check endpoint.
 */
public class HealthResponse {
    private String status;
    private String version;
    private String instance_id;
    private String instance_role;
    private String storage_mode;
    private String vector_backend;
    private String graph_backend;
    private ModelStatus models;

    public String getStatus() { return status; }
    public String getVersion() { return version; }
    public String getInstanceId() { return instance_id; }
    public String getInstanceRole() { return instance_role; }
    public String getStorageMode() { return storage_mode; }
    public String getVectorBackend() { return vector_backend; }
    public String getGraphBackend() { return graph_backend; }
    public ModelStatus getModels() { return models; }
}
