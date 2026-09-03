# 断点续作状态摘要（2026-09-03 15:52）

## 当前目标

继续完成 `F:/finalartical/flexible-ontology-reproduction` 工程及其配套论文，使引擎控制面为主、基线对比与知识图谱为辅的系统能够被真实运行、验证和写入论文；不得把本地模拟、本项目自定义行为或历史产物包装成原生产系统事实。

## 当前事实源

- 工程当前状态基准：`docs/审查状态_v0.7.md`、当前源码/测试、`docs/实验证据/20260903_baseline_flexible_final/manifest.json` 与 `report.json`。
- 可执行一致性检查：`scripts/audit_project_state.py`、`scripts/check-paper-consistency.ps1 -RequireExperiment`。
- 论文源文件：`F:/finalartical/毕业论文工作区/06_论文写作产物/07_论文源文件/njuthesis_正式初稿/`，该目录不是 Git 仓库，不能提供论文独立提交 hash。
- 历史 SQLite `data/flexible-engine.db` 只作只读历史材料，schema 4/revision 12 与当前 schema 14 不一致，不能作为当前复现证据。

## 已完成的实现与验证

1. `EngineRuntimeService` 已提供独立 `RigidMappingBaseline` 与 Flexible Engine 配对执行；两侧在一次持久化边界内保存，具备互相引用的 `comparisonId`/pair 元数据，失败时恢复原状态。
2. SQLite 已有迁移 013/014，支持纳秒级耗时、执行模式、对比元数据及配对完整性校验；schema 版本为 14。
3. 已覆盖模型/服务/API/持久化/回滚等测试；最近全量测试为 `89 tests, 0 failures, 0 errors, 0 skipped`。
4. 最近 `mvn.cmd -q clean package` 通过；前端 `npm.cmd run build` 通过（JS 249.03 kB、gzip 76.57 kB；CSS 38.76 kB、gzip 7.44 kB）。
5. 已完成真实浏览器 smoke：对比页调用真实后端，显示 baseline/flexible 两侧状态、Trace、Snapshot、Audit、持久化状态和结构化决策证据；静态本体图谱显示 3 节点/2 条有向关系；移动端 390px 无横向溢出。
6. A/B/C/D 契约与对比实验产物已归档并入 Git。D 组为每个案例 12 对配对执行：
   - `questionnaire-basic`：baseline/flexible 均 12，改进 0；p50 为 487100/1413700 ns，差值 +821600 ns。
   - `questionnaire-dynamic-field`：baseline 0、flexible 12，改进 12；p50 为 466700/1177600 ns，差值 +756500 ns。
   - `questionnaire-knowledge-graph`：baseline/flexible 均 12，改进 0；p50 为 437400/1370000 ns，差值 +847300 ns。
   - 这些结果仅支持“在本项目固定基线和本地配置下，动态字段案例的适应性差异”；不能支持生产性能、原系统等价或通用性能提升。
7. 论文正文、摘要、附录与补充材料已按 schema 14、89 tests、A/B/C/D 和上述边界更新；`check-paper-consistency.ps1 -RequireExperiment` 最近通过。
8. XeLaTeX/latexmk 在当前环境不可用，因此尚未声称论文 PDF 已编译通过。

## Git 状态

- 工程分支：`main`。
- `HEAD=origin/main=c7db96a`，最近提交：
  - `badc7fc`：加入证据门控的 baseline comparison 和论文验证。
  - `c7db96a`：归档最终配对实验证据。
- 上次最终检查工作区干净，`git diff --check` 通过。

## 当前未决事项

- 对比页默认输入不含 `subjects`，因此运行时知识图谱默认可能为空；这是诚实的空状态，不应通过硬编码结果伪造。需要补充清晰的可复现操作说明，告诉用户先在 Schema 中加入 `subjects` JSON 字段，再使用带 subjects/options 的输入观察运行时图谱。
- 需要重新执行断点审计，确认 canonical 文档、当前 Git HEAD、实验 manifest、论文一致性和历史材料警告没有漂移。
- 若为可用性说明或前端提示做小幅修改，必须重新构建、运行 smoke、执行一致性检查、`git diff --check`，再提交并推送。

## 下一步

1. 运行状态重同步审计和 Git/实验/论文一致性核对。
2. 在 README/控制面说明中补充从 Schema 到运行时知识图谱的最短可复现路径，明确空图谱与静态图谱的差异。
3. 如有必要补充前端帮助文案/示例，但不改变测试所依赖的默认数据语义。
4. 重新验证前端构建、后端全量测试、package、实验与论文检查；只在发生修改时产生新提交并推送。

## 证据边界

当前系统可声称：一个可运行、可持久化、可审计、具备版本/迁移/对比/图谱展示的单机重构原型，以及在固定基线和明确本地实验协议下的观察结果。当前系统不可声称：原生产系统黑盒等价、远程 Provider 真实性、线上业务指标、通用性能提升、真实 COT 或已编译的最终论文 PDF。

