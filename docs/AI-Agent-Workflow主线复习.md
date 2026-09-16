# AI Agent Workflow 主线复习

## 1. 当前项目一句话说明

这是一个基于 Spring Boot、Spring AI、MySQL 和 Milvus 的企业知识库 AI 应用：模型负责理解和规划，Java 负责控制流程、校验结果、执行能力、处理异常，并通过 SSE 向前端实时推送执行过程和回答。

## 2. 先记住这条总主线

```text
用户请求
  → 准备会话和历史消息
  → 保存用户消息
  → 模型输出结构化意图
  → Java根据意图和置信度决定路由
  → 复合任务才调用Planner拆分步骤
  → Java Executor执行受控业务分支
  → 生成最终回答
  → 保存助手消息
  → 返回状态、轨迹、来源和回答
```

真正的 Workflow 层不是某一个特殊注解，而是 `WorkflowServiceImpl.answerInternal()` 对这些节点的编排，以及 `executeNode()` 提供的状态、重试、超时和失败处理。

## 3. 两个接口，共用一套业务流程

入口文件：`src/main/java/com/gsy/ai/workflow/controller/WorkflowController.java`

| 接口 | 用途 | 返回方式 |
|---|---|---|
| `POST /api/ai/workflow/question` | 普通 API 调用 | 全部完成后一次返回 `WorkflowQuestionResponse` |
| `POST /api/ai/workflow/question/stream` | 页面实时展示 | 保持 SSE 连接，持续返回事件 |

Service 中的对应关系：

```text
answer()             → answerInternal(..., noop(), false)
answerWithEvents()   → answerInternal(..., eventListener, true)
```

`streamAnswer` 只决定答案是一次返回还是分片推送，不改变意图分类、Java 路由和业务执行逻辑。

## 4. WorkflowContext 到底是什么

文件：`src/main/java/com/gsy/ai/workflow/model/WorkflowContext.java`

`WorkflowContext` 是一次请求的运行时状态，不是一整个会话永久共用的对象。每次用户提问都会创建新的 Context，会话历史则保存在数据库中。

它把不同节点产生的数据集中起来：

| 字段 | 谁写入 | 后续用途 |
|---|---|---|
| `userId/question` | 创建 Context 时 | 本次请求的原始输入 |
| `conversationId/historyMessages` | 会话准备节点 | 分类和回答时理解上下文 |
| `intentResult` | 意图分类节点 | 保存模型输出的意图、置信度等 |
| `route` | Java 路由节点 | 决定唯一执行分支 |
| `plan` | Planner | 保存复合任务计划 |
| `planStepResults` | Executor | 保存每个计划步骤的执行结果 |
| `sources` | RAG 检索分支 | 返回 S1、S2 等证据来源 |
| `answer` | 路由执行节点 | 最终回答并用于保存助手消息 |
| `status/currentNode/errorMessage` | 节点模板 | 表示整体状态和失败位置 |
| `executionTrace` | 节点模板 | 保存每次尝试、状态、耗时和说明 |

使用 Context 的好处是不用让每个方法返回一个越来越复杂的大对象，也不用把十几个参数在节点之间传来传去。

## 5. 七个固定节点

节点枚举：`src/main/java/com/gsy/ai/workflow/enums/WorkflowNode.java`

### 5.1 CONVERSATION_PREPARATION

- `conversationId` 为空：创建新会话。
- 不为空：校验会话存在，并且属于当前 `userId`。
- 读取最近 20 条历史消息，顺序恢复为从旧到新。

为什么先读取历史再保存当前问题：避免当前问题同时出现在 `historyMessages` 和 `.user(question)` 中，被重复发送给模型。

### 5.2 USER_MESSAGE_PERSISTENCE

在调用模型前保存用户消息。即使后续模型失败，也保留用户真实提交过的问题。

该节点涉及数据库写入，所以不能像只读查询一样无脑重试，否则可能产生重复数据。

### 5.3 INTENT_CLASSIFICATION

调用 `StructuredOutputService.classifyQuestion()`，让模型返回 `QuestionIntentResult`：

```text
intent      用户想做什么
needTool    是否需要外部能力
confidence  模型对分类有多确定，范围0~1
reason      中文分类理由
```

分类只取最近 6 条历史，并把分类输入限制在约 4800 字，避免把 20 条历史全部发送给模型造成 Token 浪费。

Structured Output 的实际流程：

```text
BeanOutputConverter.getFormat()
  → 把Java类结构写入Prompt
  → 模型返回JSON文本
  → convert()转换成Java对象
  → Java校验字段、范围和业务关系
```

转换失败或校验失败时最多生成 2 次。第二次会携带上一次内容和具体错误，让模型有针对性地纠正。

### 5.4 ROUTE_DECISION

这里不再调用模型，由 Java 判断：

```java
confidence >= 0.6 ? intent : UNSUPPORTED
```

原因是模型负责语义理解，Java 负责落实业务边界。低置信度时宁可让用户补充问题，也不冒险调用错误能力。

### 5.5 TASK_PLANNING

只有 `COMPOSITE_TASK` 才执行，普通单步骤问题会跳过，避免额外模型调用、延迟和 Token 消耗。

Planner 使用模型将问题拆成 2～3 步，每一步只能选择白名单动作：

```text
KNOWLEDGE_SEARCH  检索企业知识库
DOCUMENT_COUNT    查询知识库文档数量
GENERAL_CHAT      普通文本任务
```

模型只生成计划，不执行 Java 方法。Java 会继续校验目标长度、步骤数量、连续序号、动作、指令长度，并要求至少存在一个知识库或文档统计步骤。计划不合法时最多纠正 1 次。

### 5.6 ROUTE_EXECUTION

这是 Executor 真正执行业务的节点。

| route/action | 实际执行 |
|---|---|
| `KNOWLEDGE_SEARCH` | Retriever 检索 Milvus/MySQL，再让模型依据资料回答 |
| `DOCUMENT_COUNT` | Java 直接查询 MySQL 文档数量 |
| `COMPOSITE_TASK` | 按计划顺序执行多个受控动作，最后让模型汇总 |
| `GENERAL_CHAT` | 模型结合会话历史回答 |
| `UNSUPPORTED` | Java 返回固定提示，不调用外部能力 |

复合任务示例：

```text
问题：总结请假制度，并告诉我知识库有多少篇文档

Planner：
1. KNOWLEDGE_SEARCH：总结请假制度
2. DOCUMENT_COUNT：查询文档总数

Executor：
1. Java调用知识检索分支，保存步骤1结果
2. Java调用文档统计分支，保存步骤2结果
3. 模型根据两个步骤结果生成完整答案
```

Planner 决定“要做几步”，Executor 决定“这些步骤实际调用哪些受控 Java 分支”。当前项目没有让模型自由调用 Tool，是因为流程简单固定，Java 控制更稳定、可测试、可审计。

### 5.7 ASSISTANT_MESSAGE_PERSISTENCE

只有成功生成回答后才保存助手消息，并更新会话最后活跃时间。失败回答不作为正常助手历史保存。

如果回答已经生成，但保存助手消息失败，接口保留原回答，同时通过 `status、failedNode、errorMessage` 告诉调用方持久化失败。

## 6. 节点状态、轨迹、重试和超时

统一入口是 `WorkflowServiceImpl.executeNode()`。

每次执行节点都会：

```text
设置currentNode
  → 增加RUNNING轨迹
  → 推送RUNNING事件
  → 在线程池中执行节点并等待结果
  → 成功：记录SUCCESS、耗时并返回
  → 失败：记录FAILED，判断是否重试
```

`WorkflowStepRecord` 保存：节点、尝试次数、状态、耗时和安全说明。因此成功和失败都有完整轨迹，不是只有失败才能看到节点。

当前重试规则：

- `ROUTE_EXECUTION` 最多执行 2 次，即失败后最多重试 1 次。
- Structured Output 和 Planner 各有自己的纠正重试。
- 消息写入等非幂等节点不重试。
- 超时不重试，因为超时只表示等待结束，底层 HTTP/数据库操作不一定真正停止。立即重试可能造成两个任务并行执行。

当前超时：

| 节点类型 | 超时 |
|---|---:|
| 会话准备、消息保存 | 3 秒 |
| 意图分类 | 30 秒 |
| 任务规划 | 30 秒 |
| Java 路由判断 | 1 秒 |
| 路由业务执行 | 60 秒 |

`Future.cancel(true)` 只是发送线程中断信号，不等于强制关闭底层模型 HTTP 请求。因此生产环境仍要分别配置 HTTP、数据库和向量库自己的超时。

## 7. SSE 流式返回

### 7.1 两个线程池

文件：`src/main/java/com/gsy/ai/workflow/config/WorkflowExecutorConfig.java`

- `workflowRequestExecutor`：执行一整个 SSE Workflow。
- `workflowNodeExecutor`：执行其中一个需要超时控制的节点。

不能共用的原因：如果外层 SSE 请求占满所有线程，同时每个请求都等待内层节点获取线程，就可能互相等待。

### 7.2 回调与真正网络发送

```text
Service：eventListener.onEvent(event)
  → 执行Controller传入的Lambda
  → Controller：emitter.send(...)
  → 数据写入同一条SSE HTTP响应
```

所以 `onEvent()` 只是 Java 内部回调，`emitter.send()` 才是真正通知前端。

`SseEmitter` 生命周期回调：

- `onCompletion`：连接结束，业务未完成时尝试取消后台任务。
- `onTimeout`：5 分钟仍未结束，取消任务并关闭连接。
- `onError`：网络发送异常，前端已收不到内容，尝试取消任务。

这些回调只是提前注册“以后发生对应事件时做什么”，不是注册时立即执行。

### 7.3 当前事件类型

| type | 含义 |
|---|---|
| `node` | 固定 Workflow 节点状态 |
| `plan-step` | Planner 动态步骤状态 |
| `source` | 一条 RAG 来源 |
| `route-output-reset` | 路由重试前清除旧回答片段和旧来源 |
| `content` | 模型回答片段 |
| `completed` | 整个 Workflow 成功完成 |
| `error` | 整个 Workflow 最终失败 |

模型的 `.stream().content()` 返回的是文本片段，不保证一个片段只有一个字符。前端依次拼接片段，用户就会看到回答逐渐出现。

## 8. RAG 来源追踪

核心文件：

- `WorkflowServiceImpl.recordSources()`
- `WorkflowSource.java`
- `WorkflowStreamEvent.source()`

流程：

```text
Retriever返回Chunk
  → Java按顺序生成S1、S2、S3
  → 同一个编号写入模型Prompt
  → 模型在答案句末标注[S1]
  → 接口另外返回sources列表
  → 前端用S1把答案和来源卡片对应起来
```

必须区分两个可信程度：

- `sources` 是 Java 根据真实检索结果生成的，来源数据是确定的。
- 答案里的 `[S1]` 是模型生成的，仍可能漏引、错引或编造编号。

当前已经增加第一层 Java 引用治理：

```text
最终答案生成
  → 正则提取[S1]等编号
  → 有检索资料时检查是否至少引用一个编号
  → 检查每个编号是否存在于本次真实sources中
  → 通过后保存回答
  → 失败则抛出异常，复用ROUTE_EXECUTION的有限重试
```

第二次生成不是盲目重复：Context会保存第一次引用校验的具体错误，并放进下一次Prompt，要求模型针对“漏引”或“假编号”进行纠正。

这仍然只是格式和编号真实性校验。例如，S1是真实来源，但模型把不受S1支持的句子标成`[S1]`，普通正则无法发现。判断“资料是否真的支持这句话”属于后续语义忠实度评测。

当前只返回 200 字 `contentPreview`，原因是完整切片可能很长、可能包含敏感内容、会增加网络和页面负担。`documentId + chunkIndex` 可以作为以后按权限查询完整原文的定位信息。

路由重试时会清空旧来源并重新编号；SSE 同时发送 `route-output-reset`，让页面清除第一次已经流出的半段回答和失效来源。

## 9. 模型决定什么，Java决定什么

| 内容 | 决定者 | 原因 |
|---|---|---|
| 用户问题是什么意图 | 模型 | 需要自然语言理解 |
| 复合任务拆成几步 | 模型 Planner | 任务描述不固定 |
| 低置信度是否执行 | Java | 风险边界必须确定 |
| 可以执行哪些动作 | Java 枚举白名单 | 防止越权或乱调用 |
| action 对应哪个方法 | Java Executor | 保持稳定、可测试 |
| 节点顺序、超时、重试 | Java | 属于工程控制逻辑 |
| RAG 的 S1/S2 编号 | Java | 证据编号必须确定 |
| 最终回答如何组织语言 | 模型 | 需要语言生成能力 |

一句话记忆：模型处理“不确定的语言问题”，Java控制“必须确定的业务边界”。

## 10. 如何阅读一次真实请求

推荐按下面顺序在 IDEA 中打断点，不要一开始钻进每个实现细节：

1. `WorkflowController.question()` 或 `questionStream()`
2. `WorkflowServiceImpl.answerInternal()`
3. `executeNode()`
4. `classifyIntent()`
5. `decideRoute()`
6. 复合任务时看 `createPlan()`
7. `executeRoute()`
8. 对应的 `answerByKnowledge/answerByDocumentCount/answerByCompositeTask/answerByGeneralChat`
9. `callOrStream()`
10. `saveAssistantMessage()`

观察时重点看 `WorkflowContext` 每经过一个节点增加了什么，不需要先背线程池和 SSE 解析代码。

## 11. 面试时可以这样介绍

> 我实现了一套企业知识库 Agent Workflow。模型先通过 Structured Output 识别意图，Java 根据置信度执行确定性路由。普通任务直接进入固定分支，复合任务由 Planner 拆成受限步骤，再由 Java Executor 按动作白名单执行。Workflow 统一记录节点状态、耗时、尝试次数和失败位置，并为不同节点设置超时；只读路由允许有限重试，非幂等写操作不重试。系统支持按用户和会话保存上下文，通过 SSE 推送节点、计划步骤和模型文本。RAG 回答由 Java 为检索切片生成来源编号，并把回答中的引用与文档来源一起返回。

如果面试官追问“为什么不用模型直接调用所有 Tool”：

> 当前流程的业务动作少且路径固定，所以让模型理解和规划，让 Java 控制执行，更容易保证权限、幂等、重试和审计。以后遇到开放式、多工具、路径无法预定义的任务，再引入模型自主 Tool Calling。

## 12. 当前必须掌握与可以延后

现在必须能解释：

1. Context 是一次请求的状态，不是永久会话对象。
2. Structured Output 之后为什么还要 Java 校验。
3. Planner 与 Executor 的职责区别。
4. 为什么固定流程由 Java 路由。
5. 为什么只有只读路由有限重试，超时不重试。
6. `onEvent()` 与 `emitter.send()` 的区别。
7. Java 来源数据与模型引用之间的可信度区别。

暂时不需要背：

- SseEmitter 和线程池的全部 API。
- Flux 的全部操作符。
- 前端 SSE 文本协议解析代码。
- 每个 Prompt 的完整原文。
- 从零默写所有类。

## 13. 自测题

1. 为什么必须先读取历史，再保存当前用户消息？
2. `.convert()` 成功后为什么还要 `validateResult()`？
3. 置信度为 0.5、意图为 `KNOWLEDGE_SEARCH` 时最终走哪个路由？
4. 普通知识库问题为什么不经过 Planner？
5. Planner 返回 `action=DELETE_DOCUMENT` 时为什么无法执行？
6. 为什么数据库消息写入节点不能直接重试？
7. 节点超时后为什么不马上重试？
8. `eventListener.onEvent()` 是否已经向浏览器发送数据？
9. 复合任务中为什么中间答案不流式展示，但来源事件仍要发送？
10. `sources` 真实存在，是否能证明模型的每句话都被资料支持？

能够不看代码讲清 7 道以上，就可以继续下一阶段。

## 14. 下一步

先根据本文和断点完整走一遍同步接口，再走一遍 SSE 接口。引用编号格式校验已经完成；下一阶段是在不堆叠过多概念的前提下，继续学习“语义忠实度评测”，判断来源内容是否真的支持模型回答。
