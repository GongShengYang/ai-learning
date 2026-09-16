package com.gsy.ai.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 最终回答的引用编号校验结果。
 * 这里只校验[S1]是否来自本次真实检索，不负责判断引用内容在语义上是否真的支持某句话。
 */
@Data
@AllArgsConstructor
public class WorkflowCitationValidationResult {
    /** 当前回答是否必须包含引用；没有检索到资料时为false。 */
    private boolean required;

    /** 引用格式和编号是否通过Java校验。 */
    private boolean passed;

    /** 按回答中首次出现的顺序保存实际引用编号，例如S1、S2。 */
    private List<String> citedSourceIds;

    /** 回答中出现、但本次Retriever结果里不存在的编号。 */
    private List<String> invalidSourceIds;

    /** 供接口展示和下一次模型纠正使用的校验说明。 */
    private String message;
}
