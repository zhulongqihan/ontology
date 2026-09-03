# 任务断点摘要（2026-09-03 12:24）

## 当前目标

在现有工程基础上完成“引擎控制面为主、对比平台为辅”的第一轮可用化改造：控制面要能展示真实后端状态、真实运行证据、调用链和知识图谱；论文必须同步描述真实能力及其边界。最终需要完成验证、提交并推送。

## 已确认的基线事实

- 工程目录：`F:/finalartical/flexible-ontology-reproduction`。
- 论文源文件目录：`F:/finalartical/毕业论文工作区/06_论文写作产物/07_论文源文件/njuthesis_正式初稿`，不属于工程 Git 仓库。
- 开始本轮改造前工程 HEAD 为 `d3cd7d9`，远程 `origin/main` 同步，工作区干净。
- 后端已有真实 Run、Trace、Snapshot、Audit、Replay、Rollback、幂等和 SQLite 持久化链路；前端原有管理页和证据页，但缺少面向控制面任务路径的统一入口、知识图谱和诚实的成对运行观察。
- 当前后端没有可执行的固定 `Rigid Mapping Baseline`。因此不能把两个同一柔性引擎的 Run 直接写成“柔性引擎前后提升”。本轮页面明确使用 `Run A / Run B`、`PAIRED OBSERVATION` 和“无固定基线”的边界提示。
- Runtime 图谱来自 `RuntimeRun.ontologyGraph`，静态本体图来自配置数据；二者在页面上区分来源和证据身份。
- 论文和 README 已补充控制面、图谱、成对观察及固定基线尚未接入的限制；论文源文件尚未能在本机用 XeLaTeX/latexmk 编译，环境中未发现对应工具。

## 本轮已修改但尚未提交的工程文件

- `frontend/src/ControlPlaneViews.tsx`：新增控制面任务路径、知识图谱、成对真实 Run 观察、调用链 Waterfall、结构化 Decision Evidence 和运行图谱。
- `frontend/src/App.tsx`：接入知识图谱与对比分析导航和视图，首页接入控制面任务路径。
- `frontend/src/styles.css`：补充控制面、图谱、对比、证据链和响应式样式。
- `docs/控制面改造_v0.1.md`：记录实现边界、数据来源、验证方法和固定基线限制。
- `README.md`：同步入口、架构、运行方式和限制说明。
- 论文源文件外部修改：`chapter/正文.tex` 新增“控制面产品化与可视化证据”小节。

## 已完成的中间验证

- 临时 SQLite 后端使用 `--no-legacy` 启动，Vite 前端使用 5174 端口启动。
- 通过真实 API 创建了两个成功 Run：`run-4ce56650`（`compare-before`）和 `run-73acd768`（`compare-after`），两者均为真实柔性引擎运行记录，均产生 3 个图谱对象和 2 条关系。
- Playwright 已验证首页、静态本体图和 Runtime 图谱；图谱规范化已修复大小写导致的重复节点（`subject/Subject`、`option/Option`）。
- Playwright 已验证对比页面展示状态、耗时、Span、Snapshot、图谱身份、调用链和结构化决策证据；移动端曾验证 390px 宽度无横向溢出。
- 后端此前 Maven 测试为 84 项通过；本轮最终仍需重新执行全量 clean test/package。前端此前构建通过；本轮需在最终修改后重新执行。

## 未完成的下一步

1. 重新打开当前 Playwright 页面，验证最新文案确实为“Run A · 参照记录 / Run B · 待比较记录 / 尚无固定基线”，并重新保存截图。
2. 关闭临时浏览器、Java 后端和 Vite 进程，确认 8787/5174 端口释放。
3. 执行前端生产构建、`mvn clean test package`、论文一致性检查、项目状态审计和 `git diff --check`。
4. 视需要更新 `docs/审查状态_v0.7.md`，使 canonical 状态包含本轮控制面呈现层事实。
5. 检查 diff，提交工程变更并推送 `origin/main`。
6. 在最终提交 hash 下重新生成契约实验/实验套件所需的 source revision 证据，最后再次检查 Git 状态与远程状态。

## 交付边界

- 可以交付：真实 API 驱动的控制面入口、静态/运行时知识图谱、真实 Run 的成对观察、调用链和结构化决策证据、论文同步段落。
- 不能声称：当前已有原系统黑盒等价性、真实生产远程 Provider、固定映射基线下的性能/正确性提升、原始模型私有 COT、或已完成 XeLaTeX PDF 编译。
