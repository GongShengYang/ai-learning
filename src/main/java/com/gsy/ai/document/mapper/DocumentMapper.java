package com.gsy.ai.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gsy.ai.document.entity.DocumentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentMapper extends BaseMapper<DocumentDO> {
}
