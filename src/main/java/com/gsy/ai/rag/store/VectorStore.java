package com.gsy.ai.rag.store;

import com.gsy.ai.entity.DocumentChunkDO;
import io.milvus.v2.service.vector.request.DeleteReq;

import java.util.List;

public interface VectorStore {

    /**
     * 添加文档切片（包含向量）
     */
    void add(ChunkWithVector chunk);


    /**
     * 批量添加文档切片向量。
     *
     * 默认逐条调用 add；
     * MilvusVectorStore 会覆盖为真正的批量写入。
     */
    default void addBatch(List<ChunkWithVector> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        for (ChunkWithVector chunk : chunks) {
            add(chunk);
        }
    }

    /**
     * 向量检索 TopK
     */
    List<ChunkWithVector> search(float[] queryVector, Long documentId, int topK);

    /**
     * 清空
     */
    void clear();

    void deleteByChunkId(Long chunkId);
}