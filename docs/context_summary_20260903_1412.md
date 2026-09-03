# 项目断点总结（2026-09-03 14:12）

## 当前目标

用户已授权对工程与论文进行全盘改造并完成验证、提交和推送。当前重点是把“控制面为主、对比平台为辅”的系统从仅有配对观测升级为真实的固定映射基线与 Flexible Engine 成对执行，并把可复核证据写入实验产物和论文。

## 独立基线事实

- 工程目录：`F:/finalartical/flexible-ontology-reproduction`。
- 论文源文件目录：`F:/finalartical/毕业论文工作区/06_论文写作产物/07_论文源文件/njuthesis_正式初稿`。
- 最近已推送 HEAD：`db539af`；此前工作区干净，远程与 HEAD 一致。
- 历史 `data/flexible-engine.db` 仅允许只读检查，schema 4 与当前迁移链不一致，不能冒充当前复现证据。
- 此前已完成 Maven 84/84、契约实验 20/20、前端构建和 Playwright 控制面真实操作验证；这些结论对应当时的 `db539af`，不等于本轮新增改造已经通过。

## 本轮已修改但尚未提交

### 运行时证据模型

- `TraceSpanRecord`、`TraceRecord`、`RuntimeRun` 增加 `durationNs`，避免子毫秒执行被毫秒整除为 0。
- `RuntimeRun` 增加 `executionMode`、`comparisonId`、`pairedRunId`、`caseId`、`inputSha256`、`configurationSha256`。
- `EngineRuntimeService` 增加真实成对执行入口、固定映射基线执行路径、对比结果和配对元数据。
- 新增 `RigidMappingBaseline`，独立于 Flexible Engine，支持问卷和访谈最小固定映射行为，并能拒绝基线未声明的字段。
- `EngineAdminService` 和 `EngineAdminServer` 增加 `/api/comparisons/execute`。
- 前端 API 与控制面已能读取高分辨率耗时及对比元数据；控制面耗时格式化已改为 ns/µs/ms，0 ns 显示为 `0 ns`。

### 持久化

- 新增 `013_high_resolution_timing.sql`：为 run、trace、trace_span 增加 `duration_ns`。
- 新增 `014_comparison_execution_metadata.sql`：为 runtime_run 增加执行模式、配对、案例和哈希元数据。
- `SqliteEngineStateRepository` 已接入迁移和读写字段，但当前存在真实回归：`runtime_run` INSERT 报 SQLite `33 values for 36 columns`。需要先核对列名与占位符数量并用 `apply_patch` 修复。
- 现有持久化测试中的 schema 断言已从 12 改到 13，需继续改到 14，并验证新字段重启恢复。

## 最近失败

执行 `mvn -q test -pl engine-admin-api,engine-persistence -am` 时，持久化模块全部在初始化阶段失败，原因是 `SqliteEngineStateRepository` 的 `runtime_run` INSERT 列数与 values 占位符数不一致；不是通过删测试、放宽断言或修改历史数据解决。

## 立即下一步

1. 只读检查 `SqliteEngineStateRepository.java` 的 runtime_run INSERT，精确统计列名和 `?` 数量，用 `apply_patch` 修复。
2. 运行 `mvn -q test -pl engine-persistence -am`，再运行 admin API 相关测试。
3. 补充固定映射基线单元测试、成对执行集成测试、重复 comparisonId 幂等测试、基线拒绝动态字段测试。
4. 检查 replay/retry 是否保留执行模式且不错误复用 comparisonId。
5. 更新状态审计脚本、架构/实验协议文档和论文中的 schema/耗时/基线表述。
6. 扩展实验套件，生成真实基线 vs Flexible Engine 的原始 JSON/CSV/SVG 证据；不把示例数字写成外部业务指标。
7. 让前端控制面调用真实 comparison API，明确区分正式基线配对、历史双运行配对和无基线状态。
8. 执行 Maven 全量测试、clean package、契约实验、前端生产构建、持久化恢复/坏数据/并发/幂等、Playwright smoke、论文一致性检查和 `git diff --check`。
9. 最后检查 diff、提交并推送，回报提交 hash 和推送状态。

## 证据边界

- 当前尚无原生产系统同版本黑盒输入/输出样本，因此不能声称原系统黑盒等价性、兼容率、生产性能或外部业务提升。
- 可以声称的方向是：在仓库可复现的固定映射基线与当前 Flexible Engine 案例上，比较状态、错误、耗时、调用链、快照和图谱证据。
- “COT”不写入或伪造内部思维链；系统展示结构化 Decision Evidence、事件、字段映射、Provider span 和审计链。
