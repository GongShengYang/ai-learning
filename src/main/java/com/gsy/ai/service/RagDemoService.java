package com.gsy.ai.service;

import com.gsy.ai.dto.RagQuestionRequest;
import com.gsy.ai.dto.rag.RagAnswerResponse;

import java.io.IOException;


public interface RagDemoService {

    RagAnswerResponse getRagDemo(RagQuestionRequest request) throws IOException;
}
