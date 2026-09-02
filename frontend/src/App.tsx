import { useMemo, useState } from 'react'
import {
  contractCases,
  ontologyNodes,
  runMeta,
  traceSteps,
  workflowStates,
  type ContractCase,
  type ContractStatus,
} from './data'

type ViewKey = 'overview' | 'contracts' | 'flexible' | 'ontology' | 'runs'

const navItems: Array<{ key: ViewKey; label: string; detail: string }> = [
  { key: 'overview', label: '实验总览', detail: '当前运行与闭环' },
  { key: 'contracts', label: '契约矩阵', detail: '20 条行为场景' },
  { key: 'flexible', label: '柔性引擎', detail: '字段、流程、快照' },
  { key: 'ontology', label: '本体模型', detail: '对象与关系' },
  { key: 'runs', label: '运行记录', detail: '可追溯输出' },
]

function App() {
  const [activeView, setActiveView] = useState<ViewKey>('overview')
  const [selectedCase, setSelectedCase] = useState<ContractCase>(contractCases[16])
  const [filter, setFilter] = useState<'全部' | '正常' | '兼容输入' | '非法输入'>('全部')
  const [notice, setNotice] = useState('')

  const visibleCases = useMemo(() => {
    if (filter === '全部') return contractCases
    return contractCases.filter((item) => item.scenario === filter)
  }, [filter])

  const title = navItems.find((item) => item.key === activeView)?.label ?? '实验总览'

  function showNotice(message: string) {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2400)
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-lockup">
          <div className="brand-symbol" aria-hidden="true">RE</div>
          <div>
            <div className="brand-name">复现台</div>
            <div className="brand-subtitle">flexible · ontology</div>
          </div>
        </div>

        <div className="workspace-switcher" aria-label="当前工作区">
          <span className="workspace-dot" />
          <div>
            <strong>本地复现环境</strong>
            <span>e66f9c3 · clean run</span>
          </div>
          <span className="chevron">⌄</span>
        </div>

        <nav className="primary-nav" aria-label="主要导航">
          <span className="nav-caption">工作区</span>
          {navItems.map((item) => (
            <button
              type="button"
              key={item.key}
              className={`nav-item ${activeView === item.key ? 'is-active' : ''}`}
              onClick={() => setActiveView(item.key)}
            >
              <span className="nav-mark" aria-hidden="true">{item.key === activeView ? '●' : '○'}</span>
              <span className="nav-copy">
                <strong>{item.label}</strong>
                <small>{item.detail}</small>
              </span>
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="source-note">
            <span className="source-kicker">SOURCE REVISION</span>
            <code>e66f9c3ca591</code>
          </div>
          <button type="button" className="help-link" onClick={() => showNotice('运行说明见项目 README')}>查看运行说明 <span>↗</span></button>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="breadcrumbs"><span>复现系统</span><b>/</b><strong>{title}</strong></div>
          <div className="topbar-actions">
            <span className="environment-pill"><i /> Java 17 · Maven 3.8.6</span>
            <button type="button" className="icon-button" aria-label="打开帮助" onClick={() => showNotice('这是绑定源码版本的本地运行预览')}>?</button>
            <div className="avatar" aria-label="当前用户">羊</div>
          </div>
        </header>

        <div className="content-wrap">
          {activeView === 'overview' && (
            <Overview
              selectedCase={selectedCase}
              onSelectCase={setSelectedCase}
              onOpenContracts={() => setActiveView('contracts')}
              onRun={() => showNotice('复现运行入口已就绪，下一步接入真实后端执行器')}
            />
          )}
          {activeView === 'contracts' && (
            <Contracts
              filter={filter}
              setFilter={setFilter}
              cases={visibleCases}
              selectedCase={selectedCase}
              onSelectCase={setSelectedCase}
              onRun={() => showNotice('当前为可视化预览，执行由 Java runner 负责')}
            />
          )}
          {activeView === 'flexible' && <FlexibleEngineView onNotice={showNotice} />}
          {activeView === 'ontology' && <OntologyView />}
          {activeView === 'runs' && <RunsView onNotice={showNotice} />}
        </div>
      </main>

      {notice && <div className="toast" role="status">{notice}</div>}
    </div>
  )
}

function PageIntro({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: React.ReactNode }) {
  return (
    <div className="page-intro">
      <div>
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </div>
  )
}

function Overview({ selectedCase, onSelectCase, onOpenContracts, onRun }: { selectedCase: ContractCase; onSelectCase: (item: ContractCase) => void; onOpenContracts: () => void; onRun: () => void }) {
  return (
    <>
      <PageIntro
        eyebrow="CONTRACT-20 / REPRODUCED SYSTEM RUN"
        title="看见一次完整的复现运行"
        description="当前数据来自独立复现系统的真实代码执行。每个结果都能回到用例、Trace 和源码版本。"
        action={<button type="button" className="primary-button" onClick={onRun}><span>▶</span> 重新执行</button>}
      />

      <section className="run-banner" aria-label="当前运行状态">
        <div className="run-banner-main">
          <span className="status-orb" />
          <div>
            <span className="run-label">当前运行</span>
            <strong>{runMeta.runId}</strong>
          </div>
          <span className="run-state">已完成</span>
        </div>
        <div className="run-banner-meta">
          <span>源码 <code>{runMeta.sourceRevision}</code></span>
          <span>身份 <b>{runMeta.identity}</b></span>
          <button type="button" className="text-button" onClick={onOpenContracts}>查看全部用例 →</button>
        </div>
      </section>

      <section className="metric-strip" aria-label="运行摘要">
        <Metric label="契约场景" value="20" suffix="条" note="全部已执行" tone="teal" />
        <Metric label="预期行为" value="20" suffix="/ 20" note="严格匹配规格" tone="ink" />
        <Metric label="失败 / 阻塞" value="0" suffix="条" note="当前运行" tone="amber" />
        <Metric label="数据身份" value="RUN" suffix="" note="可复现系统" tone="purple" />
      </section>

      <div className="content-grid">
        <section className="panel contract-panel">
          <div className="panel-heading">
            <div>
              <span className="panel-kicker">行为覆盖</span>
              <h2>契约执行矩阵</h2>
            </div>
            <button type="button" className="quiet-button" onClick={onOpenContracts}>打开矩阵</button>
          </div>
          <div className="matrix-table-wrap">
            <table className="matrix-table">
              <thead><tr><th>用例</th><th>能力</th><th>场景</th><th>结果</th><th>链路</th></tr></thead>
              <tbody>
                {contractCases.slice(0, 6).map((item) => (
                  <tr key={item.id} className={selectedCase.id === item.id ? 'selected-row' : ''} onClick={() => onSelectCase(item)}>
                    <td><code>{item.id}</code></td><td>{item.capability}</td><td><ScenarioTag scenario={item.scenario} /></td><td><StatusTag status={item.status} /></td><td><span className="trace-link">{item.traceId}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="table-footnote"><span>展示 6 / 20 条</span><button type="button" className="text-button" onClick={onOpenContracts}>展开全部 <span>→</span></button></div>
        </section>

        <TraceInspector selectedCase={selectedCase} />
      </div>

      <div className="lower-grid">
        <section className="panel lineage-panel">
          <div className="panel-heading compact"><div><span className="panel-kicker">可追踪链路</span><h2>一次请求如何穿过系统</h2></div><span className="neutral-badge">{selectedCase.traceId}</span></div>
          <div className="lineage-track">{traceSteps.map((step, index) => <div className="lineage-step" key={step.label}><div className="lineage-dot">{index + 1}</div><strong>{step.label}</strong><span>{step.detail}</span>{index < traceSteps.length - 1 && <div className="lineage-connector" />}</div>)}</div>
        </section>
        <section className="panel source-panel">
          <div className="panel-heading compact"><div><span className="panel-kicker">数据身份</span><h2>这份结果从哪里来</h2></div><span className="verified-mark">✓</span></div>
          <div className="identity-row"><span className="identity-badge reproduced">REPRODUCED_SYSTEM_RUN</span><span>代码实际执行</span></div>
          <div className="identity-meta"><span>Seed <code>20260902</code></span><span>SHA <code>a1c9b994…</code></span></div>
        </section>
      </div>
    </>
  )
}

function Contracts({ filter, setFilter, cases, selectedCase, onSelectCase, onRun }: { filter: '全部' | '正常' | '兼容输入' | '非法输入'; setFilter: (value: '全部' | '正常' | '兼容输入' | '非法输入') => void; cases: ContractCase[]; selectedCase: ContractCase; onSelectCase: (item: ContractCase) => void; onRun: () => void }) {
  return (
    <>
      <PageIntro eyebrow="CONTRACT MATRIX / 20 CASES" title="契约行为矩阵" description="点击任意用例查看请求、响应、Trace 和数据身份。当前页面展示来自 contract-20 的复现运行快照。" action={<button type="button" className="primary-button" onClick={onRun}><span>▶</span> 执行实验</button>} />
      <section className="filter-bar"><div className="filter-tabs">{(['全部', '正常', '兼容输入', '非法输入'] as const).map((item) => <button type="button" key={item} className={filter === item ? 'filter-active' : ''} onClick={() => setFilter(item)}>{item}</button>)}</div><span className="filter-result">{cases.length} 条匹配</span></section>
      <div className="panel full-table-panel"><table className="matrix-table full"><thead><tr><th>用例</th><th>能力</th><th>场景</th><th>预期结果</th><th>复现结果</th><th>Trace ID</th><th>证据</th></tr></thead><tbody>{cases.map((item) => <tr key={item.id} className={selectedCase.id === item.id ? 'selected-row' : ''} onClick={() => onSelectCase(item)}><td><code>{item.id}</code></td><td className="strong-cell">{item.capability}</td><td><ScenarioTag scenario={item.scenario} /></td><td><code>{item.response}</code></td><td><StatusTag status={item.status} /></td><td><span className="trace-link">{item.traceId}</span></td><td><span className="evidence-count">5 files</span></td></tr>)}</tbody></table></div>
      <TraceInspector selectedCase={selectedCase} />
    </>
  )
}

function FlexibleEngineView({ onNotice }: { onNotice: (message: string) => void }) {
  return (
    <>
      <PageIntro eyebrow="FLEXIBLE ENGINE / STATEFUL CORE" title="柔性引擎行为" description="动态字段、工作流和 Schema 迁移在一个可重复上下文中执行。" action={<button type="button" className="quiet-button" onClick={() => onNotice('字段编辑器将在下一阶段接入')}>打开字段编辑器</button>} />
      <div className="content-grid engine-grid"><section className="panel"><div className="panel-heading"><div><span className="panel-kicker">WORKFLOW</span><h2>状态转换</h2></div><span className="neutral-badge">3 states</span></div><div className="workflow-list">{workflowStates.map((state, index) => <div className={`workflow-row ${state.tone}`} key={state.label}><span className="workflow-index">0{index + 1}</span><strong>{state.label}</strong>{index < workflowStates.length - 1 && <span className="workflow-arrow">→</span>}{index === 1 && <span className="current-label">当前</span>}</div>)}</div><div className="code-note"><code>startInterview</code><span>只允许从 PENDING_INTERVIEW 进入 IN_INTERVIEW</span></div></section><section className="panel"><div className="panel-heading"><div><span className="panel-kicker">SCHEMA VERSION</span><h2>动态字段迁移</h2></div><span className="verified-mark">✓</span></div><div className="migration-list"><MigrationRow from="score" to="evaluationScore" kind="字段改名" /><MigrationRow from="—" to="remote" kind="默认值 false" /><MigrationRow from="v1" to="v2" kind="schema 版本" /></div><div className="success-callout"><span>✓</span><div><strong>迁移后校验通过</strong><small>旧字段被保留为可追踪的迁移规则</small></div></div></section></div>
    </>
  )
}

function OntologyView() {
  return (
    <>
      <PageIntro eyebrow="ONTOLOGY CORE / OBJECT GRAPH" title="本体对象与关系" description="固定属性、动态属性和关系统一进入 JobOntologyDetail，响应可以被直接追踪和比较。" action={<span className="identity-badge reproduced">REPRODUCED_SYSTEM_RUN</span>} />
      <div className="content-grid ontology-grid"><section className="panel ontology-graph"><div className="panel-heading"><div><span className="panel-kicker">OBJECT GRAPH</span><h2>Questionnaire 关系图</h2></div><span className="neutral-badge">3 objects</span></div><div className="object-stack">{ontologyNodes.map((node, index) => <div className="object-row" key={node.id}><div className="object-type">{node.type.slice(0, 2)}</div><div><strong>{node.type}</strong><code>{node.id}</code></div><span className="object-relation">{node.relation}</span><span className="object-count">{node.count}</span>{index < ontologyNodes.length - 1 && <div className="object-link" />}</div>)}</div></section><section className="panel detail-panel"><div className="panel-heading"><div><span className="panel-kicker">DETAIL PAYLOAD</span><h2>JobOntologyDetail</h2></div><span className="status-dot success" /></div><dl className="detail-list"><div><dt>objectType</dt><dd><code>Questionnaire</code></dd></div><div><dt>objectId</dt><dd><code>q-001</code></dd></div><div><dt>sourceVersion</dt><dd><code>1</code></dd></div><div><dt>fixedAttributes</dt><dd><span className="value-chip">name</span><span className="value-chip">subjectId</span></dd></div><div><dt>dynamicAttributes</dt><dd><span className="value-chip teal">subjectCount</span></dd></div></dl></section></div>
    </>
  )
}

function RunsView({ onNotice }: { onNotice: (message: string) => void }) {
  return (
    <>
      <PageIntro eyebrow="RUN HISTORY / SOURCE-BOUND OUTPUT" title="运行记录" description="每次实验都绑定源码版本、seed 和输出哈希。历史运行与复现运行保持分栏。" action={<button type="button" className="quiet-button" onClick={() => onNotice('导出功能将在报告模块接入')}>导出 manifest</button>} />
      <section className="panel run-history"><div className="history-row active"><div className="history-status"><span className="status-dot success" /><strong>已完成</strong></div><div className="history-main"><strong>{runMeta.runId}</strong><span>contract-20 · 20 条场景</span></div><div className="history-revision"><span>source revision</span><code>{runMeta.sourceRevision}</code></div><div className="history-identity"><span className="identity-badge reproduced">REPRODUCED</span></div><button type="button" className="row-arrow" onClick={() => onNotice('当前已在最新运行目录')}>→</button></div><div className="history-row"><div className="history-status"><span className="status-dot neutral" /><strong>历史参考</strong></div><div className="history-main"><strong>R5 / InterviewSession…</strong><span>59 tests · 历史原始报告</span></div><div className="history-revision"><span>source revision</span><code>UNKNOWN</code></div><div className="history-identity"><span className="identity-badge historical">HISTORICAL</span></div><button type="button" className="row-arrow" onClick={() => onNotice('历史材料只作参考，不进入当前复现运行')}>→</button></div></section>
      <section className="run-principle"><span className="principle-mark">i</span><p><strong>运行原则</strong> 复现系统产生的新数据可以支撑本文的本地实验结论，但不会被标记为原生产环境数据。</p></section>
    </>
  )
}

function TraceInspector({ selectedCase }: { selectedCase: ContractCase }) {
  return <section className="panel trace-panel"><div className="panel-heading"><div><span className="panel-kicker">TRACE INSPECTOR</span><h2>{selectedCase.id} · {selectedCase.capability}</h2></div><StatusTag status={selectedCase.status} /></div><div className="trace-meta"><span><b>scenario</b>{selectedCase.scenario}</span><span><b>trace_id</b><code>{selectedCase.traceId}</code></span></div><div className="trace-card"><div className="trace-row"><div className="trace-icon">↗</div><div><strong>Request</strong><span>{selectedCase.scenario} request</span></div><code>request.json</code></div><div className="trace-line" /><div className="trace-row"><div className="trace-icon teal">◈</div><div><strong>Provider</strong><span>QuestionnaireProvider</span></div><code>SUCCESS</code></div><div className="trace-line" /><div className="trace-row"><div className="trace-icon amber">↘</div><div><strong>Response</strong><span>{selectedCase.response}</span></div><code>response.json</code></div></div><div className="trace-footer"><span>原始输出已保存</span><span className="hash-mark">SHA-256</span></div></section>
}

function Metric({ label, value, suffix, note, tone }: { label: string; value: string; suffix: string; note: string; tone: string }) {
  return <div className={`metric ${tone}`}><span>{label}</span><strong>{value}<em>{suffix}</em></strong><small>{note}</small></div>
}

function ScenarioTag({ scenario }: { scenario: string }) {
  const tone = scenario === '非法输入' ? 'warning' : scenario === '兼容输入' ? 'teal' : scenario === '空值策略' ? 'neutral' : 'plain'
  return <span className={`scenario-tag ${tone}`}>{scenario}</span>
}

function StatusTag({ status }: { status: ContractStatus }) {
  return <span className={`status-tag ${status.toLowerCase()}`}><i />{status === 'PASSED' ? '通过' : status === 'BLOCKED' ? '阻塞' : '失败'}</span>
}

function MigrationRow({ from, to, kind }: { from: string; to: string; kind: string }) {
  return <div className="migration-row"><code>{from}</code><span>→</span><code className="target">{to}</code><small>{kind}</small></div>
}

export default App
