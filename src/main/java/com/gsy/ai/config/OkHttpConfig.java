package com.gsy.ai.config;

import com.gsy.ai.rag.client.AiHttpClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpConfig {

    /**
     * Embedding接口使用
     */
    @Bean("embeddingOkHttpClient")
    public OkHttpClient embeddingOkHttpClient(

            @Value("${gsy.rag.embedding.connect-timeout:30}") int connectTimeout,

            @Value("${gsy.rag.embedding.read-timeout:120}") int readTimeout,

            @Value("${gsy.rag.embedding.write-timeout:60}") int writeTimeout,

            @Value("${gsy.rag.embedding.call-timeout:180}") int callTimeout) {

        return new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .callTimeout(callTimeout, TimeUnit.SECONDS)
                .build();
    }

    /**
     * LLM接口使用
     */
    @Bean("llmOkHttpClient")
    public OkHttpClient llmOkHttpClient(

            @Value("${gsy.rag.llm.connect-timeout:30}") int connectTimeout,

            @Value("${gsy.rag.llm.read-timeout:120}") int readTimeout,

            @Value("${gsy.rag.llm.write-timeout:60}") int writeTimeout,

            @Value("${gsy.rag.llm.call-timeout:180}") int callTimeout) {

        return new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .callTimeout(callTimeout, TimeUnit.SECONDS)
                .build();
    }

    @Bean("embeddingAiHttpClient")
    public AiHttpClient embeddingAiHttpClient(
            @Qualifier("embeddingOkHttpClient") OkHttpClient okHttpClient) {
        return new AiHttpClient(okHttpClient);
    }

    @Bean("llmAiHttpClient")
    public AiHttpClient llmAiHttpClient(
            @Qualifier("llmOkHttpClient") OkHttpClient okHttpClient) {
        return new AiHttpClient(okHttpClient);
    }

}