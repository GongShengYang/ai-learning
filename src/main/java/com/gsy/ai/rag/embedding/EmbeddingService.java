package com.gsy.ai.rag.embedding;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gsy.ai.rag.client.AiHttpClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class EmbeddingService {

    @Resource(name = "embeddingAiHttpClient")
    private AiHttpClient aiHttpClient;

    private final Gson gson = new Gson();

    @Value("${gsy.rag.embedding.url}")
    private String url;

    @Value("${gsy.rag.embedding.api-key}")
    private String apiKey;

    @Value("${gsy.rag.embedding.model}")
    private String model;

    /**
     * 单条文本转向量
     */
    public float[] embed(String text) throws IOException {
        List<float[]> list = embedBatch(List.of(text));
        return list.isEmpty() ? new float[0] : list.get(0);
    }

    /**
     * 批量文本转向量
     */
    /**
     * 批量文本转向量
     */
    public List<float[]> embedBatch(List<String> texts) throws IOException {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        List<float[]> result = new ArrayList<>();
        int batchSize = 10;

        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            List<String> batchTexts = texts.subList(start, end);

            List<float[]> batchVectors = doEmbedBatch(batchTexts);
            result.addAll(batchVectors);
        }

        return result;
    }

    private List<float[]> doEmbedBatch(List<String> texts) throws IOException {
        List<float[]> result = new ArrayList<>();

        JsonArray inputArr = new JsonArray();
        for (String t : texts) {
            inputArr.add(t);
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("input", inputArr);

        String resp = aiHttpClient.postJson(url, apiKey, body.toString());

        JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();
        JsonArray data = obj.getAsJsonArray("data");

        if (data == null || data.size() == 0) {
            throw new IOException("Embedding 返回数据为空");
        }

        if (data.size() != texts.size()) {
            throw new IOException("Embedding 返回数量和输入数量不一致");
        }

        for (int i = 0; i < data.size(); i++) {
            JsonArray embArr = data.get(i)
                    .getAsJsonObject()
                    .getAsJsonArray("embedding");

            float[] vec = new float[embArr.size()];

            for (int j = 0; j < embArr.size(); j++) {
                vec[j] = embArr.get(j).getAsFloat();
            }

            result.add(vec);
        }

        return result;
    }
}