package com.gsy.ai.service.impl;

import com.gsy.ai.common.BusinessException;
import com.gsy.ai.dto.RagQuestionRequest;
import com.gsy.ai.rag.client.AiHttpClient;
import com.gsy.ai.rag.embedding.EmbeddingService;
import com.gsy.ai.rag.prompt.PromptTemplateService;
import com.gsy.ai.rag.retriever.RetrieveRequest;
import com.gsy.ai.rag.retriever.Retriever;
import com.gsy.ai.rag.store.ChunkWithVector;
import com.gsy.ai.rag.store.VectorStore;
import com.gsy.ai.service.RagDemoService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class RagDemoServiceImpl implements RagDemoService {

    private final Retriever retriever;

    @Resource
    private PromptTemplateService promptTemplateService;

    @Resource(name = "llmAiHttpClient")
    private AiHttpClient aiHttpClient;

    @Value("${gsy.rag.llm.url}")
    private String llmUrl;

    @Value("${gsy.rag.llm.model}")
    private String llmModel;

    @Value("${gsy.rag.embedding.api-key}")
    private String apiKey;

    @Value("${gsy.rag.llm.max-context-length}")
    private int maxContextLength;

    public RagDemoServiceImpl(Retriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public String getRagDemo(RagQuestionRequest request) throws IOException {
        String question = request.getQuestion();
        // 1. 参数校验
        if (question == null || question.trim().isEmpty()) {
            throw new BusinessException("问题不能为空");
        }
        log.info("RAG问答开始，问题: {}", question);

        // 2. 检索相关文档片段
        // 作用：根据用户问题，从知识库里找到最相关的 Top3 片段
        RetrieveRequest retrieveRequest = new RetrieveRequest();
        retrieveRequest.setQuestion(request.getQuestion());
        retrieveRequest.setDocumentId(request.getDocumentId());
        retrieveRequest.setTopK(request.getTopK());
        List<ChunkWithVector> chunks = retriever.retrieve(retrieveRequest);

        if (chunks.isEmpty()) {
            log.info("未找到相关文档片段，问题: {}", question);
            return "未找到相关文档片段";
        }

        // 4. 构建上下文
        String context = buildContext(chunks);
        log.info("检索到 {} 个片段，上下文长度: {}", chunks.size(), context.length());

        // 5. 调用 LLM
        String answer = callLLM(question, context);
        log.info("RAG回答生成成功，长度: {}", answer.length());
        return answer;
    }

    private String buildContext(List<ChunkWithVector> chunks) {
        int maxContextLength = this.maxContextLength;

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i).getContent();

            String piece = "【片段" + (i + 1) + "】\n"
                    + content
                    + "\n\n";

            if (sb.length() + piece.length() > maxContextLength) {
                log.warn("上下文超过最大长度限制，已截断，当前长度: {}", sb.length());
                break;
            }

            sb.append(piece);
        }

        return sb.toString();
    }

    private String callLLM(String question, String context) throws IOException {
        String systemPrompt = promptTemplateService.getSystemPrompt();
        String userPrompt = promptTemplateService.getUserPrompt(question , context);
        JsonObject body = new JsonObject();
        body.addProperty("model", llmModel);
        JsonArray messages = new JsonArray();
        messages.add(createMessage("system", systemPrompt));
        messages.add(createMessage("user", userPrompt));
        body.add("messages", messages);
        body.addProperty("stream", false);

        String resp = aiHttpClient.postJson(llmUrl, apiKey, body.toString());

        JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();

        return obj.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

    }

    private JsonObject createMessage(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        return msg;
    }
}