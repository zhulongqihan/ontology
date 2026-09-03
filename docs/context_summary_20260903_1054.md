# 项目断点摘要（2026-09-03 10:54）

## 当前目标

继续完成工程与论文的全盘修复、证据核验、论文状态对齐、提交和推送。本文档是当前断点的归档，不覆盖 canonical 状态文件 `docs/审查状态_v0.7.md`。

## Canonical 状态

- 工程仓库：`F:\finalartical\flexible-ontology-reproduction`
- 论文源文件：`F:\finalartical\毕业论文工作区\06_论文写作产物\07_论文源文件\njuthesis_正式初稿`；论文目录不是工程 Git 仓库。
- 当前状态入口：`docs/审查状态_v0.7.md`。
- 当前分支：`main`；HEAD 与 `origin/main` 均为 `26818d5c9358c20603f6056e0c608088e74566b1`；工作区干净。
- 最近提交依次为：`26818d5 Fix experiment consistency field check`、`d6cd293 Record restart idempotency in experiment evidence`、`e14b2df Harden reproducibility evidence and align thesis claims`。

## 已确认的当前证据

- Maven 全量 `clean test package`：84/84 通过，0 failure，0 error。
- 前端生产构建：Vite 构建通过。
- 契约实验：20/20；manifest 的 `source_revision=26818d5`。
- A/B/C 复现套件：A 显式绑定正确而名称推导 baseline 错误；B 中段持久化故障不增加 Run 且 Provider 恢复后 retry 成功；C 同一幂等请求重启后持久化 Run 数为 1，3 个 seed 的契约结果稳定。
- CLI smoke、临时 SQLite HTTP smoke、真实 Playwright 前端绑定操作均已通过；前端截图为 `output/playwright/frontend-binding.png`。
- 论文与实现一致性检查（含实验要求）通过；`git diff --check` 通过。

## 已完成的工程和论文改造

- 工程已加入本体定义 hash/版本身份、运行时 evidence integrity 校验、显式 Model--Ontology 绑定、SQLite schema 12、事务提交后 Trace 标记、跨实例 revision/幂等冲突处理、单次隔离 replay、故障恢复实验和可复核 manifest。
- 论文 `论文初稿.tex`、`chapter/正文.tex`、`chapter/附录.tex` 已对齐当前 schema 12、84 条测试、A/B/C、显式绑定、本体 version/hash、restart idempotency、isolated replay，并降低了无法由证据支持的原系统等价/生产结论。
- 论文个人信息仍为模板占位符（如 `待填写`、`To be completed`），没有凭空补写。

## 历史材料和未解决边界

- `data/flexible-engine.db` 为历史 schema 4 数据，当前审计标记 WARN；其中 `run-c8094404`、`run-19ce6595`、`run-b53adf33` 等记录不具备当前 evidence 完整性，不能计入当前实验。
- 历史 SQLite/JSON 中仍有旧 Provider 名称 `OntologyAssembler`；这些材料保持只读，不能通过改历史数据制造通过。
- 当前 Provider 是 `LocalOntologyProvider` 的 `IN_PROCESS` 实现，不支持远程 RPC、网络超时等价、分布式 HA、权限审计、容量基线或生产业务收益结论。
- Replay 只实现单次、隔离、同一当前本体身份；没有批量或跨版本 replay。
- 当前环境没有 `xelatex`/`latexmk`，所以尚未能证明论文 PDF 编译和视觉版式通过。

## 下一步

1. 复核上述摘要生成后文件状态和最新产物 revision。
2. 按最小风险重新执行或核对最终测试、构建、smoke、实验和论文一致性检查。
3. 如发现真实缺口，只修改根因并重新验证；不得修改历史数据或弱化测试。
4. 最终报告明确提交 hash、远程推送状态、已完成证据和未解决边界。
