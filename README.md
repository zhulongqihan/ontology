# 柔性引擎与本体化行为复现系统

这是一个独立实现的、可提交源码的复现系统。它依据当前工作区中的接口契约、静态调用链、领域对象和历史行为材料，复现柔性字段、流程状态、本体对象、关系配置以及兼容服务的可运行行为。

## 重要边界

本项目不是原始业务系统的源码复制，也不连接原生产环境。由本项目产生的数据应标记为 `REPRODUCED_SYSTEM_RUN`，表示“本文复现系统的真实运行结果”，不能表述为原系统生产指标。

## 当前阶段

当前版本的主产品是“柔性引擎控制面”：通过本地管理 API 注册模型、扩展动态 Schema、配置状态工作流、维护本体对象关系、注册本地 Provider，并直接执行引擎运行。实验运行器和 20 条契约规格是辅助验证材料，不是前端的主产品。

当前已提供：

- Java 8 语法/字节码目标的 Maven 多模块骨架。
- 柔性字段、动态记录、工作流状态机和上下文快照。
- Questionnaire、Subject、Option、JobOntologyDetail 与关系组装。
- 本地 Provider/Consumer 兼容服务。
- 20 条契约实验规格文件。
- 可运行的命令行 smoke demo。
- JSON 文件持久化的引擎管理 API：模型、字段、状态转换、本体类型、关系和服务注册。
- 连续运行上下文：同一个 `contextId` 会复用上次运行快照的状态与动态字段。
- React/Vite 中文后台控制面：引擎总览、模型管理、Schema/字段、工作流、本体模型、服务注册和运行调试。

## 构建与运行

```powershell
mvn test
mvn package
java -jar reproduction-app/target/reproduction-app-0.1.0-SNAPSHOT.jar
java -jar reproduction-app/target/reproduction-app-0.1.0-SNAPSHOT.jar contract
java -jar reproduction-app/target/reproduction-app-0.1.0-SNAPSHOT.jar contract experiments/contract-20/contract-20.csv runs/contract-20/custom-seed 20260903
# 或使用统一脚本
./scripts/run-contract.ps1
```

当前开发机使用 JDK 17 和 Maven 3.8.6；源码保持 Java 8 兼容语法。`contract` 模式会执行 20 条契约规格并生成 `runs/contract-20/latest` 下的逐用例运行文件。

启动柔性引擎管理 API：

```powershell
java -jar reproduction-app/target/reproduction-app-0.1.0-SNAPSHOT.jar admin
# 或使用统一启动脚本
./scripts/run-admin.ps1
```

默认监听 `http://127.0.0.1:8787`，状态文件为 `data/engine-state.json`。服务只绑定本机地址，适合复现和调试，不代表原生产注册中心。

## 前端可视化

前端位于 `frontend/`，通过 Vite 代理直接读取和写入管理 API；页面中的运行数据身份明确标记为 `REPRODUCED_SYSTEM_RUN`。先启动上面的 admin API，再启动前端并访问 `http://127.0.0.1:5174/`：

```powershell
Set-Location frontend
npm install
npm run dev
# 生产构建检查
npm run build
```

前端写入的模型和运行快照会持久化到 `data/engine-state.json`；该文件是本地运行产物，不提交为源码基线。`runs/contract-20` 仍保留给契约执行器和回归验证使用。

## 管理 API 主要路由

- `GET /api/overview`：读取引擎版本、资源数量和最近运行。
- `GET/POST /api/models`：查看或注册柔性对象模型。
- `GET /api/models/{id}`：读取模型、Schema 和工作流定义。
- `POST /api/models/{id}/fields`：追加动态字段并递增 Schema 版本。
- `POST /api/models/{id}/transitions`：追加事件状态转换。
- `GET/POST /api/ontology/types`：查看或注册本体对象类型。
- `POST /api/ontology/types/{id}/relations`：注册对象关系。
- `GET/POST /api/services`：查看或注册本地 Provider/Assembler。
- `GET /api/runs`、`POST /api/runtime/execute`：读取运行历史或执行一次引擎。
