package com.gsy.ai.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gsy.ai.document.entity.DocumentDO;
import com.gsy.ai.document.mapper.DocumentMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

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
        System.out.println("DocumentQueryTool" );
        Long l = documentMapper.selectCount(new QueryWrapper<DocumentDO>());
        return "当前知识库共有"
                + l
                + "篇文档";
    }
}
