package com.gsy.ai.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.gsy.ai.entity.DocumentDO;
import com.gsy.ai.mapper.DocumentMapper;
import com.gsy.ai.rag.retriever.Retriever;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import javax.swing.text.Document;
import java.util.List;

@Component
public class DocumentQueryTool {

    private final DocumentMapper documentMapper;


    public DocumentQueryTool(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Tool(description="""
                                    查询知识库中已有文档。
                                   当用户询问：
                                   有多少文档、
                                   记录了多少资料、
                                   知识库规模时调用。
                        """)
    public String DocumentQueryTool() {
        Long l = documentMapper.selectCount(new QueryWrapper<DocumentDO>());
        return "当前知识库共有"
                + l
                + "篇文档";
    }
}
