package com.gsy.ai.document.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gsy.ai.document.entity.DocumentDO;
import com.gsy.ai.document.mapper.DocumentMapper;
import com.gsy.ai.document.service.DocumentService;
import org.springframework.stereotype.Service;

@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, DocumentDO> implements DocumentService {
}