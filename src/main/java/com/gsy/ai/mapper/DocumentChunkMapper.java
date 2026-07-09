package com.gsy.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gsy.ai.dome.DO.DocumentChunk;
import com.gsy.ai.entity.DocumentChunkDO;
import com.gsy.ai.entity.DocumentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkDO> {
}
