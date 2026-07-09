package com.gsy.ai.service.impl;

import com.gsy.ai.common.BusinessException;
import com.gsy.ai.rag.embedding.EmbeddingService;
import com.gsy.ai.rag.store.ChunkWithVector;
import com.gsy.ai.rag.store.VectorStore;
import com.gsy.ai.service.RagDemoService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class RagDemoServiceImpl implements RagDemoService {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final OkHttpClient httpClient = new OkHttpClient();

    @Value("${gsy.rag.llm.url}")
    private String llmUrl;

    @Value("${gsy.rag.llm.model}")
    private String llmModel;

    @Value("${gsy.rag.embedding.api-key}")
    private String apiKey;

    public RagDemoServiceImpl(EmbeddingService embeddingService, VectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    @Override
    public String getRagDemo(String question) throws IOException {
        // 1. 参数校验
        if (question == null || question.trim().isEmpty()) {
            throw new BusinessException("问题不能为空");
        }
        log.info("RAG问答开始，问题: {}", question);

        // 2. 问题向量化
        float[] queryVector = embeddingService.embed(question);
        if (queryVector == null || queryVector.length == 0) {
            log.warn("问题向量化失败: {}", question);
            return "无法获取问题向量，请稍后重试";
        }

        // 3. 向量检索 Top3
        List<ChunkWithVector> chunks = vectorStore.search(queryVector, 3);
        if (chunks.isEmpty()) {
            log.info("未找到相关文档片段，问题: {}", question);
            return "未找到相关文档片段";
        }

        // 4. 构建上下文
        String context = buildContext(chunks);
        log.debug("检索到 {} 个片段，上下文长度: {}", chunks.size(), context.length());

        // 5. 调用 LLM
        String answer = callLLM(question, context);
        log.info("RAG回答生成成功，长度: {}", answer.length());
        return answer;
    }

    private String buildContext(List<ChunkWithVector> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("【片段").append(i + 1).append("】\n")
                    .append(chunks.get(i).getContent())
                    .append("\n\n");
        }
        return sb.toString();
    }

    private String callLLM(String question, String context) throws IOException {
        String systemPrompt = "你是一个智能助手，必须根据提供的多个资料片段回答问题。如果资料中直接包含答案，请直接引用；如果资料不完整，你可以基于已有信息进行合理推断，但不要编造完全无关的内容。如果资料中完全没有涉及，就说“资料中没有提到”。";
        String userPrompt = "以下是几个相关的资料片段：\n" + context + "\n请回答以下问题：" + question;

        JsonObject body = new JsonObject();
        body.addProperty("model", llmModel);
        JsonArray messages = new JsonArray();
        messages.add(createMessage("system", systemPrompt));
        messages.add(createMessage("user", userPrompt));
        body.add("messages", messages);
        body.addProperty("stream", false);

        Request request = new Request.Builder()
                .url(llmUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String resp = response.body().string();
            if (!response.isSuccessful()) {
                log.error("LLM调用失败，状态码: {}, 响应: {}", response.code(), resp);
                throw new IOException("LLM调用失败: " + resp);
            }
            JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();
            return obj.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        }
    }

    private JsonObject createMessage(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        return msg;
    }
}