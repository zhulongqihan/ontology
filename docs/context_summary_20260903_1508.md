# 断点续作摘要（2026-09-03 15:08）

## 当前目标

用户已授权执行完整改造：以引擎控制面为主、固定基线对比平台为辅，补齐代码、自动化测试、真实实验、前端证据视图和论文内容，并最终验证、提交、推送。必须保持证据边界：当前实现是本地重构系统与独立固定映射基线的可复现实验，不得声称原生产系统黑盒等价、远程 Provider、生产业务指标或统计显著性。

## 已完成但尚未最终交付

### 工程实现

- `engine-admin-api/src/main/java/cn/finalartical/reproduction/admin/TraceSpanRecord.java`、`TraceRecord.java`：增加纳秒级 `durationNs`。
- `engine-admin-api/src/main/java/cn/finalartical/reproduction/admin/RuntimeRun.java`：增加 `executionMode`、`comparisonId`、`pairedRunId`、`caseId`、`inputSha256`、`configurationSha256`、`durationNs`。
- `engine-admin-api/src/main/java/cn/finalartical/reproduction/admin/EngineRuntimeService.java`：增加 `RIGID_MAPPING_BASELINE` 独立执行路径、成对 comparison、幂等复用、元数据和纳秒计时；Flexible Engine 不再使用对象 `toString()` 生成配置哈希。
- 新增 `engine-runtime/.../RigidMappingBaseline.java`：固定字段/状态/关系映射基线，未调用 Flexible Engine。
- `EngineAdminServer.java` 新增 `POST /api/comparisons/execute`；`EngineAdminService.java` 暴露 comparison；前端 `api.ts` 增加对比类型/API。
- SQLite 迁移 `013_high_resolution_timing.sql`、`014_comparison_execution_metadata.sql`；仓储读写、重启恢复、坏数据校验同步更新到 schema 14。
- `frontend/src/ControlPlaneViews.tsx` 增加真实“执行基线 + Flexible Engine”入口和 paired evidence；`App.tsx` 接线；`styles.css` 增加响应式布局。

### 测试和实验

- Maven 最近全量测试已通过：89 tests，0 failures，0 errors，0 skipped（新增成对投影失败原子回滚测试后）。
- 新增 comparison service、SQLite persistence、HTTP endpoint 测试；验证独立基线、动态字段失败/成功差异、pair 元数据、哈希、重启和高分辨率计时。
- `ReproductionExperimentSuite.java` 已加入 D：三个 questionnaire case，每个 12 个成对 trial，独立临时 SQLite，输出原始 observation、`result.json`、`summary.csv`、SVG 和 runtime knowledge graph。
- 已验证的中间产物 `runs/reproduction-suite/archive/20260903_baseline_flexible_v2/D/result.json`：
  - basic：baseline/flexible 均 12/12 成功，改进 0；Flexible p50 比 baseline 高 406700 ns。
  - dynamic-field：baseline 0/12、Flexible 12/12，改进 12；这是当前唯一明确的 outcome advantage。
  - knowledge-graph：baseline/flexible 均 12/12 成功，改进 0；Flexible p50 比 baseline 高 569300 ns。
  - 三个 case 的输入哈希、基线配置哈希、Flexible 配置哈希稳定性均为 true。
- `scripts/check-paper-consistency.ps1` 已增加 D 实验要求，并已同步当前 `89 条`测试数字。

### 文档和论文

- `README.md`、`docs/审查状态_v0.7.md`、`docs/论文补充_系统复现与工程验证_v0.1.md` 已更新 schema 14、88 tests、D 实验、固定基线边界和证据说明。
- 新增 `docs/基线对比实验协议_v0.1.md`。
- 论文目录：`F:\finalartical\毕业论文工作区\06_论文写作产物\07_论文源文件\njuthesis_正式初稿`。
  - `chapter/正文.tex`、`chapter/附录.tex`、`论文初稿.tex` 已补充 D 实验、对照协议、哈希/纳秒计时、88 tests 和限制性结论。
  - 需要重新搜索旧的 87、84、schema 12 和 A/B/C 残留，并在最终实验数值变化时同步论文表格。

## 当前运行状态

- 后端真实 smoke server：`java -jar ... admin 0 output/playwright/compare-smoke-20260903.db --no-legacy`，session id `91510`，端口 `55070`。
- 前端 Vite dev server：代理到 `http://127.0.0.1:55070`，端口 `5174`，session id `73337`。
- Playwright 已打开 `http://127.0.0.1:5174/` 并进入“对比分析”；已点击“执行基线 + Flexible Engine”，需要继续 snapshot/find，保存截图到 `output/playwright/comparison-baseline-flexible.png`，再做 390x844 移动端无横向溢出验证。

## 必须继续执行的顺序

1. Playwright snapshot/find/screenshot/mobile；停止或清理本轮临时服务。
2. 修正 paper consistency 脚本旧数字并搜索论文/文档残留。
3. `git diff --check`、状态和差异审查。
4. `mvn -q clean test`，解析 89/89；`mvn -q clean package`；`npm run build`。
5. 先提交当前实现/文档/论文，取得 implementation commit hash 并推送。
6. 用该实现 hash 运行最终 D 对比实验，保存最终可追溯产物；如纳秒计时与论文表格不同，按最终产物修订论文并做第二个论文同步提交。
7. 运行 `check-paper-consistency.ps1 -RequireExperiment`、`audit_project_state.py`、最终 `git diff --check`、Git 状态、远程状态。
8. 最终报告必须列出真实修复、89/89 测试、实验观察、尚未验证边界、文件清单、commit hash 和 push 状态；明确不能声称原生产黑盒等价、远程服务、COT 或性能提升。

## 证据边界

- 当前最强可写结论：在仓库内定义的、独立的固定映射基线下，Flexible Engine 对动态字段场景保持成功，而基线失败；该结论来自 3 个 case × 12 paired trials 的当前实验产物。
- 当前不可写结论：原生产系统兼容率、线上吞吐/延迟提升、远程 Provider 等价、统计显著性、真实客户端已收到/渲染 Trace、原系统黑盒行为完全复现。
- 历史 `data/flexible-engine.db` 只读检查；schema 4/旧 provider 是历史证据，不得混入当前 schema 14 的验证。
