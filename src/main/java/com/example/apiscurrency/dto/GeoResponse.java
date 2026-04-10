package com.example.apiscurrency.dto;

import java.util.List;

public class GeoResponse {

    private List<Result> results;

    public List<Result> getResults() {
        return results;
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }
}