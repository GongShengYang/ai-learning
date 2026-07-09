package com.gsy.ai.rag.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class MemoryVectorStore implements VectorStore {

    private final List<ChunkWithVector> storage = new ArrayList<>();

    @Override
    public void add(ChunkWithVector chunk) {
        storage.add(chunk);
    }

    @Override
    public List<ChunkWithVector> search(float[] queryVector, int topK) {
        List<ScoreItem> scores = new ArrayList<>();
        for (int i = 0; i < storage.size(); i++) {
            ChunkWithVector c = storage.get(i);
            float sim = cosineSimilarity(queryVector, c.getVector());
            scores.add(new ScoreItem(i, sim));
        }
        scores.sort((a, b) -> Float.compare(b.sim, a.sim));

        List<ChunkWithVector> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scores.size()); i++) {
            result.add(storage.get(scores.get(i).idx));
        }
        return result;
    }

    @Override
    public void clear() {
        storage.clear();
    }

    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) return 0;
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static class ScoreItem {
        int idx;
        float sim;
        ScoreItem(int idx, float sim) {
            this.idx = idx;
            this.sim = sim;
        }
    }
}