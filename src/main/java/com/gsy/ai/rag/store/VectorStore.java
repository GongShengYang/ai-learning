package com.gsy.ai.rag.store;

import com.gsy.ai.entity.DocumentChunkDO;

import java.util.List;

public interface VectorStore {

    /**
     * 添加文档切片（包含向量）
     */
    void add(ChunkWithVector chunk);

    /**
     * 向量检索 TopK
     */
    List<ChunkWithVector> search(float[] queryVector, Long documentId, int topK);

    /**
     * 清空
     */
    void clear();
}