package com.gsy.ai.mcp.client.controller;

import com.gsy.ai.common.Result;
import com.gsy.ai.mcp.client.dto.McpClientDemoRequest;
import com.gsy.ai.mcp.client.service.McpClientDemoService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示 ChatClient 通过 MCP Client 调用知识库工具。
 */
@RestController
@RequestMapping("/ai/mcp-demo")
public class McpClientDemoController {
    @Resource
    private McpClientDemoService mcpClientDemoService;

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody McpClientDemoRequest request) {
        return Result.success(mcpClientDemoService.chat(request == null ? null : request.getQuestion()));
    }
}