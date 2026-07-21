package com.gsy.ai.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_message")
public class AiChatMessageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话。
     */
    private String conversationId;

    /**
     * 所属用户。
     */
    private Long userId;

    /**
     * USER、ASSISTANT、SYSTEM。
     */
    private String role;

    /**
     * 消息正文。
     */
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}