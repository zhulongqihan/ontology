# ADR-006：Provider 边界与 Trace 观测

日期：2026-09-02
状态：Accepted

## 背景

系统需求要求 Provider 调用能够回溯请求、响应、错误、耗时和 Trace。此前管理运行时只有本体装配 Span，契约实验虽然实际调用了本地 Provider，但 Trace 是手工拼接的三个状态字段。这样的记录只能说明“代码写了一个 Trace 文件”，不能证明文件对应一次真实调用，也无法区分 Provider 未调用、调用失败和返回业务失败。

## 决策

1. `ontology-assembler` 是当前运行时唯一接入的本地 Provider。它通过 `ServiceRegistration` 的 `id/status/endpoint/provider/version` 进入调用边界，并由 `OntologyProvider` 接口的 `LocalOntologyProvider` 实现执行；实际实现仍在同一 JVM 内，因此 Span 的 `transport` 固定为 `IN_PROCESS`。
2. 注册信息中的 `provider` 必须与可用的本地实现匹配；若注册成未接入的远程或未知实现，运行失败并留下 Provider 错误 Span，不能静默回退到本地实现。
3. Provider 调用前构造可审阅的请求 JSON，调用成功后保存实际生成的响应 JSON；异常或 Provider 不可用时保存错误原因，不能把异常改写成成功响应。
4. Provider Span 采用三态语义：`OK` 表示调用完成，`FAILED` 表示已经尝试调用但失败，`SKIPPED` 表示由于没有本体输入或前置阶段失败而没有调用。`SKIPPED` 使用 `skipReason`，不冒充错误。
5. Provider Span 的耗时使用 `System.nanoTime()` 的单调时钟，开始/结束时间使用 ISO-8601 墙上时钟；Trace 同时保留两者对应的时间线、毫秒耗时和 `durationNs` 精度，避免本地快速调用被毫秒取整为不可解释的 0。
6. 契约实验使用核心 `Trace`/`TraceSpan` 对真实 Consumer dispatch、Provider call 和 Response serialization 建立 Span，写入前封存。契约报告不包含时间字段，所以报告结果可以稳定复核；包含真实时间的 Trace 哈希不要求跨次运行稳定。

## 证据与边界

- 管理运行时的 Questionnaire → Subject → Option 用例可以查到 `provider` Span，其 attributes 包括 `serviceId`、`endpoint`、`requestJson`、`responseJson`、`objectCount` 和耗时。
- Provider 关闭的故障用例必须产生 `FAILED` Provider Span，运行状态为 `FAILED`，上下文 revision 不推进。
- 当前不能声称已经实现远程注册中心、网络重试、跨服务 Trace Context 传播或生产 Provider 性能；这些属于下一阶段，不能从 `IN_PROCESS` 证据外推。

## 后果

- 运行证据从“阶段标签”升级为可解释的调用事实，失败也能说明发生在哪个边界。
- 本地实现仍然保持可重复测试和离线运行，不需要伪造外部服务数据。
- 未来接入 HTTP/RPC Provider 时，保留相同的 Span 字段和三态语义，只替换 `OntologyProvider` 实现并增加远程 traceparent/重试等字段；不能改变现有字段含义。
