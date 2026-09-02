# 柔性引擎与本体化行为复现系统

一个面向硕士毕业论文研究与复现实验的、可本地运行的柔性引擎实现。项目的核心不是把已有实验结果做成网页，而是从可观察的领域行为出发，重新实现一套具有动态 Schema、工作流状态机、本体关系和兼容服务能力的引擎，并提供可操作的后台控制面。当前工程基线以 `docs/系统需求规格_v0.3.md` 为准。

> 当前仓库对应论文项目的“可运行复现系统”版本。它不是原始业务系统源码的复制，也不连接原生产环境。由本项目产生的运行结果统一标记为 `REPRODUCED_SYSTEM_RUN`，只能表述为“本文复现系统的运行结果”，不能表述为原系统生产指标。

## 项目定位

本项目试图回答一个工程复现问题：在不依赖原始业务源码、生产数据库和生产注册中心的前提下，能否以清晰的领域模型和可重复执行的运行时行为，重建柔性引擎的核心能力，并让 Schema、状态流程、本体对象和服务调用都可以被配置、执行和审阅？

复现对象的核心闭环是：

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
- 状态持久化：配置和运行历史保存到 `data/engine-state.json`，重启后重新加载。
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

管理 API 默认监听 `http://127.0.0.1:8787`，只绑定本机地址。健康检查：

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

控制面中的新增模型、动态字段、状态转换、本体关系、服务注册和运行快照都会进入本地引擎状态，而不是只改变浏览器内存。

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
| `POST` | `/api/runtime/execute` | 按模型 Schema 和工作流执行一次运行 |

运行请求示例：

```json
{
  "modelId": "interview-session",
  "contextId": "ctx-demo-001",
  "event": "startInterview",
  "values": {
    "candidateName": "复现样例",
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

- Maven 多模块测试：22/22 通过。
- `contract` 模式：20 条契约规格可执行并生成逐用例产物。
- 相同 seed 的契约运行可生成稳定报告哈希。
- 前端 `npm.cmd run build` 通过。
- 管理 API 的模型注册、字段写入、关系注册、服务注册、运行执行和 JSON 持久化已完成实测。

## 论文数据口径与复现边界

本项目的运行数据必须区分以下两类：

1. `REPRODUCED_SYSTEM_RUN`：本项目源码实际执行产生的复现系统运行结果，可用于说明本复现实现的行为。
2. 原系统历史数据：只有在原始报告、日志、请求响应或其他可核验材料仍然存在时，才能作为原系统事实引用。

本项目不会把缺失的原始报告、用户口述、仿真数据或本地复现结果自动改写成原系统生产指标。论文中使用本项目结果时，应明确“复现系统实验”与“原系统历史事实”的边界。

## 当前限制与演进路线

当前版本是论文级复现系统的可运行基础，不宣称已经对未知的原始系统实现做到 1:1 等价。仍需继续完善的工程能力包括：

- 数据库事务和并发版本控制，替代当前 JSON 文件仓库。
- Schema 迁移规则、字段编辑/删除和版本回滚。
- 运行重试、回滚、幂等和故障注入。
- 请求—响应—Provider—Trace 的多 span 追踪。
- 本体关系装配的运行时调用链和更多兼容场景。
- 将历史报告导入、证据哈希和论文导出纳入独立的证据层，但不与引擎运行结果混写。

详细方向、工程需求和当前进度见：

- [系统需求规格 v0.3](docs/系统需求规格_v0.3.md)
- [领域模型与术语 v0.3](docs/领域模型与术语_v0.3.md)
- [复现方向 v0.2](docs/复现方向_v0.2.md)
- [开发进度 v0.2](docs/开发进度_v0.1.md)
