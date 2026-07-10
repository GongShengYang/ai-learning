package com.gsy.ai.rag.prompt;


import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


@Service
public class PromptTemplateService {


    /**
     * 获取系统Prompt
     *
     * 作用：
     * 读取大模型角色规则
     */
    public String getSystemPrompt() throws IOException {

        return readFile(
                "prompts/rag-system-prompt.txt"
        );
    }


    /**
     * 获取用户Prompt
     *
     * 作用：
     * 把问题和上下文填入模板
     */
    public String getUserPrompt(
            String question,
            String context
    ) throws IOException {


        String template = readFile(
                "prompts/rag-user-prompt.txt"
        );


        return template
                .replace("{{context}}", context)
                .replace("{{question}}", question);
    }



    private String readFile(String path)
            throws IOException {


        ClassPathResource resource =
                new ClassPathResource(path);


        return new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
    }

}