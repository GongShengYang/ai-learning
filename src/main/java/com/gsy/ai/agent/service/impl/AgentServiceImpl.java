package com.gsy.ai.agent.service.impl;

import com.gsy.ai.agent.dto.AgentChatResponse;
import com.gsy.ai.agent.service.AgentService;
import com.gsy.ai.agent.service.ChatMessageService;
import com.gsy.ai.agent.service.ConversationService;
import com.gsy.ai.agent.tool.DocumentQueryTool;
import com.gsy.ai.agent.tool.RagSearchTool;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;


@Service
public class AgentServiceImpl implements AgentService {

    /**
     * 取最近20条历史消息。
     *
     * 指20条Message，不是20轮。
     * 一轮通常包含USER和ASSISTANT两条。
     */
    private static final int MAX_HISTORY_MESSAGES = 20;

    @Resource
    private ChatClient chatClient;

    @Resource
    private RagSearchTool ragSearchTool;

    @Resource
    private DocumentQueryTool documentQueryTool;

    @Resource
    private ConversationService conversationService;

    @Resource
    private ChatMessageService chatMessageService;

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
                                                    
                        必须调用 documentQueryTool 工具获取真实数据。
                                                    
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


    @Override
    public AgentChatResponse chat(Long userId, String conversationId, String question) {
        validateRequest(userId, question);

        /*
         * conversationId为空：
         * 创建新会话。
         *
         * conversationId不为空：
         * 校验该会话属于当前用户。
         */
        String actualConversationId = conversationService.getOrCreateConversation(userId, conversationId, question);

        /*
         * 必须先读取历史，再保存当前问题。
         *
         * 否则当前问题会同时出现在：
         * 1. historyMessages
         * 2. .user(question)
         *
         * 导致重复发送。
         */

        List<Message> historyMessages = chatMessageService.listRecentMessages(actualConversationId, MAX_HISTORY_MESSAGES);

        /*
         * 先保存用户本轮输入。
         *
         * 即使模型调用失败，也可以保留用户真实提交过的内容。
         * 是否保留失败消息可根据业务要求调整。
         */
        chatMessageService.saveUserMessage(userId, actualConversationId, question);

        String answer;

        try {
            ChatClient.ChatClientRequestSpec requestSpec =
                    chatClient
                            .prompt()
                            .system("""
                                    你是一个企业知识库智能助手。

                                    你可以根据当前问题和历史对话理解用户意图。

                                    当用户询问知识库正文、技术资料、问题记录、
                                    踩坑记录或解决方案时，调用知识库检索工具。

                                    当用户询问知识库中有多少文档时，
                                    调用文档统计工具。

                                    涉及知识库真实数据时必须调用对应工具，
                                    不允许猜测或编造。

                                    历史对话仅用于理解当前问题。
                                    如果历史内容与当前问题无关，不要强行关联。
                                    """);

            /*
             * 只有存在历史时才添加。
             */
            if (historyMessages != null && !historyMessages.isEmpty()) {
                requestSpec.messages(historyMessages);
            }

            answer = requestSpec
                    .user(question)
                    .tools(
                            ragSearchTool,
                            documentQueryTool
                    )
                    .call()
                    .content();

        } catch (Exception e) {
            /*
             * 不要保存虚假的助手回答。
             * 继续把异常交给全局异常处理。
             */
            throw new IllegalStateException(
                    "Agent调用失败：" + e.getMessage(),
                    e);
        }

        if (!StringUtils.hasText(answer)) {
            throw new IllegalStateException(
                    "模型未返回有效内容"
            );
        }

        chatMessageService.saveAssistantMessage(userId, actualConversationId, answer);

        conversationService.updateConversationTime(actualConversationId);

        return new AgentChatResponse(actualConversationId, answer);
    }

    private void validateRequest(Long userId, String question) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId不能为空");
        }

        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question不能为空");
        }

        if (question.length() > 5000) {
            throw new IllegalArgumentException(
                    "question长度不能超过5000字符"
            );
        }
    }
}
