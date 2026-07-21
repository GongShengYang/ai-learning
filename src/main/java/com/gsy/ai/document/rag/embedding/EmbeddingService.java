package com.gsy.ai.document.rag.embedding;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 单条文本转向量
     */
    public float[] embed(String text) {
        List<float[]> list = embedBatch(List.of(text));
        return list.isEmpty() ? new float[0] : list.get(0);
    }

    /**
     * 批量文本转向量
     */
    /**
     * 批量文本转向量
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        List<float[]> result = new ArrayList<>();
        int batchSize = 10;

        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            List<String> batchTexts = texts.subList(start, end);

            // Spring AI负责真正调用Embedding模型
            List<float[]> batchVectors = embeddingModel.embed(batchTexts);
            result.addAll(batchVectors);
        }

        return result;
    }

}