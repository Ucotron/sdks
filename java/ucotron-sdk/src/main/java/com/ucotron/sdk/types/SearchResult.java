package com.ucotron.sdk.types;

import java.util.List;

/**
 * Response from the search endpoint.
 */
public class SearchResult {
    private List<SearchResultItem> results;
    private int total;
    private String query;

    public List<SearchResultItem> getResults() { return results; }
    public int getTotal() { return total; }
    public String getQuery() { return query; }
}
