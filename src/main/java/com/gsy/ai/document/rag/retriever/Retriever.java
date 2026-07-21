package com.gsy.ai.document.rag.retriever;

import com.gsy.ai.document.rag.store.ChunkWithVector;

import java.io.IOException;
import java.util.List;

public interface Retriever {

    /**
     * 根据用户问题检索相关文档片段
     *
     * question
     * 作用：用户输入的问题
     *
     * topK
     * 作用：返回最相关的前 K 个文档片段
     */

    List<ChunkWithVector> retrieve(RetrieveRequest request) throws IOException;
}