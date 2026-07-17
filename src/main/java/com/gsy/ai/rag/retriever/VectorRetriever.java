package com.gsy.ai.rag.retriever;


import com.gsy.ai.common.BusinessException;
import com.gsy.ai.rag.embedding.EmbeddingService;
import com.gsy.ai.rag.store.ChunkWithVector;
import com.gsy.ai.rag.store.VectorStore;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;


@Component
public class VectorRetriever implements Retriever {


    private final EmbeddingService embeddingService;

    private final VectorStore vectorStore;


    public VectorRetriever(EmbeddingService embeddingService, VectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }



    @Override
    public List<ChunkWithVector> retrieve(RetrieveRequest request) throws IOException {
        if(request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            throw new BusinessException(
                    "问题不能为空"
            );
        }

        // 问题向量化
        // 作用：
        // 把用户问题转换为float[]向量
        float[] queryVector = embeddingService.embed(request.getQuestion());

        if(queryVector == null || queryVector.length == 0){
            return List.of();
        }

        return vectorStore.search(queryVector, request.getDocumentId(), request.getTopK());

    }

}