package com.ucotron.sdk.types;

import java.util.List;

/**
 * Response from the augment endpoint.
 */
public class AugmentResult {
    private List<SearchResultItem> memories;
    private List<EntityResponse> entities;
    private String context_text;

    public List<SearchResultItem> getMemories() { return memories; }
    public List<EntityResponse> getEntities() { return entities; }
    public String getContextText() { return context_text; }
}
