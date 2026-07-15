package com.gsy.ai.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    /**
     * 创建 Milvus 客户端
     *
     * 作用：
     * Spring Boot 启动时建立一个 MilvusClientV2 Bean，
     * 后续 MilvusVectorStore 等组件直接注入使用。
     */
    @Bean
    public MilvusClientV2 milvusClient(MilvusProperties milvusProperties) {
        ConnectConfig connectConfig = ConnectConfig.builder().
                                        uri(milvusProperties.getUri()).
                                        build();
        return new MilvusClientV2(connectConfig);
    }
}