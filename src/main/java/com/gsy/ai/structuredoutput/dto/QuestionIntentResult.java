package com.gsy.ai.structuredoutput.dto;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.gsy.ai.structuredoutput.enums.QuestionIntent;
import lombok.Data;

/**
 * 模型返回的问题分类结果。
 */
@Data
@JsonClassDescription("用户问题的结构化分类结果")
@JsonPropertyOrder({"intent", "needTool", "confidence", "reason"})
public class QuestionIntentResult {
    /** intent：意图，即用户真正想做什么。 */
    @JsonPropertyDescription("用户问题的业务意图，只能使用QuestionIntent中定义的枚举值")
    private QuestionIntent intent;

    /** needTool：是否需要调用外部工具才能完成用户请求。 */
    @JsonPropertyDescription("是否需要调用外部工具；知识库搜索、文档统计和复合任务为true，其余类型为false")
    private Boolean needTool;

    /** confidence：置信度，表示模型对本次分类有多确定，范围为0到1。 */
    @JsonPropertyDescription("分类置信度，取值范围为0到1，数值越大表示越确定")
    private Double confidence;

    /** reason：分类理由，用中文简要说明为什么判断为该意图。 */
    @JsonPropertyDescription("分类理由，必须使用中文且不能超过100个字符")
    private String reason;
}
