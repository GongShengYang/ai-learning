package com.gsy.ai.agent.dto;

import lombok.Data;


@Data
public class AgentRequest {


    /**
     * 用户任务
     */
    private String question;


    /**
     * 指定文档
     *
     * 可为空：
     * 空 = 全知识库
     * 有值 = 指定文档
     */
    private Long documentId;
}