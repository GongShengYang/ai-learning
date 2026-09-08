package com.gsy.ai.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gsy.ai.agent.tool.RagSearchTool;
import com.gsy.ai.document.entity.DocumentDO;
import com.gsy.ai.document.mapper.DocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 通过 MCP 协议暴露给任意 AI 客户端的企业知识库工具。
 * 与 Agent 内部使用的工具分离：MCP 工具显式校验参数并以字符串返回错误。
 */
@Slf4j
@Component
public class McpKnowledgeTools {

    private final RagSearchTool ragSearchTool;
    private final DocumentMapper documentMapper;

    public McpKnowledgeTools(RagSearchTool ragSearchTool, DocumentMapper documentMapper) {
        this.ragSearchTool = ragSearchTool;
        this.documentMapper = documentMapper;
    }

    @Tool(description = "查询企业知识库正文。当用户询问制度、文档内容、业务规则、技术资料时调用。")
    public String searchKnowledge(
            @ToolParam(description = "用户要查询知识库的问题") String question) {
        log.info("MCP工具被调用: searchKnowledge, question={}", question);
        if (!StringUtils.hasText(question)) {
            return "参数question不能为空";
        }
        if (question.length() > 500) {
            return "参数question长度不能超过500个字符";
        }
        // 复用 Agent 已有的检索能力，MCP 侧只负责协议暴露和参数校验。
        return ragSearchTool.searchKnowledge(question);
    }

    @Tool(description = "查询知识库中已有文档的数量。当用户询问有多少文档、记录了多少资料、知识库规模时调用。")
    public String queryDocumentCount() {
        log.info("MCP工具被调用: queryDocumentCount");
        Long count = documentMapper.selectCount(new QueryWrapper<DocumentDO>());
        log.info("MCP工具queryDocumentCount查询结果: {}", count);
        return "当前知识库共有" + count + "篇文档";
    }
}