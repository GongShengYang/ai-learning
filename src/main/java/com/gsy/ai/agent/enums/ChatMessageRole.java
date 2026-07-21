package com.gsy.ai.agent.enums;

import lombok.Getter;

@Getter
public enum ChatMessageRole {

    USER("USER"),
    ASSISTANT("ASSISTANT"),
    SYSTEM("SYSTEM");

    private final String code;

    ChatMessageRole(String code) {
        this.code = code;
    }
}