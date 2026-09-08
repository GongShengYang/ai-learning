package com.gsy.ai.mcp.config;

import com.gsy.ai.mcp.tool.McpKnowledgeTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把知识库工具注册成 MCP Server 的工具。
 * 任意 MCP Client 都可以发现并调用，不需要为每个客户端硬编码。
 */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(McpKnowledgeTools mcpKnowledgeTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpKnowledgeTools)
                .build();
    }
}