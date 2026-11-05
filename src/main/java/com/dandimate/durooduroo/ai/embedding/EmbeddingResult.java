package com.dandimate.durooduroo.ai.embedding;

import java.util.List;

public record EmbeddingResult(String model, int dimensions, List<List<Double>> vectors) {
    public EmbeddingResult { vectors = vectors.stream().map(List::copyOf).toList(); }
}
