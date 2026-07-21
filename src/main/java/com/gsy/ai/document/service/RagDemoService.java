package com.gsy.ai.document.service;

import com.gsy.ai.document.dto.RagQuestionRequest;
import com.gsy.ai.document.dto.rag.RagAnswerResponse;

import java.io.IOException;


public interface RagDemoService {

    RagAnswerResponse getRagDemo(RagQuestionRequest request) throws IOException;
}
