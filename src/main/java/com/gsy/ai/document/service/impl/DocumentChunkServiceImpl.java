package com.gsy.ai.document.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gsy.ai.document.entity.DocumentChunkDO;
import com.gsy.ai.document.mapper.DocumentChunkMapper;
import com.gsy.ai.document.service.DocumentChunkService;
import org.springframework.stereotype.Service;

@Service
public class DocumentChunkServiceImpl extends ServiceImpl<DocumentChunkMapper, DocumentChunkDO> implements DocumentChunkService {
}