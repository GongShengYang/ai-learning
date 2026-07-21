package com.gsy.ai.document.rag.retriever;

import lombok.Data;


@Data
public class RetrieveRequest {


    /**
     * 用户问题
     *
     * 作用：
     * 用于生成查询向量
     */
    private String question;


    /**
     * 文档ID
     *
     * 作用：
     * 控制检索范围
     *
     * null:
     * 全知识库搜索
     *
     * 有值:
     * 指定文档搜索
     */
    private Long documentId;


    /**
     * 返回数量
     *
     * 作用：
     * 返回最相关的几个Chunk
     */
    private Integer topK = 3;

}