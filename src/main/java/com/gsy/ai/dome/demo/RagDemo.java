package com.gsy.ai.dome.demo;

import com.gsy.ai.dome.DO.DocumentChunk;
import com.google.gson.*;
import okhttp3.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class RagDemo {
    public static final String API_KEY = "sk-cad1be71e0274f8cae49c0e2cf124c36";
    public static final String EMBEDDING_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    public static final String LLM_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    public static final OkHttpClient client = new OkHttpClient();
    public static final Gson gson = new Gson();

//    public static List<float[]> vectors = new ArrayList<>();
//    public static List<String> chunks = new ArrayList<>();
    public static List<DocumentChunk> documentChunkList = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        // 1. 加载文档并切块（按段落）
        String text = loadDocument();
        System.out.println("文本长度: " + text.length());
        System.out.println("前50字符: " + text.substring(0, Math.min(50, text.length())));
        splitByParagraphs(text);
        System.out.println("文档切分完成，共 " + documentChunkList.size() + " 个片段");

        // 2. 为每个片段生成向量
        for (int i = 0; i < documentChunkList.size(); i++) {
            System.out.println("生成向量 " + (i+1) + "/" + documentChunkList.size());
            float[] vec = getEmbedding(documentChunkList.get(i).getContent());
            if (vec != null) {
                documentChunkList.get(i).setVector(vec);
            } else {
                documentChunkList.get(i).setVector(new float[0]);// 占位
            }
        }

        // 3. 交互问答
        Scanner scanner = new Scanner(System.in, "UTF-8");
        System.out.println("\nRAG 系统就绪，输入问题（输入 exit 退出）：");
        while (true) {
            System.out.print("问: ");
            String question = scanner.nextLine();
            if ("exit".equalsIgnoreCase(question.trim())) break;
            if (question.trim().isEmpty()) continue;

            float[] qVec = getEmbedding(question);
            if (qVec == null || qVec.length == 0) {
                System.out.println("答: 获取问题向量失败");
                continue;
            }

            // 检索 Top 3 相似片段
            List<Integer> topKIdx = findTopKSimilar(qVec, 3);
            if (topKIdx.isEmpty()) {
                System.out.println("答: 未找到相关文档片段");
                continue;
            }

            // 合并片段（加上索引，便于调试）
            StringBuilder contextBuilder = new StringBuilder();
            for (int idx : topKIdx) {
                String c = documentChunkList.get(idx).getContent();
                contextBuilder.append("【片段").append(idx).append("】\n").append(c).append("\n\n");
            }
            String context = contextBuilder.toString();

            // 打印调试信息
            System.out.println("检索到 " + topKIdx.size() + " 个片段，相似度分数：");
            for (int i = 0; i < topKIdx.size(); i++) {
                int idx = topKIdx.get(i);
                float sim = cosineSimilarity(qVec, documentChunkList.get(idx).getVector());
                System.out.printf("  片段%d (idx=%d): 相似度%.4f\n", i+1, idx, sim);
            }
            System.out.println("第一个片段预览: " + documentChunkList.get(topKIdx.get(0)).getContent().substring(0, Math.min(80, documentChunkList.get(topKIdx.get(0)).getContent().length())));

            String answer = askLLM(question, context);
            System.out.println("答: " + answer);
        }
        scanner.close();
    }

    public static String loadDocument() throws IOException {
        Path path = Paths.get("C:\\Users\\Administrator\\Desktop\\test.txt");
        if (Files.exists(path)) {
            byte[] bytes = Files.readAllBytes(path);
            // 移除UTF-8 BOM
            if (bytes.length >= 3 && bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF) {
                bytes = Arrays.copyOfRange(bytes, 3, bytes.length);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } else {
            String defaultDoc = "减肥期间应该多吃鸡胸肉、西兰花和全麦面包。每天喝2升水有助于代谢。每餐保证蛋白质摄入，避免含糖饮料。每周可以有一天吃放纵餐，但不要暴食。";
            Files.write(path, defaultDoc.getBytes("UTF-8"));
            return defaultDoc;
        }
    }

    // 按段落切块，兼容 \n 和 \r\n
    public static List<DocumentChunk> splitByParagraphs(String text) {
        // 先统一换行符为 \n，再按连续两个以上换行切分（保留单个换行）
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        // 按两个以上换行符切分（段落间空行）
        String[] paras = normalized.split("\n\n+");
        for (String p : paras) {
            p = p.trim();
            DocumentChunk documentChunk = new DocumentChunk();
            if (!p.isEmpty()){
                documentChunk.setContent(p);
                documentChunkList.add(documentChunk);
            }
        }
        // 如果没有空行分隔，退化为按单个换行切分
        if (documentChunkList.size() <= 1 && text.contains("\n")) {
            documentChunkList = Arrays.stream(normalized.split("\n"))
                    .filter(s -> !s.isEmpty())
                    .map(s-> {
                        DocumentChunk documentChunk = new DocumentChunk();
                        documentChunk.setContent(s.trim());
                        return documentChunk;
                    })
                    .collect(Collectors.toList());
        }
        return documentChunkList;
    }

    public static float[] getEmbedding(String text) throws IOException {
        String json = "{\"model\":\"text-embedding-v3\",\"input\":\"" + escapeJson(text) + "\"}";
        Request request = new Request.Builder()
                .url(EMBEDDING_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String resp = response.body().string();
            if (!response.isSuccessful()) {
                System.err.println("Embedding 失败: " + resp);
                return null;
            }
            JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();
            JsonArray data = obj.getAsJsonArray("data");
            if (data != null && data.size() > 0) {
                JsonArray embeddingArr = data.get(0).getAsJsonObject().getAsJsonArray("embedding");
                float[] vec = new float[embeddingArr.size()];
                for (int i = 0; i < embeddingArr.size(); i++) {
                    vec[i] = embeddingArr.get(i).getAsFloat();
                }
                return vec;
            }
            return null;
        }
    }

    public static float cosineSimilarity(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) return 0;
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static List<Integer> findTopKSimilar(float[] qVec, int k) {
        // 计算所有相似度，排序
        List<SimItem> list = new ArrayList<>();
        for (int i = 0; i < documentChunkList.size(); i++) {
            float[] v = documentChunkList.get(i).getVector();
            if (v.length == 0) continue;
            float sim = cosineSimilarity(qVec, v);
            if(sim < 0.75){
                System.out.println("资料中没有相关内容");
                continue;
            }
            list.add(new SimItem(i, sim));
        }
        list.sort((a, b) -> Float.compare(b.sim, a.sim));
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < Math.min(k, list.size()); i++) {
            result.add(list.get(i).idx);
        }
        return result;
    }

    static class SimItem {
        int idx;
        float sim;
        SimItem(int idx, float sim) { this.idx = idx; this.sim = sim; }
    }

    public static String askLLM(String question, String context) throws IOException {
        // 更宽松的 system prompt
        String systemPrompt = "你是一个智能助手，必须根据提供的多个资料片段回答问题。如果资料中直接包含答案，请直接引用；如果资料不完整，你可以基于已有信息进行合理推断，但不要编造完全无关的内容。如果资料中完全没有涉及，就说“资料中没有提到”。你可以总结、归纳，不必逐字重复。";
        String userPrompt = "以下是几个相关的资料片段：\n" + context + "\n请回答以下问题：" + question;
        String json = "{\n" +
                "  \"model\": \"deepseek-v4-flash\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"system\", \"content\": \"" + escapeJson(systemPrompt) + "\"},\n" +
                "    {\"role\": \"user\", \"content\": \"" + escapeJson(userPrompt) + "\"}\n" +
                "  ],\n" +
                "  \"stream\": false\n" +
                "}";
        Request request = new Request.Builder()
                .url(LLM_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String resp = response.body().string();
            if (!response.isSuccessful()) return "LLM 调用失败: " + resp;
            JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();
            return obj.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        }
    }

    public static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int)c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}