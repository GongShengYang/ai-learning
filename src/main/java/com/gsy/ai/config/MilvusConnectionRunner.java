package com.gsy.ai.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gsy.ai.document.rag.embedding.EmbeddingService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.ListIndexesReq;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class MilvusConnectionRunner implements CommandLineRunner {

    private static final String COLLECTION_NAME = "document_chunk_vector";
    private static final String VECTOR_INDEX_NAME = "document_chunk_vector_idx";

    private final MilvusClientV2 milvusClient;

    private final EmbeddingService embeddingService;

    public MilvusConnectionRunner(
            MilvusClientV2 milvusClient,
            EmbeddingService embeddingService
    ) {
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
    }

    @Override
    public void run(String... args) {

        try {
            verifyConnection();

            int embeddingDimension = getEmbeddingDimension();

            log.info(
                    "当前 Embedding 向量维度: {}",
                    embeddingDimension
            );

            // 1. 创建 Collection
            createCollectionIfNecessary(embeddingDimension);

            // 2. 创建向量索引
            createIndexIfNecessary();

            // 3. 加载 Collection
            loadCollection();

        } catch (Exception e) {
            log.error("Milvus 测试流程执行失败", e);

            throw new IllegalStateException(
                    "Milvus 测试流程执行失败",
                    e
            );
        }
    }

    private int getEmbeddingDimension() {

        float[] vector =
                embeddingService.embed("dimension");

        return vector.length;
    }

    /**
     * 将 Collection 加载到 Milvus 查询节点内存。
     *
     * Query 和 Search 执行前必须加载。
     */
    private void loadCollection() {

        LoadCollectionReq request =
                LoadCollectionReq.builder()
                        .collectionName(COLLECTION_NAME)
                        .build();

        milvusClient.loadCollection(request);

        log.info(
                "Collection 加载成功: {}",
                COLLECTION_NAME
        );
    }

    /**
     * 真正向 Milvus 发起 RPC 调用。
     */
    private void verifyConnection() {

        List<String> collectionNames =
                milvusClient
                        .listCollections()
                        .getCollectionNames();

        log.info(
                "Milvus 真实连接成功，当前 Collection: {}",
                collectionNames
        );
    }

    /**
     * Collection 不存在时创建。
     */
    private void createCollectionIfNecessary(int dimension) {

        boolean exists = milvusClient.hasCollection(
                HasCollectionReq.builder()
                        .collectionName(COLLECTION_NAME)
                        .build()
        );

        if (exists) {
            log.info(
                    "Collection 已存在: {}",
                    COLLECTION_NAME
            );
            return;
        }

        CreateCollectionReq.CollectionSchema schema =
                milvusClient.createSchema();

        schema.addField(
                AddFieldReq.builder()
                        .fieldName("chunk_id")
                        .dataType(DataType.Int64)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build()
        );

        schema.addField(
                AddFieldReq.builder()
                        .fieldName("document_id")
                        .dataType(DataType.Int64)
                        .build()
        );

        schema.addField(
                AddFieldReq.builder()
                        .fieldName("chunk_index")
                        .dataType(DataType.Int32)
                        .build()
        );

        schema.addField(
                AddFieldReq.builder()
                        .fieldName("vector")
                        .dataType(DataType.FloatVector)
                        .dimension(dimension)
                        .build()
        );

        CreateCollectionReq request =
                CreateCollectionReq.builder()
                        .collectionName(COLLECTION_NAME)
                        .collectionSchema(schema)
                        .build();

        milvusClient.createCollection(request);

        log.info(
                "Collection 创建成功，名称: {}, 向量维度: {}",
                COLLECTION_NAME,
                dimension
        );
    }


    /**
     * 为 vector 字段创建向量索引。
     *
     * Collection 只有 Schema 还不够。
     * Search 和 loadCollection 需要 vector 字段存在索引。
     */
    private void createIndexIfNecessary() {

        List<String> indexes =
                milvusClient.listIndexes(
                        ListIndexesReq.builder()
                                .collectionName(COLLECTION_NAME)
                                .fieldName("vector")
                                .build()
                );

        if (indexes != null && !indexes.isEmpty()) {
            log.info(
                    "Milvus 向量索引已存在，跳过创建: {}",
                    indexes
            );
            return;
        }

        IndexParam indexParam =
                IndexParam.builder()
                        .fieldName("vector")
                        .indexName(VECTOR_INDEX_NAME)

                        /*
                         * AUTOINDEX：
                         * 让 Milvus 根据当前环境自动选择适合的索引实现。
                         *
                         * 当前学习项目不需要先手动选择 IVF_FLAT、HNSW 等。
                         */
                        .indexType(IndexParam.IndexType.AUTOINDEX)

                        /*
                         * COSINE：
                         * 使用余弦相似度。
                         *
                         * 与你原来 DatabaseVectorStore 中的
                         * cosineSimilarity() 保持语义一致。
                         */
                        .metricType(IndexParam.MetricType.COSINE)
                        .build();

        CreateIndexReq request =
                CreateIndexReq.builder()
                        .collectionName(COLLECTION_NAME)
                        .indexParams(
                                Collections.singletonList(indexParam)
                        )

                        /*
                         * 同步等待索引创建完成。
                         * 避免索引尚未完成就立即 loadCollection。
                         */
                        .sync(true)
                        .build();

        milvusClient.createIndex(request);

        log.info(
                "Milvus 向量索引创建成功，collection={}, field=vector, index={}",
                COLLECTION_NAME,
                VECTOR_INDEX_NAME
        );
    }


}