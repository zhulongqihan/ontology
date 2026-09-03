# 断点上下文摘要（2026-09-03 17:15）

## 当前任务

用户要求在既有全盘改造基础上再次独立检查，确认没有遗留问题后继续迭代开发。当前已确定的下一步方向是：以引擎控制面为主，提升系统可用性，让实验结果、适应性提升、调用链和知识图谱证据能够在系统内直接查看；不能把离线实验产物冒充运行时能力，也不能虚构原生产系统证据。

## canonical 状态来源

- 工程根目录：`F:/finalartical/flexible-ontology-reproduction`。
- 论文根目录：`F:/finalartical/毕业论文工作区/06_论文写作产物/07_论文源文件/njuthesis_正式初稿`，不是 Git 仓库。
- 工程当前 canonical 审查状态：`docs/审查状态_v0.7.md`。
- 当前实验 canonical 证据：`docs/实验证据/20260903_evolution_capability_final/manifest.json`、`report.json`、`summary.md`。
- 当前实验源代码提交记录在 manifest 中为 `6578b744f67a3ff35c0f21ddfed4c40cb0e0c429`；之后的 `c74ee2032394ac73c4debe47e2d930860a758530` 只增加了文档和证据产物，没有改变实验涉及的运行时语义。

## 已确认事实

- 工程分支为 `main`，远端为 `git@github.com:zhulongqihan/ontology.git`，上一轮结束时 `HEAD` 与 `origin/main` 均为 `c74ee2032394ac73c4debe47e2d930860a758530`，工作区干净。
- `mvn.cmd -q clean test` 通过，Surefire 共 91 个测试，失败 0、错误 0、跳过 0。
- `mvn.cmd -q clean package` 通过。
- 前端生产构建通过：31 modules，JavaScript 249.33 kB（gzip 76.67 kB），CSS 38.76 kB（gzip 7.44 kB）。
- 契约实验 `runs/contract-20/validation-20260903`：20 次全部通过，失败 0。
- 最终 E 阶段实验 `docs/实验证据/20260903_evolution_capability_final/`：`reproduction-abcde`，3 个场景、36 个观测；baseline 12/36、flexible 36/36、改进 24/36、适应性提升 66.67%、证据完整 36/36。
- 论文一致性脚本 `scripts/check-paper-consistency.ps1 -RequireExperiment` 通过；`git diff --check` 上一轮通过。
- 默认 jar smoke 通过，输出 `ENGINE_RUNTIME_RESULT`，`workflow.state=IN_INTERVIEW`，validationErrors 为空，问卷 detail 为 SUCCESS。
- `EngineAdminServerTest` 的真实进程内 HTTP 测试通过。
- 论文已同步 91 个测试、A/B/C/D/E 实验及 E 演化能力矩阵；D 实验最新本地 p50 为 basic 1,286,100 ns、dynamic 1,105,800 ns、graph 1,271,300 ns，均不支持性能提升结论。

## 证据边界

- 没有原生产系统同版本黑盒输入、输出和错误样本，因此不能声称原系统黑盒等价或兼容率。
- `RigidMappingBaseline` 是仓库内独立固定比较器，不是原生产系统。
- Provider 是本地 `IN_PROCESS`，不是远程 RPC。
- E 阶段的 66.67% 是三个场景的聚合适应性提升观察，不是普遍兼容率、生产 KPI 或业务收益。
- 没有 XeLaTeX/latexmk，因此不能声称论文 PDF 已完成编译和视觉验收。

## 已知但未篡改的遗留边界

运行 `scripts/audit_project_state.py` 会给出 WARN：历史 `data/flexible-engine.db` schema 4、revision 12，低于当前 loader 预期 >=14；历史 runtime 行 `run-c8094404`、`run-19ce6595`、`run-b53adf33` 不完整；历史兼容投影仍出现旧 Provider 名称 `OntologyAssembler`，且 `data/engine-state.json` 为旧状态。这些是历史数据警告，当前实验排除了它们；不能直接迁移、覆盖或修改历史库来消除警告，后续应作为明确的 legacy-data 边界处理。

## 上一轮主要实现

- `ReproductionExperimentSuite` 增加 E 演化能力矩阵实验，覆盖新增字段、字段重命名迁移、动态关系图谱，并记录 hash、migration、trace 生命周期和证据完整性。
- `ReproductionApplication` 实验命令打印 `suite_id=reproduction-abcde`。
- `EngineAdminServiceTest` 增加显式字段重命名迁移后的柔性适应对比测试。
- README、审查状态、实验协议、论文补充和一致性脚本已同步。

## 本轮待执行

1. 按 `reconcile-project-state` 流程重新检查 Git、canonical 文档、实验 manifest/report、状态审计、论文一致性和差异格式，确认无新的 P0/P1 冲突。
2. 检查控制面当前是否已经暴露实验/对比证据；若缺失，优先实现从真实持久化运行记录派生的对比历史/证据查询，不读取静态报告冒充实时状态。
3. 为新增控制面能力补自动化测试，完成全量测试、package、前端构建、契约实验、smoke、重启/坏数据等相关验证。
4. 若测试总数变化，必须同步论文、审查文档和一致性脚本，不能留下数字漂移。
5. 完成 `git diff --check`、状态检查、提交并推送；最终明确仍无法证明的 legacy 数据和论文边界。
