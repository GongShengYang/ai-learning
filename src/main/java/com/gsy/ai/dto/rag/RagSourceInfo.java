package com.gsy.ai.dto.rag;

import lombok.Data;


@Data
public class RagSourceInfo {


    /**
     * Chunk主键
     */
    private Long chunkId;


    /**
     * 文档ID
     */
    private Long documentId;


    /**
     * Chunk序号
     */
    private Integer chunkIndex;


    /**
     * Milvus相似度
     */
    private Float score;


    /**
     * 原始文本
     */
    private String content;

    public RagSourceInfo(){

    }

    public RagSourceInfo(
            Long chunkId,
            Long documentId,
            Integer chunkIndex,
            Float score,
            String content
    ) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.score = score;
        this.content = content;
    }
}