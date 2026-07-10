package com.gsy.ai.dto;


import lombok.Data;


@Data
public class RagQuestionRequest {


    /**
     * 用户问题
     */
    private String question;


    /**
     * 指定文档
     *
     * 可为空
     */
    private Long documentId;


    /**
     * 返回数量
     */
    private Integer topK = 3;

}