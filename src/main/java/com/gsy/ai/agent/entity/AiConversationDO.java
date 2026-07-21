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
@TableName("ai_conversation")
public class AiConversationDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话唯一标识。
     */
    private String conversationId;

    /**
     * 会话所属用户。
     */
    private Long userId;

    /**
     * 会话标题。
     */
    private String title;

    /**
     * 状态：1正常，0关闭。
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}