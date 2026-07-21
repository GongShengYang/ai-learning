package com.gsy.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gsy.ai.agent.entity.AiChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiChatMessageMapper
        extends BaseMapper<AiChatMessageDO> {
}