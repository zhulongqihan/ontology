import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { engineApi, type EngineModel, type ExecutionSnapshot, type OntologyType, type RuntimeRun, type TraceRecord, type TraceSpan } from './api'

type ViewKey = 'overview' | 'models' | 'schema' | 'workflow' | 'ontology' | 'graph' | 'services' | 'runtime' | 'evidence' | 'compare'

type GraphNode = {
  id: string
  type: string
  label: string
  attributes: number
}

type GraphEdge = {
  sourceId: string
  relation: string
  targetId: string
}

type NormalizedGraph = {
  rootObjectId?: string
  nodes: GraphNode[]
  edges: GraphEdge[]
  source: 'runtime' | 'definition'
}

type LoadedRun = {
  run: RuntimeRun
  trace: TraceRecord | null
  snapshots: ExecutionSnapshot[]
}

export function ControlPlaneJourney({ onNavigate }: { onNavigate: (view: ViewKey) => void }) {
  const steps: Array<{ number: string; title: string; description: string; view: ViewKey }> = [
    { number: '01', title: '定义模型', description: '创建对象模型并确认字段与状态', view: 'models' },
    { number: '02', title: '绑定本体', description: '选择显式根类型和版本身份', view: 'ontology' },
    { number: '03', title: '真实运行', description: '提交输入并生成 Run 证据', view: 'runtime' },
    { number: '04', title: '复核结果', description: '沿 Trace、快照和图谱继续下钻', view: 'evidence' },
  ]
  return <section className="control-journey" aria-label="控制面工作路径">
    <div className="journey-intro"><span className="panel-kicker">CONTROL PLANE PATH</span><h2>从定义到证据</h2><p>按这条路径完成一次真实引擎运行。每一步都写入或读取同一份引擎状态。</p></div>
    <div className="journey-steps">{steps.map((step) => <button type="button" className="journey-step" key={step.number} onClick={() => onNavigate(step.view)}><span className="journey-number">{step.number}</span><span><strong>{step.title}</strong><small>{step.description}</small></span><b aria-hidden="true">→</b></button>)}</div>
  </section>
}

export function KnowledgeGraphView({ types, runs }: { types: OntologyType[]; runs: RuntimeRun[] }) {
  const [selectedRunId, setSelectedRunId] = useState('')
  const selectedRun = runs.find((run) => run.id === selectedRunId)
  const graph = useMemo(() => normalizeGraph(selectedRun?.ontologyGraph, types), [selectedRun?.ontologyGraph, types])
  const positions = useMemo(() => graphPositions(graph.nodes), [graph.nodes])
  const width = Math.max(720, Math.ceil(graph.nodes.length / 3) * 220)
  const height = Math.max(280, Math.ceil(graph.nodes.length / 3) * 104)

  useEffect(() => {
    if (selectedRunId && !runs.some((run) => run.id === selectedRunId)) setSelectedRunId('')
  }, [runs, selectedRunId])

  return <>
    <PageIntro eyebrow="ONTOLOGY / GRAPH EXPLORER" title="知识图谱" description="查看本体定义和一次真实运行实际经过的对象关系。切换运行记录后，图谱会从静态定义切换为运行时实例。" action={<label className="inline-select">运行路径<select value={selectedRunId} onChange={(event) => setSelectedRunId(event.target.value)}><option value="">静态本体定义</option>{runs.filter((run) => hasGraph(run.ontologyGraph)).map((run) => <option value={run.id} key={run.id}>{run.id} · {run.status}</option>)}</select></label>} />
    <section className="panel graph-panel">
      <PanelHeading kicker={graph.source === 'runtime' ? 'RUNTIME GRAPH' : 'ONTOLOGY DEFINITION'} title={graph.source === 'runtime' ? `${selectedRun?.id ?? '运行'} 的实际执行图` : '本体对象关系'} action={<span className="graph-source">{graph.nodes.length} 节点 · {graph.edges.length} 条关系</span>} />
      {graph.nodes.length === 0 ? <EmptyState title="暂无可视化关系" description="先在本体模型中注册对象和关系，或执行一次带 ontology 输入的真实运行。" /> : <div className="graph-canvas-wrap"><svg className="graph-canvas" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="本体对象关系图"><defs><marker id="graph-arrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L0,6 L7,3 z" fill="currentColor" /></marker></defs>{graph.edges.map((edge, index) => { const source = positions.get(edge.sourceId); const target = positions.get(edge.targetId); if (!source || !target) return null; return <g key={`${edge.sourceId}-${edge.relation}-${edge.targetId}-${index}`} className="graph-edge"><line x1={source.x + 82} y1={source.y + 26} x2={target.x + 82} y2={target.y + 26} markerEnd="url(#graph-arrow)" /><text x={(source.x + target.x) / 2 + 82} y={(source.y + target.y) / 2 + 21}>{edge.relation}</text></g> })}{graph.nodes.map((node) => { const position = positions.get(node.id); if (!position) return null; const isRoot = node.id === graph.rootObjectId; return <g key={node.id} className={`graph-node ${isRoot ? 'is-root' : ''}`} transform={`translate(${position.x},${position.y})`}><rect width="164" height="53" rx="8" /><text className="graph-node-type" x="13" y="17">{node.type}</text><text className="graph-node-label" x="13" y="36">{node.label}</text><text className="graph-node-count" x="151" y="17" textAnchor="end">{node.attributes}</text></g> })}</svg></div>}
      <div className="graph-legend"><span><i className="legend-root" />根对象</span><span><i className="legend-node" />对象节点</span><span><i className="legend-edge" />关系方向</span><span className="graph-note">{graph.source === 'runtime' ? '绿色路径来自 Run 的 ontologyGraph' : '当前展示注册的本体定义'}</span></div>
    </section>
    <section className="graph-detail-grid"><section className="panel"><PanelHeading kicker="RELATION LEDGER" title="关系清单" />{graph.edges.length === 0 ? <EmptyState title="暂无关系" description="关系会在本体模型中定义后显示。" /> : <div className="graph-relation-list">{graph.edges.map((edge, index) => <div className="graph-relation-row" key={`${edge.relation}-${index}`}><code>{edge.sourceId}</code><span>→</span><strong>{edge.relation}</strong><span>→</span><code>{edge.targetId}</code></div>)}</div>}</section><section className="panel"><PanelHeading kicker="GRAPH PROVENANCE" title="图谱来源" /><div className="provenance-list"><Fact label="source" value={graph.source === 'runtime' ? 'RuntimeRun.ontologyGraph' : 'OntologyTypeConfig'} /><Fact label="selected run" value={selectedRun?.id ?? 'none'} /><Fact label="root object" value={graph.rootObjectId ?? '—'} /><Fact label="data identity" value={selectedRun?.dataIdentity ?? 'ENGINE_RUNTIME_RESULT'} /></div></section></section>
  </>
}

export function ComparisonView({ models, runs }: { models: EngineModel[]; runs: RuntimeRun[] }) {
  const [baselineId, setBaselineId] = useState(runs[1]?.id ?? '')
  const [flexibleId, setFlexibleId] = useState(runs[0]?.id ?? '')
  const [baseline, setBaseline] = useState<LoadedRun | null>(null)
  const [flexible, setFlexible] = useState<LoadedRun | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (runs.length === 0) return
    if (!runs.some((run) => run.id === baselineId)) setBaselineId(runs[1]?.id ?? runs[0].id)
    if (!runs.some((run) => run.id === flexibleId)) setFlexibleId(runs[0].id)
  }, [runs, baselineId, flexibleId])

  useEffect(() => {
    if (!baselineId || !flexibleId || baselineId === flexibleId) {
      setBaseline(null)
      setFlexible(null)
      return
    }
    let active = true
    setLoading(true)
    setError('')
    void Promise.all([loadRunEvidence(baselineId), loadRunEvidence(flexibleId)])
      .then(([nextBaseline, nextFlexible]) => { if (active) { setBaseline(nextBaseline); setFlexible(nextFlexible) } })
      .catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : '对比证据加载失败') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [baselineId, flexibleId])

  const paired = Boolean(baseline && flexible && baseline.run.modelId === flexible.run.modelId && stableValue(baseline.run.inputValues) === stableValue(flexible.run.inputValues) && baseline.run.event === flexible.run.event)
  const modelName = models.find((model) => model.id === flexible?.run.modelId)?.name ?? flexible?.run.modelId ?? '—'

  return <>
    <PageIntro eyebrow="COMPARISON / PAIRED RUNS" title="成对运行对比" description="选择两次真实持久化 Run，检查同一输入下的结果、调用链、证据完整度和本体身份。固定基线执行器尚未接入，因此页面不会把同一引擎的两次运行冒充成 Before / After 提升。" action={<span className="identity-badge live">REAL RUN EVIDENCE</span>} />
    <section className="panel comparison-selector"><div><PanelHeading kicker="PAIR CONFIGURATION" title="选择对比运行" /><p className="panel-description">Run A 和 Run B 必须来自相同模型、事件和输入；如果条件不同，结果会被标记为不可直接比较。当前两侧均读取 Engine API 的真实运行记录。</p></div><div className="comparison-selects"><label>Run A · 参照记录<select value={baselineId} onChange={(event) => setBaselineId(event.target.value)}><option value="">选择 Run</option>{runs.map((run) => <option value={run.id} key={run.id}>{run.id} · {run.status}</option>)}</select></label><span className="comparison-arrow">→</span><label>Run B · 待比较记录<select value={flexibleId} onChange={(event) => setFlexibleId(event.target.value)}><option value="">选择 Run</option>{runs.map((run) => <option value={run.id} key={run.id}>{run.id} · {run.status}</option>)}</select></label></div></section>
    {error && <div className="error-list comparison-error">! {error}</div>}
    {loading && <div className="panel comparison-loading"><span className="state-mark spinning">↻</span><strong>正在读取两次运行的证据…</strong></div>}
    {!loading && (!baseline || !flexible) && <section className="panel"><EmptyState title="选择两次不同的真实运行" description="当前页面只读取后端 Run、Trace 和 Snapshot，不生成虚假的 Before/After 结果。" /></section>}
    {!loading && baseline && flexible && <>
      <section className={`comparison-verdict ${paired ? 'is-paired' : 'is-unpaired'}`}><div><span className="panel-kicker">COMPARABILITY</span><strong>{paired ? '输入条件一致，可以进行成对观察' : '输入条件不一致，不能直接解释为提升'}</strong><p>{paired ? `当前比较模型：${modelName} · 事件：${flexible.run.event || 'none'} · 输入值和执行条件来自两个 Run 的持久化记录。` : '请重新选择相同模型、事件和 inputValues 的两次运行，或将本次结果仅作为工程观察。'}</p></div><span className="comparison-badge">{paired ? 'PAIRED OBSERVATION' : 'UNPAIRED'}</span></section>
      {paired && <div className="comparison-disclosure"><strong>当前对照边界</strong><span>两侧均为柔性引擎真实 Run；固定映射 baseline 尚未接入。这里的耗时和状态差异是成对运行观察，不是系统提升结论。</span></div>}
      <section className="comparison-metric-grid"><CompareMetric label="执行状态" before={humanStatus(baseline.run.status)} after={humanStatus(flexible.run.status)} tone={flexible.run.status === 'PASSED' ? 'positive' : 'negative'} /><CompareMetric label="最终状态" before={baseline.run.toState} after={flexible.run.toState} /><CompareMetric label="端到端耗时" before={`${baseline.run.durationMs} ms`} after={`${flexible.run.durationMs} ms`} note={durationDelta(baseline.run.durationMs, flexible.run.durationMs)} /><CompareMetric label="Trace Span" before={String(baseline.trace?.spans.length ?? baseline.run.trace?.spans.length ?? 0)} after={String(flexible.trace?.spans.length ?? flexible.run.trace?.spans.length ?? 0)} note="来自真实 Trace" /><CompareMetric label="Snapshot" before={String(baseline.snapshots.length)} after={String(flexible.snapshots.length)} note="Run A / Run B 结构" /><CompareMetric label="本体身份" before={ontologyIdentity(baseline.run)} after={ontologyIdentity(flexible.run)} note="version + hash" /></section>
      <section className="panel comparison-panel"><PanelHeading kicker="EXECUTION CHAIN / SIDE BY SIDE" title="调用链对比" /><CallChainComparison baseline={baseline} flexible={flexible} /></section>
      <section className="comparison-two-column"><section className="panel"><PanelHeading kicker="DECISION EVIDENCE" title="结构化决策证据" /><DecisionEvidence run={flexible.run} /></section><section className="panel"><PanelHeading kicker="KNOWLEDGE GRAPH / RUN B" title="待比较运行图谱" /><MiniGraph run={flexible.run} /></section></section>
    </>}
  </>
}

function PageIntro({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: ReactNode }) {
  return <div className="page-intro"><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>{description}</p></div>{action}</div>
}

function PanelHeading({ kicker, title, action }: { kicker: string; title: string; action?: ReactNode }) {
  return <div className="panel-heading"><div><span className="panel-kicker">{kicker}</span><h2>{title}</h2></div>{action}</div>
}

function Fact({ label, value }: { label: string; value: string }) {
  return <div className="fact"><span>{label}</span><strong title={value}>{value}</strong></div>
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return <div className="empty-state"><span>∅</span><strong>{title}</strong><p>{description}</p></div>
}

function CompareMetric({ label, before, after, note, tone }: { label: string; before: string; after: string; note?: string; tone?: string }) {
  return <div className={`compare-metric ${tone ?? ''}`}><span>{label}</span><div><strong>{before}</strong><b>→</b><strong>{after}</strong></div><small>{note ?? 'Run A / Run B 原始值'}</small></div>
}

function CallChainComparison({ baseline, flexible }: { baseline: LoadedRun; flexible: LoadedRun }) {
  const beforeSpans = baseline.trace?.spans ?? baseline.run.trace?.spans ?? []
  const afterSpans = flexible.trace?.spans ?? flexible.run.trace?.spans ?? []
  const maxDuration = Math.max(1, ...beforeSpans.map((span) => span.durationMs), ...afterSpans.map((span) => span.durationMs))
  const names = Array.from(new Set([...beforeSpans.map((span) => span.name), ...afterSpans.map((span) => span.name)]))
  if (names.length === 0) return <EmptyState title="没有可用 Span" description="本次运行没有返回 Trace Span，不能绘制调用链。" />
  return <div className="call-chain-table"><div className="call-chain-head"><span>阶段</span><span>Run A / 参照记录</span><span>Run B / 待比较记录</span></div>{names.map((name) => { const before = beforeSpans.find((span) => span.name === name); const after = afterSpans.find((span) => span.name === name); return <div className="call-chain-row" key={name}><strong>{name}</strong><SpanBar span={before} maxDuration={maxDuration} /><SpanBar span={after} maxDuration={maxDuration} /></div> })}</div>
}

function SpanBar({ span, maxDuration }: { span?: TraceSpan; maxDuration: number }) {
  if (!span) return <div className="span-bar missing"><span>未出现</span></div>
  const width = Math.max(7, Math.min(100, (span.durationMs / maxDuration) * 100))
  return <div className={`span-bar ${span.status.toLowerCase()}`}><div style={{ width: `${width}%` }} /><span>{span.status} · {span.durationMs} ms</span></div>
}

function DecisionEvidence({ run }: { run: RuntimeRun }) {
  const spans = run.trace?.spans ?? []
  const ontology = spans.find((span) => span.name === 'ontology')
  const provider = spans.find((span) => span.name === 'provider')
  const validation = spans.find((span) => span.name === 'validation')
  const workflow = spans.find((span) => span.name === 'workflow')
  const facts = [
    ['输入模型', run.modelId],
    ['显式本体绑定', run.ontologyTypeId ?? '未绑定'],
    ['本体版本', String(run.ontologyVersion ?? '—')],
    ['定义 hash', shortHash(run.ontologyDefinitionSha256)],
    ['Schema 迁移', validation?.attributes.schemaMigrationApplied ?? '未记录'],
    ['工作流转移', workflow?.attributes.fromState && workflow?.attributes.toState ? `${workflow.attributes.fromState} → ${workflow.attributes.toState}` : '未记录'],
    ['Provider', provider?.attributes.provider ?? provider?.attributes.transport ?? '未记录'],
    ['对象数量', provider?.attributes.objectCount ?? '未记录'],
    ['本体阶段', ontology?.status ?? '未记录'],
  ]
  return <div className="decision-evidence">{facts.map(([label, value]) => <div className="decision-row" key={label}><span>{label}</span><code title={value}>{value}</code></div>)}<p className="evidence-disclaimer">这里展示的是可复核的结构化决策证据，不是模型内部不可验证的原始 COT。</p></div>
}

function MiniGraph({ run }: { run: RuntimeRun }) {
  const graph = normalizeGraph(run.ontologyGraph, [])
  const positions = graphPositions(graph.nodes, 2)
  const width = Math.max(560, Math.ceil(graph.nodes.length / 2) * 220)
  const height = Math.max(230, Math.ceil(graph.nodes.length / 2) * 104)
  if (graph.nodes.length === 0) return <EmptyState title="本次运行没有图谱" description="只有带 ontology 输入且 Provider 成功装配的 Run 才会生成运行时图谱。" />
  return <div className="mini-graph-wrap"><svg className="mini-graph" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="柔性运行时知识图谱"><defs><marker id="mini-graph-arrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L0,6 L7,3 z" fill="currentColor" /></marker></defs>{graph.edges.map((edge, index) => { const source = positions.get(edge.sourceId); const target = positions.get(edge.targetId); if (!source || !target) return null; return <line className="mini-graph-edge" key={`${edge.relation}-${index}`} x1={source.x + 82} y1={source.y + 26} x2={target.x + 82} y2={target.y + 26} markerEnd="url(#mini-graph-arrow)" /> })}{graph.nodes.map((node) => { const position = positions.get(node.id); if (!position) return null; return <g className="mini-graph-node" key={node.id} transform={`translate(${position.x},${position.y})`}><rect width="164" height="53" rx="8" /><text x="12" y="18">{node.type}</text><text x="12" y="37">{node.label}</text></g> })}</svg><small className="graph-note">{graph.nodes.length} 个对象 · {graph.edges.length} 条实际关系 · root={graph.rootObjectId ?? '—'}</small></div>
}

async function loadRunEvidence(runId: string): Promise<LoadedRun> {
  const [run, trace, snapshots] = await Promise.all([engineApi.run(runId), engineApi.trace(runId), engineApi.snapshots(runId)])
  return { run, trace, snapshots }
}

function normalizeGraph(raw: Record<string, unknown> | undefined, types: OntologyType[]): NormalizedGraph {
  const runtimeObjects = raw && Array.isArray(raw.objects) ? raw.objects.filter(isRecord) : []
  const runtimeRelations = raw && Array.isArray(raw.relations) ? raw.relations.filter(isRecord) : []
  if (runtimeObjects.length > 0) {
    return {
      rootObjectId: text(raw?.rootObjectId),
      nodes: runtimeObjects.map((object) => ({ id: text(object.id), type: text(object.type) || 'Object', label: text(object.id), attributes: object.attributes && isRecord(object.attributes) ? Object.keys(object.attributes).length : 0 })).filter((node) => node.id),
      edges: runtimeRelations.map((relation) => ({ sourceId: text(relation.sourceId), relation: text(relation.relation) || 'relatedTo', targetId: text(relation.targetId) })).filter((edge) => edge.sourceId && edge.targetId),
      source: 'runtime',
    }
  }
  const nodes = types.map((type) => ({ id: type.id, type: 'OntologyType', label: type.label, attributes: type.fixedAttributes.length + type.dynamicAttributes.length }))
  const known = new Set(nodes.map((node) => node.id))
  const canonicalIds = new Map(types.map((type) => [type.id.toLowerCase(), type.id]))
  const edges: GraphEdge[] = []
  types.forEach((type) => type.relations.forEach((relation) => {
    const targetId = canonicalIds.get(relation.targetType.toLowerCase()) ?? relation.targetType
    if (!known.has(targetId)) { nodes.push({ id: targetId, type: 'OntologyType', label: relation.targetType, attributes: 0 }); known.add(targetId) }
    edges.push({ sourceId: type.id, relation: `${relation.name} · ${relation.cardinality}`, targetId })
  }))
  return { nodes, edges, source: 'definition' }
}

function graphPositions(nodes: GraphNode[], columns = 3) {
  const positions = new Map<string, { x: number; y: number }>()
  nodes.forEach((node, index) => positions.set(node.id, { x: 22 + (index % columns) * 220, y: 22 + Math.floor(index / columns) * 104 }))
  return positions
}

function hasGraph(graph?: Record<string, unknown>) {
  return Boolean(graph && Array.isArray(graph.objects) && graph.objects.length > 0)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

function text(value: unknown) {
  return value === undefined || value === null ? '' : String(value)
}

function stableValue(value: unknown) {
  return JSON.stringify(value ?? null)
}

function humanStatus(status: string) {
  return status === 'PASSED' ? '通过' : status === 'FAILED' ? '失败' : status
}

function ontologyIdentity(run: RuntimeRun) {
  return run.ontologyTypeId ? `${run.ontologyTypeId} · v${run.ontologyVersion ?? '—'}` : '未绑定'
}

function durationDelta(before: number, after: number) {
  const delta = after - before
  return `${delta > 0 ? '+' : ''}${delta} ms · 原始观测`
}

function shortHash(value?: string | null) {
  if (!value) return '—'
  return value.length > 12 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value
}
