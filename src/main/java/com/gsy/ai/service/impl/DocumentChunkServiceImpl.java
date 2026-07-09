package com.gsy.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gsy.ai.entity.DocumentChunkDO;
import com.gsy.ai.mapper.DocumentChunkMapper;
import com.gsy.ai.service.DocumentChunkService;
import org.springframework.stereotype.Service;

@Service
public class DocumentChunkServiceImpl extends ServiceImpl<DocumentChunkMapper, DocumentChunkDO> implements DocumentChunkService {
}