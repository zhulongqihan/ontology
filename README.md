# 柔性引擎与本体化平台

一个面向硕士毕业论文研究的、可本地运行的柔性引擎与本体化平台。系统从零实现动态 Schema、工作流状态机、本体对象关系、兼容服务和运行时控制面，论文围绕这套系统的设计、实现、机制和实验展开。当前工程基线以 `docs/系统需求规格_v0.3.md` 为准。

> 研究边界：这是独立实现的柔性引擎与本体化平台，不连接任何外部生产环境，也不把外部历史指标写入本地数据。论文中的实验结果应引用本平台的源码版本、模型版本、运行 ID 和 Trace，而不是脱离运行记录单独填写数字。

## 项目定位

本项目要解决的是一个工程系统问题：如何构建一个不依赖外部生产设施、可以配置和扩展的柔性引擎，并让 Schema、状态流程、本体对象和服务调用都可以被定义、执行、追踪和审阅。

平台的核心能力闭环是：

```text
模型注册
    ↓
动态 Schema / 字段版本
    ↓
运行时数据校验
    ↓
事件驱动的状态迁移
    ↓
本体对象与关系装配
    ↓
Provider / Consumer 兼容调用
    ↓
运行快照、Trace ID 与持久化状态
```

这里的“后台管理平台”是引擎的控制面：通过它定义引擎如何运行，而不是展示预先写死的实验数据。

## 已实现能力

- 柔性字段：`STRING`、`INTEGER`、`DECIMAL`、`BOOLEAN`、`JSON`、`OBJECT`。
- 动态 Schema：字段可在运行中追加，Schema 版本随配置变化递增。
- 数据校验：必填校验、类型校验和错误信息保留。
- 工作流运行时：初始状态、事件、状态转换和非法事件拒绝。
- 连续上下文：相同 `contextId` 会继承上次运行快照的状态和字段，实现跨请求迁移。
- 本体模型：Questionnaire、Subject、Option 以及固定属性、动态属性和对象关系。
- 本地兼容层：Provider / Consumer 的可运行本地实现，明确使用 `local://` 地址。
- 管理 API：模型、字段、转换、本体类型、关系和服务注册的读取与写入。
- 运行证据：每次运行固定引擎、Schema、Workflow 版本，保存独立 RuntimeContext、before/after Snapshot、SHA-256、Trace Span、幂等记录和错误原因。
- 状态持久化：配置和运行历史默认保存到 SQLite `data/flexible-engine.db`，运行域事实同步写入 `runtime_context`、`runtime_run`、`execution_snapshot`、`trace`、`trace_span`、`audit_event` 和 `idempotency_record` 表，重启后重新加载。
- 控制面前端：引擎总览、模型管理、Schema/字段、工作流、本体模型、服务注册和运行调试。
- 契约回归：20 条接口契约规格、逐用例结果、Trace、报告和稳定哈希输出。

## 系统架构

```text
┌──────────────────────────────────────────────────────────────┐
│                 React / Vite Engine Control Plane            │
│  Overview · Models · Schema · Workflow · Ontology · Runtime   │
└───────────────────────────────┬──────────────────────────────┘
                                │ HTTP JSON
┌───────────────────────────────▼──────────────────────────────┐
│                       engine-admin-api                        │
│  Registry · JSON Repository · Runtime Execute · CORS · Trace  │
└───────────────┬──────────────────┬────────────────┬───────────┘
                │                  │                │
┌───────────────▼──────┐ ┌─────────▼─────────┐ ┌────▼────────────┐
│ flexible-engine-core │ │   ontology-core   │ │ compatibility-  │
│ Schema · Record · FSM│ │ Object · Relation │ │ adapter          │
└──────────────────────┘ └───────────────────┘ └─────────────────┘
                │
┌───────────────▼──────────────────────────────────────────────┐
│ reproduction-app · experiment-runner · local state / reports  │
└───────────────────────────────────────────────────────────────┘
```

## 模块说明

| 模块 | 职责 | 关键代码 |
| --- | --- | --- |
| `flexible-engine-core` | 动态记录、字段定义、类型校验、工作流执行 | `FlexibleEngine`、`DynamicRecord`、`WorkflowExecutor` |
| `ontology-core` | 本体对象、关系和对象装配 | `Questionnaire`、`Subject`、`Option`、`OntologyAssembler` |
| `compatibility-adapter` | 本地 Provider / Consumer 兼容行为 | `QuestionnaireServiceProvider` |
| `engine-admin-api` | 引擎管理 API、状态仓库和运行时入口 | `EngineAdminServer`、`EngineAdminService` |
| `engine-persistence` | SQLite 事务持久化、JSON 迁移和状态写入审计 | `SqliteEngineStateRepository` |
| `experiment-runner` | 20 条契约规格的可重复回归执行 | `ContractExperimentRunner` |
| `reproduction-app` | fat jar 启动入口 | `ReproductionApplication` |
| `frontend` | 中文后台控制面 | `App.tsx`、`api.ts` |

## 快速运行

环境要求：JDK 17、Maven 3.8+、Node.js 18+。源码使用 Java 8 兼容语法和字节码目标，可在当前 JDK 17 上构建。

### 1. 构建并启动管理 API

```powershell
Set-Location F:\finalartical\flexible-ontology-reproduction
mvn.cmd test
mvn.cmd package
java -jar reproduction-app\target\reproduction-app-0.1.0-SNAPSHOT.jar admin
```

也可以使用：

```powershell
./scripts/run-admin.ps1
```

管理 API 默认监听 `http://127.0.0.1:8787`，只绑定本机地址；默认将状态写入 `data/flexible-engine.db`。如果数据库首次为空且存在旧的 `data/engine-state.json`，启动时会先导入旧状态。健康检查：

```powershell
Invoke-RestMethod http://127.0.0.1:8787/api/health
```

### 2. 启动中文控制面

另开一个终端：

```powershell
Set-Location F:\finalartical\flexible-ontology-reproduction\frontend
npm.cmd install
npm.cmd run dev
```

打开 [http://127.0.0.1:5174/](http://127.0.0.1:5174/)。

控制面中的新增模型、动态字段、状态转换、本体关系、服务注册和运行快照都会进入本地引擎状态，而不是只改变浏览器内存。SQLite 写入在事务中完成，并保留状态写入审计记录；旧 JSON 只作为迁移入口，不再是默认运行仓库。

## 管理 API

| 方法 | 路由 | 用途 |
| --- | --- | --- |
| `GET` | `/api/health` | 管理 API 健康状态 |
| `GET` | `/api/overview` | 引擎版本、能力、资源数量和最近运行 |
| `GET/POST` | `/api/models` | 查询或注册柔性对象模型 |
| `GET` | `/api/models/{id}` | 查询模型、Schema 和工作流 |
| `POST` | `/api/models/{id}/fields` | 写入动态字段并递增 Schema 版本 |
| `POST` | `/api/models/{id}/transitions` | 写入事件状态转换 |
| `GET/POST` | `/api/ontology/types` | 查询或注册本体类型 |
| `POST` | `/api/ontology/types/{id}/relations` | 注册对象关系 |
| `GET/POST` | `/api/services` | 查询或注册本地 Provider/Assembler |
| `GET` | `/api/runs` | 查询运行历史 |
| `GET` | `/api/runs/{id}` | 查询完整运行、前后快照和 Trace |
| `GET` | `/api/runs/{id}/trace` | 查询 Trace Span 时间线 |
| `GET` | `/api/contexts` | 查询运行上下文 |
| `GET` | `/api/contexts/{id}` | 查询上下文当前状态 |
| `GET` | `/api/audit-events` | 查询配置审计事件 |
| `POST` | `/api/runtime/execute` | 按模型 Schema 和工作流执行一次运行 |

运行请求示例：

```json
{
  "modelId": "interview-session",
  "contextId": "ctx-demo-001",
  "event": "startInterview",
  "values": {
    "candidateName": "平台运行样例",
    "score": 92
  }
}
```

同一个 `contextId` 再提交 `submitEvaluation`，运行会从 `IN_INTERVIEW` 继续迁移到 `COMPLETED`，并保留之前的动态字段。

## 回归验证

```powershell
Set-Location F:\finalartical\flexible-ontology-reproduction
mvn.cmd test
mvn.cmd package
java -jar reproduction-app\target\reproduction-app-0.1.0-SNAPSHOT.jar contract
```

当前验证基线：

- Maven 多模块测试：25/25 通过。
- `contract` 模式：20 条契约规格可执行并生成逐用例产物。
- 相同 seed 的契约运行可生成稳定报告哈希。
- 前端 `npm.cmd run build` 通过。
- 管理 API 的模型注册、字段写入、关系注册、服务注册、运行执行、SQLite 持久化和旧 JSON 迁移已完成实测。

## 论文与系统边界

论文以本平台为研究对象，重点说明柔性建模、本体化表达、事件驱动运行时、配置持久化和可观测机制。系统运行数据必须至少关联以下信息：

- 源码提交和构建版本；
- 模型、Schema、Workflow 和本体关系版本；
- `runId`、`contextId`、`traceId`、输入、输出和错误；
- 运行时间、持久化位置和可重复执行参数。

本平台产生的运行结果用于解释和评价本平台本身，不自动代表任何外部业务系统的生产指标。若论文引用外部历史材料，应在论文中单独标注来源；若论文评价本平台，则直接使用本平台生成且可通过运行 ID 追溯的实验记录。

## 当前限制与演进路线

当前版本是论文级柔性引擎与本体化平台的可运行基础，但仍不是完整生产级实现。已完成运行域证据闭环的第一切片，仍需继续完善的工程能力包括：

- SQLite 已建立运行域规范化表和事务投影；模型、Schema、Workflow、本体和服务配置的规范化表及从投影读取仍待继续。
- Schema 迁移规则、字段编辑/删除和版本回滚。
- 运行重试、可执行回滚、故障注入和更细粒度的并发冲突恢复。
- 请求—响应—Provider 的真实多服务调用链和更多 Trace 语义。
- 本体关系装配的目标类型、基数约束执行和更多兼容场景。
- 将运行快照、Trace、审计日志和论文导出纳入独立的实验支撑层，并与引擎领域数据保持清晰边界。

历史 JSON/SQLite 记录若生成于证据字段加入前，会保留为历史记录但显示为“旧记录/证据不完整”，不会被回填为当前运行结果。

详细方向、工程需求和当前进度见：

- [系统需求规格 v0.3](docs/系统需求规格_v0.3.md)
- [领域模型与术语 v0.3](docs/领域模型与术语_v0.3.md)
- [系统总体方向](docs/复现方向_v0.2.md)
- [开发进度 v0.2](docs/开发进度_v0.1.md)
