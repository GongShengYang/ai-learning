package com.gsy.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gsy.ai.entity.DocumentDO;
import com.gsy.ai.mapper.DocumentMapper;
import com.gsy.ai.service.DocumentService;
import org.springframework.stereotype.Service;

@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, DocumentDO> implements DocumentService {
}