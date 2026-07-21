package com.gsy.ai.document.rag.store;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkWithVector {

    /**
     * MySQL document_chunk.id。
     *
     * 作用：
     * 同时作为 Milvus Collection 的 chunk_id 主键。
     */
    private Long chunkId;

    /**
     * 文档切片内容。
     *
     * 作用：
     * 从 MySQL 查询后提供给 LLM 作为上下文。
     */
    private String content;

    /**
     * 文档向量。
     *
     * 作用：
     * 写入 Milvus，或者用于调试。
     */
    private float[] vector;

    /**
     * 来源文档 ID。
     *
     * 作用：
     * 支持按 documentId 限制检索范围。
     */
    private Long documentId;

    /**
     * 当前 Chunk 在文档中的编号。
     */
    private Integer chunkIndex;

    /**
     * 向量相似度分数。
     *
     * COSINE 模式下越接近 1，相关性通常越高。
     */
    private Float score;
}