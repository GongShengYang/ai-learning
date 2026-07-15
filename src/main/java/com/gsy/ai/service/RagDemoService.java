package com.gsy.ai.service;

import com.gsy.ai.dto.RagQuestionRequest;

import java.io.IOException;


public interface RagDemoService {

    String getRagDemo(RagQuestionRequest request) throws IOException;
}
