package com.ucotron.sdk.types;

/**
 * Status of loaded ML models.
 */
public class ModelStatus {
    private boolean embedder_loaded;
    private String embedding_model;
    private boolean ner_loaded;
    private boolean relation_extractor_loaded;
    private boolean transcriber_loaded;

    public boolean isEmbedderLoaded() { return embedder_loaded; }
    public String getEmbeddingModel() { return embedding_model; }
    public boolean isNerLoaded() { return ner_loaded; }
    public boolean isRelationExtractorLoaded() { return relation_extractor_loaded; }
    public boolean isTranscriberLoaded() { return transcriber_loaded; }
}
