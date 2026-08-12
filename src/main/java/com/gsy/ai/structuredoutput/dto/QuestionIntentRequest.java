package com.gsy.ai.structuredoutput.dto;

import lombok.Data;

/**
 * 问题分类请求。
 */
@Data
public class QuestionIntentRequest {
    /** 用户需要分类的原始问题。 */
    private String question;
}
