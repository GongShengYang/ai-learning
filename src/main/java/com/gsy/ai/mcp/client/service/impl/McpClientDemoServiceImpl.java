package com.gsy.ai.mcp.client.service.impl;

import com.gsy.ai.common.BusinessException;
import com.gsy.ai.mcp.client.service.McpClientDemoService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 手动构建 MCP Client 的原因：
 * Spring AI 自动配置会在启动阶段立即解析所有 ToolCallbackProvider，
 * 而“同一应用既当 Server 又当 Client”时，启动阶段 Server 端点还没就绪、
 * 客户端也还没初始化，会导致启动失败（Client must be initialized before listing tools）。
 *
 * 因此这里改为第一次调用接口时才连接 Server，此时应用本身已经启动完成，
 * 自己的 MCP 端点一定可用。
 */
@Service
public class McpClientDemoServiceImpl implements McpClientDemoService {

    /** MCP Server 暴露的 SSE 建连地址，包含应用的 /api 上下文路径。 */
    private static final String MCP_SSE_ENDPOINT = "/api/sse";

    /** 本应用自己 MCP Server 的基础地址，由 gsy.mcp.server-url 配置。 */
    private final String mcpServerBaseUrl;

    private final ChatClient chatClient;

    /** 懒加载：第一次调用时才创建并连接 MCP Server，volatile 保证多线程可见性。 */
    private volatile ToolCallbackProvider mcpToolCallbacks;

    public McpClientDemoServiceImpl(ChatClient chatClient,
                                    @Value("${gsy.mcp.server-url}") String mcpServerBaseUrl) {
        this.chatClient = chatClient;
        this.mcpServerBaseUrl = mcpServerBaseUrl;
    }

    @Override
    public String chat(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }

        // 首次调用时才连接并发现工具。
        ToolCallbackProvider mcpTools = getOrCreateMcpTools();

        String answer = chatClient.prompt()
                .system("""
                        你是企业智能助手。
                        当用户询问知识库正文、制度、资料时，调用 searchKnowledge 工具。
                        当用户询问知识库有多少文档、知识库规模时，调用 queryDocumentCount 工具。
                        不允许编造知识库数据。
                        """)
                .user(question)
                // MCP 工具是 ToolCallbackProvider，必须用 toolCallbacks() 注册。
                .toolCallbacks(mcpTools)
                .call()
                .content();

        if (!StringUtils.hasText(answer)) {
            throw new BusinessException("模型未返回有效内容");
        }
        return answer;
    }

    /**
     * 懒加载创建 MCP 客户端：
     * 1. 用 SSE 传输连接 Server 的 /api/sse 端点；
     * 2. 完成 initialize() 握手；
     * 3. 把客户端包装成 ToolCallbackProvider，供 ChatClient 发现工具。
     */
    private ToolCallbackProvider getOrCreateMcpTools() {
        if (mcpToolCallbacks == null) {
            synchronized (this) {
                if (mcpToolCallbacks == null) {
                    HttpClientSseClientTransport transport =
                            new HttpClientSseClientTransport.Builder(mcpServerBaseUrl)
                                    .sseEndpoint(MCP_SSE_ENDPOINT)
                                    .build();
                    McpSyncClient client = McpClient.sync(transport).build();
                    client.initialize();
                    mcpToolCallbacks = new SyncMcpToolCallbackProvider(client);
                }
            }
        }
        return mcpToolCallbacks;
    }
}