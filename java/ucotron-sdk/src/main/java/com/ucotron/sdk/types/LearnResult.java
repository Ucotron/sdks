package com.ucotron.sdk.types;

/**
 * Response from the learn endpoint.
 */
public class LearnResult {
    private int memories_created;
    private int entities_found;
    private int conflicts_found;

    public int getMemoriesCreated() { return memories_created; }
    public int getEntitiesFound() { return entities_found; }
    public int getConflictsFound() { return conflicts_found; }
}
