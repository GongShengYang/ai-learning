package com.gsy.ai.rag.store;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkWithVector {


    /**
     * 文档切片内容
     *
     * 作用：
     * 给LLM提供上下文
     */
    private String content;


    /**
     * 文档向量
     *
     * 作用：
     * 当前阶段保存，后续切换Milvus可以不用重新Embedding
     */
    private float[] vector;


    /**
     * 来源文档ID
     *
     * 作用：
     * 知道这个答案来自哪个文件
     */
    private Long documentId;


    /**
     * 当前chunk编号
     *
     * 作用：
     * 知道这个片段在原文的位置
     */
    private Integer chunkIndex;


    /**
     * 相似度分数
     *
     * 作用：
     * 判断问题和这个片段的匹配程度
     *
     * 越接近1：
     * 越相关
     */
    private Float score;

}