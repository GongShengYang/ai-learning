package com.gsy.ai.rag.store;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.gsy.ai.entity.DocumentChunkDO;
import com.gsy.ai.mapper.DocumentChunkMapper;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Primary
@Component
public class MilvusVectorStore implements VectorStore {

    private static final String COLLECTION_NAME =
            "document_chunk_vector";

    private static final String VECTOR_FIELD =
            "vector";

    private final MilvusClientV2 milvusClient;

    private final DocumentChunkMapper documentChunkMapper;

    private final Gson gson = new Gson();

    public MilvusVectorStore(
            MilvusClientV2 milvusClient,
            DocumentChunkMapper documentChunkMapper
    ) {
        this.milvusClient = milvusClient;
        this.documentChunkMapper = documentChunkMapper;
    }

    /**
     * 添加单个 Chunk。
     *
     * 接口名称虽然是 add，但底层使用 Upsert：
     *
     * - 主键不存在：插入
     * - 主键已存在：更新
     *
     * 这样导入重试时不会因为相同 chunk_id 重复而直接失败。
     */
    @Override
    public void add(ChunkWithVector chunk) {
        if (chunk == null) {
            return;
        }

        addBatch(Collections.singletonList(chunk));
    }

    /**
     * 批量写入 Milvus。
     *
     * 后续 DocumentImportService 会调用这个方法，
     * 避免一个 Chunk 发起一次网络请求。
     */
    public void addBatch(List<ChunkWithVector> chunks) {

        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        List<JsonObject> entities = new ArrayList<>();

        for (ChunkWithVector chunk : chunks) {

            validateChunk(chunk);

            JsonObject entity = new JsonObject();

            entity.addProperty(
                    "chunk_id",
                    chunk.getChunkId()
            );

            entity.addProperty(
                    "document_id",
                    chunk.getDocumentId()
            );

            entity.addProperty(
                    "chunk_index",
                    chunk.getChunkIndex()
            );

            entity.add(
                    VECTOR_FIELD,
                    gson.toJsonTree(chunk.getVector())
            );

            entities.add(entity);
        }

        UpsertReq request =
                UpsertReq.builder()
                        .collectionName(COLLECTION_NAME)
                        .data(entities)
                        .build();

        milvusClient.upsert(request);

        log.info(
                "Milvus 批量写入完成，collection={}, count={}",
                COLLECTION_NAME,
                entities.size()
        );
    }

    /**
     * 使用问题向量从 Milvus 检索 TopK Chunk。
     *
     * 执行流程：
     *
     * 1. 将 float[] 包装成 FloatVec
     * 2. Milvus 执行向量搜索
     * 3. Milvus 返回 chunk_id 和 score
     * 4. 根据 chunk_id 批量查询 MySQL
     * 5. 按 Milvus 相似度顺序组装 ChunkWithVector
     */
    @Override
    public List<ChunkWithVector> search(
            float[] queryVector,
            Long documentId,
            int topK
    ) {

        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }

        if (topK <= 0) {
            return List.of();
        }

        SearchReq.SearchReqBuilder builder =
                SearchReq.builder()
                        .collectionName(COLLECTION_NAME)
                        .annsField(VECTOR_FIELD)
                        .data(
                                List.of(
                                        new FloatVec(queryVector)
                                )
                        )
                        .topK(topK)

                        /*
                         * 当前项目允许用户上传后立即提问，
                         * 因此先使用 STRONG 保证读取到最新写入。
                         *
                         * 数据量扩大后可以再评估 BOUNDED。
                         */
                        .consistencyLevel(
                                ConsistencyLevel.STRONG
                        );

        /*
         * 指定文档问答：
         * 只搜索该 documentId 下的向量。
         *
         * 全知识库问答：
         * documentId 为 null，不增加过滤条件。
         */
        if (documentId != null) {
            builder.filter(
                    "document_id == " + documentId
            );
        }

        SearchResp response =
                milvusClient.search(
                        builder.build()
                );

        List<List<SearchResp.SearchResult>> searchResults =
                response.getSearchResults();

        if (searchResults == null || searchResults.isEmpty()) {
            return List.of();
        }

        /*
         * 本次只传入了一个问题向量，
         * 因此外层 List 中只有一组结果。
         */
        List<SearchResp.SearchResult> firstResultGroup =
                searchResults.get(0);

        if (firstResultGroup == null
                || firstResultGroup.isEmpty()) {
            return List.of();
        }

        /*
         * LinkedHashMap 保持 Milvus 返回的相似度顺序。
         *
         * key：chunkId
         * value：score
         */
        Map<Long, Float> scoreMap =
                new LinkedHashMap<>();

        for (SearchResp.SearchResult searchResult
                : firstResultGroup) {

            Long chunkId =
                    convertToLong(
                            searchResult.getId()
                    );

            if (chunkId == null) {
                log.warn(
                        "Milvus 搜索结果主键无法转换，id={}",
                        searchResult.getId()
                );
                continue;
            }

            scoreMap.put(
                    chunkId,
                    searchResult.getScore()
            );
        }

        if (scoreMap.isEmpty()) {
            return List.of();
        }

        List<Long> chunkIds =
                new ArrayList<>(
                        scoreMap.keySet()
                );

        /*
         * 一次性批量查询 MySQL，
         * 不要在循环中 selectById，避免 N+1 查询。
         */
        List<DocumentChunkDO> chunkDOList =
                documentChunkMapper.selectBatchIds(
                        chunkIds
                );

        if (chunkDOList == null
                || chunkDOList.isEmpty()) {

            log.warn(
                    "Milvus 命中向量，但 MySQL 未查询到对应 Chunk，chunkIds={}",
                    chunkIds
            );

            return List.of();
        }

        Map<Long, DocumentChunkDO> chunkDOMap =
                chunkDOList.stream()
                        .filter(
                                Objects::nonNull
                        )
                        .collect(
                                Collectors.toMap(
                                        DocumentChunkDO::getId,
                                        item -> item,
                                        (oldValue, newValue) ->
                                                oldValue,
                                        HashMap::new
                                )
                        );

        List<ChunkWithVector> result =
                new ArrayList<>();

        /*
         * 必须按照 scoreMap 的顺序组装。
         *
         * selectBatchIds() 返回顺序不一定等于 Milvus 的相似度顺序。
         */
        for (Map.Entry<Long, Float> entry
                : scoreMap.entrySet()) {

            Long chunkId = entry.getKey();

            DocumentChunkDO chunkDO =
                    chunkDOMap.get(chunkId);

            if (chunkDO == null) {
                log.warn(
                        "Milvus 数据和 MySQL 数据不一致，chunkId={}",
                        chunkId
                );
                continue;
            }

            float[] vector =
                    parseVector(
                            chunkDO.getVector()
                    );

            ChunkWithVector chunk =
                    new ChunkWithVector(
                            chunkDO.getId(),
                            chunkDO.getContent(),
                            vector,
                            chunkDO.getDocumentId(),
                            chunkDO.getChunkIndex(),
                            entry.getValue()
                    );

            result.add(chunk);
        }

        log.info(
                "Milvus 向量检索完成，documentId={}, topK={}, actualCount={}",
                documentId,
                topK,
                result.size()
        );

        for (ChunkWithVector chunk : result) {
            log.info(
                    "Milvus 命中，chunkId={}, documentId={}, chunkIndex={}, score={}",
                    chunk.getChunkId(),
                    chunk.getDocumentId(),
                    chunk.getChunkIndex(),
                    chunk.getScore()
            );
        }

        return result;
    }

    /**
     * 当前不允许通过 VectorStore.clear() 清空整个 Collection。
     *
     * 防止业务代码误调用后删除全部向量。
     */
    @Override
    public void clear() {
        log.warn(
                "MilvusVectorStore.clear 当前不执行，防止误删全部向量数据"
        );
    }

    @Override
    public void deleteByChunkId(Long chunkId) {

        DeleteReq request =
                DeleteReq.builder()
                        .collectionName(COLLECTION_NAME)
                        .filter("chunk_id == " + chunkId)
                        .build();

        milvusClient.delete(request);

        log.info("删除成功 chunkId={}", chunkId);
    }

    /**
     * 校验写入 Milvus 所需字段。
     */
    private void validateChunk(ChunkWithVector chunk) {

        if (chunk == null) {
            throw new IllegalArgumentException(
                    "写入 Milvus 的 Chunk 不能为空"
            );
        }

        if (chunk.getChunkId() == null) {
            throw new IllegalArgumentException(
                    "写入 Milvus 时 chunkId 不能为空"
            );
        }

        if (chunk.getDocumentId() == null) {
            throw new IllegalArgumentException(
                    "写入 Milvus 时 documentId 不能为空"
            );
        }

        if (chunk.getChunkIndex() == null) {
            throw new IllegalArgumentException(
                    "写入 Milvus 时 chunkIndex 不能为空"
            );
        }

        if (chunk.getVector() == null
                || chunk.getVector().length == 0) {
            throw new IllegalArgumentException(
                    "写入 Milvus 时 vector 不能为空"
            );
        }
    }

    /**
     * Milvus 主键响应可能是 Long、Integer 或其他 Number 类型。
     */
    private Long convertToLong(Object id) {

        if (id == null) {
            return null;
        }

        if (id instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.parseLong(
                    id.toString()
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 MySQL vector JSON 恢复 float[]。
     */
    private float[] parseVector(String vectorJson) {

        if (vectorJson == null
                || vectorJson.trim().isEmpty()) {
            return new float[0];
        }

        try {
            return gson.fromJson(
                    vectorJson,
                    float[].class
            );
        } catch (Exception e) {

            log.warn(
                    "解析 MySQL Chunk 向量失败，原因={}",
                    e.getMessage()
            );

            return new float[0];
        }
    }
}