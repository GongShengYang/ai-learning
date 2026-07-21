package com.gsy.ai.document.dto.rag;

import lombok.Data;

import java.util.List;

@Data
public class RagAnswerResponse {


    /**
     * 大模型最终回答
     */
    private String answer;


    /**
     * RAG检索来源
     */
    private List<RagSourceInfo> sources;

    public RagAnswerResponse() {

    }


    public RagAnswerResponse(
            String answer,
            List<RagSourceInfo> sources
    ) {
        this.answer = answer;
        this.sources = sources;
    }
}