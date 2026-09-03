# 断点续作摘要（2026-09-03 16:45）

## 当前目标

继续执行系统与论文的下一阶段，产出可复核的真实实验数据，重点量化柔性引擎相对固定映射基线在动态字段、字段演化/迁移等场景中的适应性提升。所有结论必须基于当前代码真实运行、独立 SQLite 和提交后的实验产物；不得虚构原生产系统或线上指标。

## 工程与论文位置

- 工程：`F:\finalartical\flexible-ontology-reproduction`
- 论文：`F:\finalartical\毕业论文工作区\06_论文写作产物\07_论文源文件\njuthesis_正式初稿`
- 工程远程：`github.com:zhulongqihan/ontology.git`，分支 `main`
- 论文目录不是 Git 仓库，因此论文修改不能以独立提交 hash 证明。

## 已确认的工程状态

- 最近已推送提交：`73ea442`，内容为管理端 HTTP Server 关闭时回收 `ExecutorService`。
- 上一个核心修复提交：`fd3ad8e`，禁止同一 `comparisonId` 搭配不同请求时静默返回旧比较证据。
- 当前已有测试总数：90；上次 `mvn.cmd -q clean test` 全部通过。
- 上次 `mvn.cmd -q package` 全部通过。
- 上次 `npm.cmd run build` 通过，前端 31 个模块构建成功。
- 上次契约实验：20/20 通过。
- 上次真实 Playwright smoke 已验证比较页面、知识图谱展示、移动端无横向溢出；截图保存在 `output/playwright/`。

## 当前实验基线

上一阶段 D 实验的最终跟踪证据目录：`docs/实验证据/20260903_baseline_flexible_final/`。

- manifest 的源代码 revision 为 `fd3ad8e`；后续 `0e43432`、`73ea442` 只改变证据门禁/生命周期收尾，不改变 D 实验语义。
- D 为 3 个场景、每个场景 12 对独立 SQLite 配对运行：
  - 基础问卷：baseline p50 `419800 ns`，flexible p50 `1302300 ns`，差值 `770300 ns`。
  - 动态字段：baseline `0/12`，flexible `12/12`；p50 分别 `402600 ns`、`1218900 ns`，差值 `736500 ns`。
  - 知识图谱：baseline p50 `450500 ns`，flexible p50 `1375300 ns`，差值 `952700 ns`。
- 这些数据只能支持本地重构系统中固定映射基线与柔性引擎的工程观察，不能支持原生产系统等价性、远程 Provider 性能或业务收益结论。

## 已完成的证据与门禁

- `scripts/audit_project_state.py`：只读状态一致性审计；已识别历史 `data/flexible-engine.db` 为旧 schema/旧 provider 数据，不得作为当前证据。
- `scripts/check-paper-consistency.ps1`：读取提交的最终实验报告，校验 A/B/C/D 结果、动态字段计数、论文 D 表 p50 与 JSON 一致；当前为 PASS。下一阶段需要加入 E 校验，并将 canonical report 指向 E 阶段目录。
- `docs/审查状态_v0.7.md`：当前工程被定性为可运行、可审查的单节点重构系统，尚非原生产黑盒复现；可信度中等。
- 论文已补充系统架构、版本迁移、事务/幂等、Trace/Snapshot/Audit、实验限制等内容；XeLaTeX/latexmk 不可用，尚不能声称已编译 PDF。

## 下一步执行计划

1. 独立检查 `RigidMappingBaseline`、Schema 字段重命名/迁移 API 和现有测试，确定 E 场景不共享柔性实现的错误假设。
2. 在 `ReproductionExperimentSuite` 增加 E：演化能力矩阵，使用全新临时 SQLite 和真实服务调用，至少覆盖动态字段与字段重命名/迁移；每个场景 12 对配对运行。
3. 输出 baseline/flexible 通过率、适应性增益、可比对数量、配置/输入 hash、Trace/提交证据完整性和高精度耗时；明确“提升”指适应性/成功率，不默认指速度。
4. 增加自动化测试，覆盖 E 的关键配对语义；如测试总数改变，同步所有证据和论文表述。
5. 生成新的 `docs/实验证据/20260903_evolution_capability_final/`，保留上一阶段 D 目录，不覆盖历史产物。
6. 更新一致性脚本、状态文档、README/实验说明和论文正文/附录，加入 E 表格及限制，所有数字从最终 JSON 读取。
7. 执行全量测试、package、前端 build、契约实验、E 实验、论文一致性检查、`git diff --check`，提交并推送。

## 重要限制

- 没有原生产系统同版本黑盒输入/输出与错误样本，因此不能把当前契约实验称为原系统兼容率。
- Provider 是本地 in-process fake provider；只能验证协议与故障语义，不能代表远程网络服务。
- 不能使用旧 SQLite、旧 JSON、历史报告或忽略产物替代新运行证据。
- 不应输出原始模型 Chain-of-Thought；只输出结构化决策证据、调用链、Trace 和状态转移。
