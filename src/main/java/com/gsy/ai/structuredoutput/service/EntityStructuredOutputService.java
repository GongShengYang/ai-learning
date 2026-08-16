package com.gsy.ai.structuredoutput.service;

import com.gsy.ai.structuredoutput.dto.QuestionIntentResult;

/**
 * 使用Spring AI entity简写实现问题分类。
 */
public interface EntityStructuredOutputService {
    QuestionIntentResult classifyQuestion(String question);
}
