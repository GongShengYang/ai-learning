package com.gsy.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gsy.milvus")
public class MilvusProperties {

    /**
     * Milvus 服务地址
     *
     * 当前 Docker Standalone：
     */
    private String uri;
}