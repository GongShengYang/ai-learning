package com.gsy.ai.agent.service;

import com.gsy.ai.agent.tool.RagSearchTool;
import org.springframework.ai.chat.client.ChatClient;

public interface AgentService {

    String chat(String question);

}
