package com.gsy.ai.rag.client;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;

@Slf4j
public class AiHttpClient {

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json");

    private final OkHttpClient okHttpClient;

    public AiHttpClient(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    public String postJson(String url, String apiKey, String jsonBody) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String resp = response.body() == null ? "" : response.body().string();

            if (!response.isSuccessful()) {
                log.error("AI接口调用失败，状态码: {}, 响应: {}", response.code(), resp);
                throw new IOException("AI接口调用失败: " + resp);
            }

            if (resp == null || resp.trim().isEmpty()) {
                throw new IOException("AI接口返回为空");
            }

            return resp;
        }
    }
}