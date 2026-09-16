package com.gsy.ai.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 一条可返回给前端的知识库来源。
 * 来源信息由Java根据Retriever结果生成，不依赖模型自己编造。
 */
@Data
@AllArgsConstructor
public class WorkflowSource {
    /** 本次回答内的来源编号，例如S1；模型在答案中使用[S1]引用它。 */
    private String sourceId;

    /** 来源属于哪个Planner步骤；普通单问题没有计划步骤，因此为null。 */
    private Integer planStepNumber;

    /** 来源文档在MySQL中的ID，可用于后续跳转文档详情。 */
    private Long documentId;

    /** 该切片在原文档中的顺序编号。 */
    private Integer chunkIndex;

    /** 向量检索相似度；当前COSINE模式下通常越接近1越相关。 */
    private Float similarity;

    /** 切片内容摘要，只返回有限长度，避免接口暴露整段内部资料。 */
    private String contentPreview;
}
