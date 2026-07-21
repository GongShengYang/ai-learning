package com.gsy.ai.agent.tool;


import com.gsy.ai.document.rag.retriever.RetrieveRequest;
import com.gsy.ai.document.rag.retriever.Retriever;
import com.gsy.ai.document.rag.store.ChunkWithVector;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;


import java.io.IOException;
import java.util.List;


@Component
public class RagSearchTool {


    private final Retriever retriever;


    public RagSearchTool(Retriever retriever) {
        this.retriever = retriever;
    }



    /**
     * 知识库搜索工具
     *
     * Agent 根据用户问题决定是否调用。
     *
     * @param question 用户问题
     * @return 知识库检索结果
     */
    @Tool(description = "查询企业知识库。 当用户询问公司制度、 文档内容、 业务规则、 技术资料时调用该工具。 ")
    public String searchKnowledge(String question) {
        System.out.println("searchKnowledge 参数是：" + question);
        RetrieveRequest request = new RetrieveRequest();

        request.setQuestion(question);

        /*
         当前 Agent 默认查询全知识库

         后续如果需要：
         用户选择文档
         可以增加 documentId 参数
         */
        request.setDocumentId(null);

        /*
         Agent场景默认召回数量

         不需要太多，
         防止上下文过长。
         */
        request.setTopK(3);

        List<ChunkWithVector> chunks = null;
        try {
            chunks = retriever.retrieve(request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        if (chunks == null || chunks.isEmpty()) {

            return "知识库中没有找到相关信息";
        }



        StringBuilder context = new StringBuilder();

        for (ChunkWithVector chunk : chunks) {
            context.append("【来源信息】\n");
            context.append("documentId:")
                    .append(chunk.getDocumentId())
                    .append("\n");
            context.append("chunkIndex:")
                    .append(chunk.getChunkIndex())
                    .append("\n");
            context.append("similarity:")
                    .append(chunk.getScore())
                    .append("\n\n");
            context.append(chunk.getContent());
            context.append("\n\n");
        }


        return context.toString();
    }
}