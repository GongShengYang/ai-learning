package com.gsy.ai.agent.service.impl;

import com.gsy.ai.agent.service.AgentService;
import com.gsy.ai.agent.tool.DocumentQueryTool;
import com.gsy.ai.agent.tool.RagSearchTool;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AgentServiceImpl implements AgentService {

    @Resource
    private ChatClient chatClient;

    @Resource
    private RagSearchTool ragSearchTool;

    @Resource
    private DocumentQueryTool documentQueryTool;


    @Override
    public String chat(String question) {

        return chatClient
                .prompt()
                /*
                 Agent核心提示词
                 */
                .system("""
                        你是企业智能助手。
                                                    
                        你不能猜测数据库中的数据。
                                                    
                        如果用户询问：
                        文档数量、
                        已有资料、
                        知识库规模，
                                                    
                        必须调用 documentCount 工具获取真实数据。
                                                    
                        如果需要文档内容，
                        调用 ragSearchTool。
                            """)
                .user(question)
                /*
                 注册工具
                 */
                .tools(ragSearchTool,documentQueryTool)
                .call()
                .content();
    }
}
