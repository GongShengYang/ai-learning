package com.gsy.ai.structuredoutput.service;

import com.gsy.ai.structuredoutput.dto.QuestionIntentResult;

/**
 * Structured Output 示例服务。
 */
public interface StructuredOutputService {
    QuestionIntentResult classifyQuestion(String question);
}
