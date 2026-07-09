package com.gsy.ai.rag.store;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkWithVector {
    private String content;
    private float[] vector;
    private Long documentId;
    private Integer chunkIndex;
    /**
     * 相似度分数
     */
    //private Float score;
}