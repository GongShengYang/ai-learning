package com.gsy.ai.mcp.client.dto;

import lombok.Data;

/**
 * MCP Client 演示请求。
 */
@Data
public class McpClientDemoRequest {
    /** 用户问题。 */
    private String question;
}