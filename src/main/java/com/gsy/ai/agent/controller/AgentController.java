package com.gsy.ai.agent.controller;


import com.gsy.ai.agent.dto.AgentChatRequest;
import com.gsy.ai.agent.dto.AgentChatResponse;
import com.gsy.ai.agent.dto.AgentRequest;
import com.gsy.ai.agent.service.AgentService;
import com.gsy.ai.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/ai/agent")
public class AgentController {
    @Resource
    private AgentService agentService;

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody AgentRequest request) {
        String answer = agentService.chat(request.getQuestion());
        return Result.success(answer);
    }

    @PostMapping("/agentChat")
    public Result<AgentChatResponse> agentChat(@RequestBody AgentChatRequest request) {
        AgentChatResponse response = agentService.chat(request.getUserId(), request.getConversationId(), request.getQuestion());
        return Result.success(response);
    }
}