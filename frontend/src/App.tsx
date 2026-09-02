import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { engineApi, type EngineField, type EngineModel, type EngineOverview, type EngineTransition, type FieldType, type OntologyType, type RuntimeRun, type ServiceRegistration } from './api'

type ViewKey = 'overview' | 'models' | 'schema' | 'workflow' | 'ontology' | 'services' | 'runtime'

const navigation: Array<{ key: ViewKey; label: string; detail: string; mark: string }> = [
  { key: 'overview', label: '引擎总览', detail: '控制面状态', mark: '◎' },
  { key: 'models', label: '模型管理', detail: '柔性对象模型', mark: '◇' },
  { key: 'schema', label: 'Schema / 字段', detail: '动态结构版本', mark: '▦' },
  { key: 'workflow', label: '工作流', detail: '状态与事件', mark: '↗' },
  { key: 'ontology', label: '本体模型', detail: '对象与关系', mark: '◈' },
  { key: 'services', label: '服务注册', detail: 'Provider / Consumer', mark: '⌘' },
  { key: 'runtime', label: '运行调试', detail: '输入与快照', mark: '▶' },
]

function App() {
  const [activeView, setActiveView] = useState<ViewKey>('overview')
  const [overview, setOverview] = useState<EngineOverview | null>(null)
  const [models, setModels] = useState<EngineModel[]>([])
  const [ontologyTypes, setOntologyTypes] = useState<OntologyType[]>([])
  const [services, setServices] = useState<ServiceRegistration[]>([])
  const [runs, setRuns] = useState<RuntimeRun[]>([])
  const [selectedModelId, setSelectedModelId] = useState('interview-session')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const refresh = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [nextOverview, nextModels, nextOntologyTypes, nextServices, nextRuns] = await Promise.all([
        engineApi.overview(),
        engineApi.models(),
        engineApi.ontologyTypes(),
        engineApi.services(),
        engineApi.runs(),
      ])
      setOverview(nextOverview)
      setModels(nextModels)
      setOntologyTypes(nextOntologyTypes)
      setServices(nextServices)
      setRuns(nextRuns)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '无法连接到引擎管理 API')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])
  useEffect(() => {
    if (models.length > 0 && !models.some((model) => model.id === selectedModelId)) {
      setSelectedModelId(models[0].id)
    }
  }, [models, selectedModelId])

  const selectedModel = models.find((model) => model.id === selectedModelId) ?? models[0]
  const title = navigation.find((item) => item.key === activeView)?.label ?? '引擎总览'

  function showNotice(message: string) {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2600)
  }

  async function afterMutation(action: () => Promise<unknown>, success: string) {
    try {
      await action()
      await refresh()
      showNotice(success)
    } catch (reason) {
      showNotice(reason instanceof Error ? reason.message : '操作失败')
    }
  }

  if (loading && !overview) return <LoadingScreen />
  if (error && !overview) return <ConnectionScreen message={error} onRetry={() => void refresh()} />

  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <div className="brand-lockup">
          <div className="brand-symbol" aria-hidden="true">FE</div>
          <div><div className="brand-name">柔性引擎</div><div className="brand-subtitle">admin control plane</div></div>
        </div>
        <div className="engine-switcher" aria-label="当前引擎">
          <span className="engine-pulse" />
          <div><strong>{overview?.engine.name ?? '柔性引擎复现'}</strong><span>v{overview?.engine.version ?? '0.2.0'} · local</span></div>
          <span className="chevron">⌄</span>
        </div>
        <nav className="primary-nav" aria-label="引擎管理导航">
          <span className="nav-caption">ENGINE CONTROL</span>
          {navigation.map((item) => (
            <button type="button" key={item.key} className={`nav-item ${activeView === item.key ? 'is-active' : ''}`} onClick={() => setActiveView(item.key)}>
              <span className="nav-mark" aria-hidden="true">{item.mark}</span>
              <span className="nav-copy"><strong>{item.label}</strong><small>{item.detail}</small></span>
            </button>
          ))}
        </nav>
        <div className="sidebar-footer"><span className="source-kicker">ENGINE ID</span><code>{overview?.engine.id ?? 'flexible-engine-reproduction'}</code><span className="sidebar-note">本地持久化 · API 在线</span></div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="breadcrumbs"><span>柔性引擎控制面</span><b>/</b><strong>{title}</strong></div>
          <div className="topbar-actions"><span className="api-status"><i /> API ONLINE</span><span className="environment-pill">JDK 17 · local state</span><button type="button" className="icon-button" aria-label="刷新引擎状态" onClick={() => void refresh()}>↻</button><div className="avatar" aria-label="当前用户">羊</div></div>
        </header>
        <div className="content-wrap">
          {activeView === 'overview' && overview && <OverviewView overview={overview} runs={runs} onNavigate={setActiveView} onRefresh={() => void refresh()} />}
          {activeView === 'models' && <ModelsView models={models} selectedModel={selectedModel} onSelect={setSelectedModelId} onNavigate={setActiveView} onAdd={(payload) => afterMutation(() => engineApi.addModel(payload), '模型已写入引擎注册表')} />}
          {activeView === 'schema' && selectedModel && <SchemaView model={selectedModel} onAdd={(payload) => afterMutation(() => engineApi.addField(selectedModel.id, payload), '字段已写入引擎 Schema')} />}
          {activeView === 'workflow' && selectedModel && <WorkflowView model={selectedModel} onAdd={(payload) => afterMutation(() => engineApi.addTransition(selectedModel.id, payload), '状态转换已写入工作流')} />}
          {activeView === 'ontology' && <OntologyView types={ontologyTypes} onAdd={(payload) => afterMutation(() => engineApi.addOntologyType(payload), '本体类型已写入模型注册表')} onAddRelation={(typeId, payload) => afterMutation(() => engineApi.addOntologyRelation(typeId, payload), '本体关系已写入对象模型')} />}
          {activeView === 'services' && <ServicesView services={services} onAdd={(payload) => afterMutation(() => engineApi.addService(payload), '服务已写入本地注册表')} />}
          {activeView === 'runtime' && selectedModel && <RuntimeView models={models} selectedModel={selectedModel} runs={runs} onExecute={async (payload) => { const run = await engineApi.execute(payload); await refresh(); showNotice('运行已完成，结果已持久化'); return run }} />}
        </div>
      </main>
      {notice && <div className="toast" role="status">{notice}</div>}
    </div>
  )
}

function LoadingScreen() {
  return <div className="state-screen"><div className="state-mark spinning">↻</div><h1>正在连接柔性引擎</h1><p>读取模型、Schema、工作流和本体注册表…</p></div>
}

function ConnectionScreen({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <div className="state-screen"><div className="state-mark error">!</div><h1>管理 API 尚未连接</h1><p>{message}</p><code>java -jar reproduction-app/target/reproduction-app-0.1.0-SNAPSHOT.jar admin</code><button type="button" className="primary-button" onClick={onRetry}>重新连接</button></div>
}

function PageIntro({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: ReactNode }) {
  return <div className="page-intro"><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>{description}</p></div>{action}</div>
}

function OverviewView({ overview, runs, onNavigate, onRefresh }: { overview: EngineOverview; runs: RuntimeRun[]; onNavigate: (view: ViewKey) => void; onRefresh: () => void }) {
  return <>
    <PageIntro eyebrow="FLEXIBLE ENGINE / CONTROL PLANE" title="引擎总览" description="从这里管理柔性对象模型、动态 Schema、状态流程和本体关系。所有写操作都会进入本地引擎状态并可再次运行。" action={<button type="button" className="primary-button" onClick={onRefresh}><span>↻</span> 刷新状态</button>} />
    <section className="engine-banner"><div className="banner-main"><span className="status-orb" /><div><span>当前引擎</span><strong>{overview.engine.name}</strong></div><StatusPill status="READY" /></div><div className="banner-meta"><span>版本 <code>{overview.engine.version}</code></span><span>身份 <b>{overview.engine.dataIdentity}</b></span><span>更新 <code>{formatTime(overview.engine.updatedAt)}</code></span></div></section>
    <section className="metric-strip" aria-label="引擎资源摘要"><Metric label="对象模型" value={overview.counts.models} note="可管理模型" tone="teal" /><Metric label="动态字段" value={overview.counts.fields} note="跨模型 Schema" tone="ink" /><Metric label="本体类型" value={overview.counts.ontologyTypes} note="对象与关系" tone="purple" /><Metric label="运行快照" value={overview.counts.runs} note="本地持久化" tone="amber" /></section>
    <div className="overview-grid"><section className="panel model-overview"><PanelHeading kicker="MODEL REGISTRY" title="模型注册表" action={<button type="button" className="text-button" onClick={() => onNavigate('models')}>管理模型 →</button>} /><div className="model-list">{overview.models.map((model) => <button type="button" className="model-list-row" key={model.id} onClick={() => onNavigate('models')}><span className="model-type">M</span><span className="model-copy"><strong>{model.name}</strong><small>{model.id} · v{model.schemaVersion}</small></span><span className="model-stats">{model.fields.length} fields<br />{model.transitions.length} transitions</span><span className="row-arrow">→</span></button>)}</div></section><section className="panel capability-panel"><PanelHeading kicker="ENGINE CAPABILITIES" title="已启用能力" /><div className="capability-list">{overview.capabilities.map((capability) => <div className="capability-row" key={capability}><span className="status-dot success" /><code>{capability}</code><span>READY</span></div>)}</div></section></div>
    <section className="panel recent-panel"><PanelHeading kicker="RUNTIME JOURNAL" title="最近运行" action={<button type="button" className="text-button" onClick={() => onNavigate('runtime')}>打开运行调试 →</button>} />{runs.length === 0 ? <EmptyState title="还没有运行快照" description="进入运行调试，提交一次真实的引擎输入。" /> : <div className="run-list">{runs.slice(0, 4).map((run) => <div className="run-row" key={run.id}><StatusPill status={run.status} /><div><strong>{run.id}</strong><small>{run.modelId} · {run.event || '未触发事件'}</small></div><div><span>{run.fromState} → {run.toState}</span><small>{formatTime(run.createdAt)}</small></div><code>{run.traceId}</code></div>)}</div>}</section>
  </>
}

function ModelsView({ models, selectedModel, onSelect, onNavigate, onAdd }: { models: EngineModel[]; selectedModel?: EngineModel; onSelect: (id: string) => void; onNavigate: (view: ViewKey) => void; onAdd: (payload: { id: string; name: string; description: string; initialState: string }) => Promise<void> }) {
  const [id, setId] = useState(''); const [name, setName] = useState(''); const [description, setDescription] = useState(''); const [initialState, setInitialState] = useState('DRAFT');
  async function submit(event: FormEvent) { event.preventDefault(); if (!id.trim() || !name.trim()) return; await onAdd({ id: id.trim(), name: name.trim(), description: description.trim(), initialState: initialState.trim() || 'DRAFT' }); setId(''); setName(''); setDescription(''); setInitialState('DRAFT') }
  return <><PageIntro eyebrow="MODEL REGISTRY / FLEXIBLE OBJECTS" title="模型管理" description="模型是柔性引擎的运行单元。选择模型后，可以继续管理它的字段版本、状态流程和运行输入。" action={<span className="identity-badge live">ENGINE STATE</span>} /><div className="split-layout"><div className="registry-stack"><section className="panel registry-panel"><PanelHeading kicker="REGISTERED MODELS" title={`${models.length} 个模型`} /><div className="registry-list">{models.map((model) => <button type="button" className={`registry-row ${selectedModel?.id === model.id ? 'selected' : ''}`} key={model.id} onClick={() => onSelect(model.id)}><span className="model-type">M</span><span className="model-copy"><strong>{model.name}</strong><small>{model.id}</small></span><span className="status-dot success" /></button>)}</div></section><section className="panel form-panel compact-form"><PanelHeading kicker="CREATE MODEL" title="新增运行模型" /><form onSubmit={submit} className="admin-form"><label>模型 ID<input value={id} onChange={(event) => setId(event.target.value)} placeholder="assessment-session" /></label><label>模型名称<input value={name} onChange={(event) => setName(event.target.value)} placeholder="评估会话" /></label><label>说明<input value={description} onChange={(event) => setDescription(event.target.value)} placeholder="模型用途" /></label><label>初始状态<input value={initialState} onChange={(event) => setInitialState(event.target.value)} placeholder="DRAFT" /></label><button type="submit" className="primary-button">创建模型</button></form></section></div>{selectedModel ? <ModelDetail model={selectedModel} onNavigate={onNavigate} /> : <EmptyState title="没有模型" description="请先创建模型定义。" />}</div></>
}

function ModelDetail({ model, onNavigate }: { model: EngineModel; onNavigate: (view: ViewKey) => void }) {
  return <section className="panel model-detail"><div className="detail-hero"><div><span className="eyebrow">MODEL / {model.id}</span><h2>{model.name}</h2><p>{model.description}</p></div><StatusPill status="READY" /></div><div className="detail-facts"><Fact label="schema version" value={`v${model.schemaVersion}`} /><Fact label="initial state" value={model.initialState} /><Fact label="fields" value={String(model.fields.length)} /><Fact label="transitions" value={String(model.transitions.length)} /></div><div className="section-divider" /><PanelHeading kicker="MODEL CONTROL" title="进入管理模块" /><div className="quick-actions"><button type="button" onClick={() => onNavigate('schema')}><strong>Schema / 字段</strong><span>增加动态属性和版本规则 →</span></button><button type="button" onClick={() => onNavigate('workflow')}><strong>工作流</strong><span>配置状态和事件转换 →</span></button><button type="button" onClick={() => onNavigate('runtime')}><strong>运行调试</strong><span>输入数据并执行引擎 →</span></button></div><div className="detail-foot">最后更新：{formatTime(model.updatedAt)}<code>{model.id}</code></div></section>
}

function SchemaView({ model, onAdd }: { model: EngineModel; onAdd: (payload: { name: string; type: FieldType; required: boolean; defaultValue?: unknown }) => Promise<void> }) {
  const [name, setName] = useState(''); const [type, setType] = useState<FieldType>('STRING'); const [required, setRequired] = useState(false); const [defaultValue, setDefaultValue] = useState('');
  async function submit(event: FormEvent) { event.preventDefault(); if (!name.trim()) return; await onAdd({ name: name.trim(), type, required, ...(defaultValue ? { defaultValue } : {}) }); setName(''); setDefaultValue('') }
  return <><PageIntro eyebrow={`SCHEMA / ${model.id}`} title="Schema 与动态字段" description="字段定义会进入模型 Schema，版本号由引擎在写入时递增。运行时会按这里的类型和必填规则校验输入。" action={<span className="version-badge">CURRENT v{model.schemaVersion}</span>} /><div className="content-grid admin-grid"><section className="panel"><PanelHeading kicker="FIELD DEFINITIONS" title={`${model.fields.length} 个字段`} /><div className="field-table-wrap"><table className="admin-table"><thead><tr><th>字段</th><th>类型</th><th>必填</th><th>版本</th><th>默认值</th></tr></thead><tbody>{model.fields.map((field) => <FieldRow field={field} key={field.name} />)}</tbody></table></div></section><section className="panel form-panel"><PanelHeading kicker="ADD FIELD" title="增加动态字段" /><form onSubmit={submit} className="admin-form"><label>字段名<input value={name} onChange={(event) => setName(event.target.value)} placeholder="例如 evaluationScore" /></label><label>字段类型<select value={type} onChange={(event) => setType(event.target.value as FieldType)}>{(['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'JSON', 'OBJECT'] as FieldType[]).map((item) => <option value={item} key={item}>{item}</option>)}</select></label><label className="check-label"><input type="checkbox" checked={required} onChange={(event) => setRequired(event.target.checked)} /> 必填字段</label><label>默认值<input value={defaultValue} onChange={(event) => setDefaultValue(event.target.value)} placeholder="可选" /></label><button type="submit" className="primary-button">写入 Schema</button></form><div className="form-note">写入后会持久化到引擎状态文件，刷新页面仍然可见。</div></section></div></>
}

function WorkflowView({ model, onAdd }: { model: EngineModel; onAdd: (payload: EngineTransition) => Promise<void> }) {
  const [fromState, setFromState] = useState(model.initialState); const [event, setEvent] = useState(''); const [toState, setToState] = useState('');
  async function submit(formEvent: FormEvent) { formEvent.preventDefault(); if (!fromState.trim() || !event.trim() || !toState.trim()) return; await onAdd({ fromState: fromState.trim(), event: event.trim(), toState: toState.trim() }); setEvent(''); setToState('') }
  return <><PageIntro eyebrow={`WORKFLOW / ${model.id}`} title="工作流与状态" description="工作流由初始状态和事件转换组成。运行调试提交事件时，只有这里配置的转换才能推进状态。" action={<span className="version-badge">{model.states.length} STATES</span>} /><div className="content-grid admin-grid"><section className="panel"><PanelHeading kicker="STATE GRAPH" title="状态节点" /><div className="state-flow">{model.states.map((state, index) => <div className={`state-node ${state === model.initialState ? 'initial' : ''}`} key={state}><span>0{index + 1}</span><strong>{state}</strong>{state === model.initialState && <small>INITIAL</small>}</div>)}</div><div className="workflow-note"><span className="status-dot success" /><p>初始状态：<code>{model.initialState}</code>。执行会从此状态创建一个新的运行上下文。</p></div></section><section className="panel form-panel"><PanelHeading kicker="ADD TRANSITION" title="增加状态转换" /><form onSubmit={submit} className="admin-form"><label>起始状态<input value={fromState} onChange={(event) => setFromState(event.target.value)} placeholder="PENDING_INTERVIEW" /></label><label>触发事件<input value={event} onChange={(formEvent) => setEvent(formEvent.target.value)} placeholder="startInterview" /></label><label>目标状态<input value={toState} onChange={(formEvent) => setToState(formEvent.target.value)} placeholder="IN_INTERVIEW" /></label><button type="submit" className="primary-button">写入工作流</button></form></section></div><section className="panel transition-panel"><PanelHeading kicker="TRANSITIONS" title={`${model.transitions.length} 条事件转换`} /><div className="transition-list">{model.transitions.map((transition) => <div className="transition-row" key={`${transition.fromState}-${transition.event}`}><code>{transition.fromState}</code><span>— {transition.event} →</span><code className="target">{transition.toState}</code></div>)}</div></section></>
}

function OntologyView({ types, onAdd, onAddRelation }: { types: OntologyType[]; onAdd: (payload: { id: string; label: string; description: string; fixedAttributes: string[]; dynamicAttributes: string[] }) => Promise<void>; onAddRelation: (typeId: string, payload: { name: string; targetType: string; cardinality: string }) => Promise<void> }) {
  const [id, setId] = useState(''); const [label, setLabel] = useState(''); const [description, setDescription] = useState(''); const [fixed, setFixed] = useState(''); const [dynamic, setDynamic] = useState('');
  const [relationType, setRelationType] = useState(types[0]?.id ?? ''); const [relationName, setRelationName] = useState(''); const [targetType, setTargetType] = useState(types[1]?.id ?? types[0]?.id ?? ''); const [cardinality, setCardinality] = useState('1:N');
  async function submit(event: FormEvent) { event.preventDefault(); if (!id.trim() || !label.trim()) return; await onAdd({ id: id.trim(), label: label.trim(), description: description.trim(), fixedAttributes: splitCsv(fixed), dynamicAttributes: splitCsv(dynamic) }); setId(''); setLabel(''); setDescription(''); setFixed(''); setDynamic('') }
  async function submitRelation(event: FormEvent) { event.preventDefault(); if (!relationType || !relationName.trim() || !targetType) return; await onAddRelation(relationType, { name: relationName.trim(), targetType, cardinality }); setRelationName('') }
  return <><PageIntro eyebrow="ONTOLOGY / OBJECT REGISTRY" title="本体对象与关系" description="本体层描述柔性对象的固定属性、动态属性和关系目标。它是模型运行结果进入领域对象的装配入口。" action={<span className="identity-badge live">LIVE REGISTRY</span>} /><div className="content-grid admin-grid"><section className="panel"><PanelHeading kicker="ONTOLOGY TYPES" title={`${types.length} 个对象类型`} /><div className="ontology-list">{types.map((type) => <div className="ontology-row" key={type.id}><div className="object-type">{type.label.slice(0, 2)}</div><div className="ontology-copy"><strong>{type.label}</strong><code>{type.id}</code><small>{type.description}</small></div><div className="ontology-attributes"><span>{type.fixedAttributes.length} fixed</span><span>{type.dynamicAttributes.length} dynamic</span><span>{type.relations.length} relations</span></div>{type.relations.length > 0 && <div className="ontology-relations">{type.relations.map((relation) => <span key={relation.name}><code>{relation.name}</code> → {relation.targetType} · {relation.cardinality}</span>)}</div>}</div>)}</div></section><div className="registry-stack"><section className="panel form-panel"><PanelHeading kicker="REGISTER TYPE" title="增加本体类型" /><form onSubmit={submit} className="admin-form"><label>类型 ID<input value={id} onChange={(event) => setId(event.target.value)} placeholder="assessment" /></label><label>显示名称<input value={label} onChange={(event) => setLabel(event.target.value)} placeholder="Assessment" /></label><label>说明<input value={description} onChange={(event) => setDescription(event.target.value)} placeholder="对象用途" /></label><label>固定属性<input value={fixed} onChange={(event) => setFixed(event.target.value)} placeholder="name, subjectId" /></label><label>动态属性<input value={dynamic} onChange={(event) => setDynamic(event.target.value)} placeholder="score, tags" /></label><button type="submit" className="primary-button">注册本体类型</button></form></section><section className="panel form-panel"><PanelHeading kicker="ADD RELATION" title="增加本体关系" /><form onSubmit={submitRelation} className="admin-form"><label>关系来源<select value={relationType} onChange={(event) => setRelationType(event.target.value)}>{types.map((type) => <option value={type.id} key={type.id}>{type.label} · {type.id}</option>)}</select></label><label>关系名称<input value={relationName} onChange={(event) => setRelationName(event.target.value)} placeholder="containsAssessment" /></label><label>目标类型<select value={targetType} onChange={(event) => setTargetType(event.target.value)}>{types.map((type) => <option value={type.id} key={type.id}>{type.label} · {type.id}</option>)}</select></label><label>基数<input value={cardinality} onChange={(event) => setCardinality(event.target.value)} placeholder="1:N" /></label><button type="submit" className="primary-button" disabled={types.length === 0}>写入本体关系</button></form></section></div></div></>
}

function ServicesView({ services, onAdd }: { services: ServiceRegistration[]; onAdd: (payload: { id: string; name: string; provider: string; status: string; endpoint: string; version: string }) => Promise<void> }) {
  const [id, setId] = useState(''); const [name, setName] = useState(''); const [provider, setProvider] = useState('LocalServiceRegistry'); const [endpoint, setEndpoint] = useState('local://'); const [version, setVersion] = useState('v1');
  async function submit(event: FormEvent) { event.preventDefault(); if (!id.trim() || !name.trim() || !provider.trim() || !endpoint.trim()) return; await onAdd({ id: id.trim(), name: name.trim(), provider: provider.trim(), status: 'READY', endpoint: endpoint.trim(), version: version.trim() || 'v1' }); setId(''); setName(''); setEndpoint('local://') }
  return <><PageIntro eyebrow="SERVICE REGISTRY / LOCAL PROVIDERS" title="服务注册" description="查看兼容层中的 Provider、Assembler 和本地服务地址。服务注册是引擎调用链的一部分，不是实验报告数据。" action={<span className="version-badge">{services.length} SERVICES</span>} /><div className="content-grid admin-grid"><section className="panel"><PanelHeading kicker="REGISTERED SERVICES" title="服务目录" /><div className="service-table-wrap"><table className="admin-table service-table"><thead><tr><th>服务</th><th>Provider</th><th>状态</th><th>Endpoint</th><th>版本</th></tr></thead><tbody>{services.map((service) => <tr key={service.id}><td><strong>{service.name}</strong><small>{service.id}</small></td><td><code>{service.provider}</code></td><td><StatusPill status={service.status} /></td><td><code>{service.endpoint}</code></td><td><span className="version-badge">{service.version}</span></td></tr>)}</tbody></table></div></section><section className="panel form-panel"><PanelHeading kicker="REGISTER SERVICE" title="增加本地服务" /><form onSubmit={submit} className="admin-form"><label>服务 ID<input value={id} onChange={(event) => setId(event.target.value)} placeholder="assessment-provider" /></label><label>服务名称<input value={name} onChange={(event) => setName(event.target.value)} placeholder="Assessment Provider" /></label><label>Provider<input value={provider} onChange={(event) => setProvider(event.target.value)} /></label><label>Endpoint<input value={endpoint} onChange={(event) => setEndpoint(event.target.value)} placeholder="local://assessment-provider" /></label><label>版本<input value={version} onChange={(event) => setVersion(event.target.value)} placeholder="v1" /></label><button type="submit" className="primary-button">注册服务</button></form></section></div><div className="info-callout"><span>i</span><p>当前服务为本地可复现实现。真实生产注册中心不可用时，系统会明确使用 `local://` 地址，不会伪装成原生产服务。</p></div></>
}

function RuntimeView({ models, selectedModel, runs, onExecute }: { models: EngineModel[]; selectedModel: EngineModel; runs: RuntimeRun[]; onExecute: (payload: { modelId: string; contextId?: string; event: string; values: Record<string, unknown> }) => Promise<RuntimeRun> }) {
  const [modelId, setModelId] = useState(selectedModel.id); const currentModel = models.find((model) => model.id === modelId) ?? selectedModel; const [contextId, setContextId] = useState(''); const [event, setEvent] = useState(currentModel.transitions[0]?.event ?? ''); const [input, setInput] = useState(JSON.stringify(sampleFor(currentModel), null, 2)); const [running, setRunning] = useState(false); const [lastRun, setLastRun] = useState<RuntimeRun | null>(runs[0] ?? null);
  function changeModel(nextId: string) { const next = models.find((model) => model.id === nextId) ?? selectedModel; setModelId(nextId); setEvent(next.transitions[0]?.event ?? ''); setInput(JSON.stringify(sampleFor(next), null, 2)) }
  async function submit(formEvent: FormEvent) { formEvent.preventDefault(); try { const values = JSON.parse(input) as Record<string, unknown>; setRunning(true); const payload = { modelId, ...(contextId.trim() ? { contextId: contextId.trim() } : {}), event, values }; const run = await onExecute(payload); setContextId(run.contextId); setLastRun(run) } catch (reason) { setLastRun({ id: 'local-error', modelId, contextId: contextId || 'not-created', status: 'FAILED', dataIdentity: 'LOCAL_VALIDATION', event, fromState: currentModel.initialState, toState: currentModel.initialState, traceId: 'not-created', createdAt: new Date().toISOString(), durationMs: 0, values: {}, validationErrors: [reason instanceof Error ? reason.message : '输入 JSON 无法解析'] }) } finally { setRunning(false) } }
  return <><PageIntro eyebrow="RUNTIME / ENGINE EXECUTION" title="运行调试" description="直接向柔性引擎提交动态对象和事件，观察 Schema 校验、状态迁移和运行快照。这里是真实调用，不是静态演示。" action={<span className="identity-badge live">EXECUTION READY</span>} /><div className="runtime-layout"><section className="panel runtime-form-panel"><PanelHeading kicker="INPUT CONTEXT" title="提交运行" /><form onSubmit={submit} className="admin-form"><label>选择模型<select value={modelId} onChange={(event) => changeModel(event.target.value)}>{models.map((model) => <option value={model.id} key={model.id}>{model.name} · {model.id}</option>)}</select></label><label>上下文 ID<input value={contextId} onChange={(event) => setContextId(event.target.value)} placeholder="留空则创建新上下文" /></label><label>触发事件<select value={event} onChange={(formEvent) => setEvent(formEvent.target.value)}><option value="">不触发事件</option>{currentModel.transitions.map((transition) => <option value={transition.event} key={transition.event}>{transition.event} · {transition.fromState} → {transition.toState}</option>)}</select></label><label>动态对象 JSON<textarea value={input} onChange={(formEvent) => setInput(formEvent.target.value)} spellCheck={false} /></label><button type="submit" className="primary-button" disabled={running}>{running ? '执行中…' : '执行引擎'}</button></form><div className="form-note">上下文 ID 相同的运行会复用上次快照的状态和字段，实现连续状态迁移；留空则启动全新的运行上下文。</div></section><section className="panel result-panel"><PanelHeading kicker="EXECUTION RESULT" title="最近一次结果" />{lastRun ? <RuntimeResult run={lastRun} /> : <EmptyState title="等待第一次运行" description="提交左侧输入后，这里会显示状态和快照。" />}</section></div><section className="panel"><PanelHeading kicker="RUN HISTORY" title={`${runs.length} 条运行记录`} />{runs.length === 0 ? <EmptyState title="暂无运行记录" description="运行结果将保存在本地引擎状态文件中。" /> : <div className="run-list">{runs.slice(0, 8).map((run) => <div className="run-row" key={run.id}><StatusPill status={run.status} /><div><strong>{run.id}</strong><small>{run.modelId} · {run.event || 'no event'} · {run.contextId}</small></div><div><span>{run.fromState} → {run.toState}</span><small>{formatTime(run.createdAt)}</small></div><code>{run.dataIdentity}</code></div>)}</div>}</section></>
}

function RuntimeResult({ run }: { run: RuntimeRun }) {
  return <div className="runtime-result"><div className="result-top"><StatusPill status={run.status} /><code>{run.traceId}</code></div><div className="result-flow"><span>{run.fromState}</span><b>→</b><span className={run.status === 'PASSED' ? 'success-text' : 'error-text'}>{run.toState}</span></div><div className="result-meta"><Fact label="context" value={run.contextId} /><Fact label="event" value={run.event || 'none'} /><Fact label="duration" value={`${run.durationMs} ms`} /></div>{run.validationErrors.length > 0 && <div className="error-list">{run.validationErrors.map((error) => <div key={error}>! {error}</div>)}</div>}<pre>{JSON.stringify(run.values, null, 2)}</pre></div>
}

function FieldRow({ field }: { field: EngineField }) { return <tr><td><strong>{field.name}</strong></td><td><span className="type-badge">{field.type}</span></td><td>{field.required ? <span className="required-mark">REQUIRED</span> : <span className="muted-mark">OPTIONAL</span>}</td><td><code>v{field.version}</code></td><td>{field.defaultValue === undefined || field.defaultValue === null ? <span className="muted-mark">—</span> : <code>{String(field.defaultValue)}</code>}</td></tr> }
function Metric({ label, value, note, tone }: { label: string; value: number; note: string; tone: string }) { return <div className={`metric ${tone}`}><span>{label}</span><strong>{value}</strong><small>{note}</small></div> }
function PanelHeading({ kicker, title, action }: { kicker: string; title: string; action?: ReactNode }) { return <div className="panel-heading"><div><span className="panel-kicker">{kicker}</span><h2>{title}</h2></div>{action}</div> }
function Fact({ label, value }: { label: string; value: string }) { return <div className="fact"><span>{label}</span><strong>{value}</strong></div> }
function StatusPill({ status }: { status: string }) { const normalized = status.toLowerCase(); return <span className={`status-pill ${normalized}`}><i />{status === 'PASSED' ? '通过' : status === 'FAILED' ? '失败' : status === 'READY' ? 'READY' : status}</span> }
function EmptyState({ title, description }: { title: string; description: string }) { return <div className="empty-state"><span>∅</span><strong>{title}</strong><p>{description}</p></div> }
function formatTime(value: string) { const date = new Date(value); return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false }) }
function splitCsv(value: string) { return value.split(',').map((item) => item.trim()).filter(Boolean) }
function sampleFor(model: EngineModel) { const values: Record<string, unknown> = {}; model.fields.forEach((field) => { if (field.defaultValue !== undefined && field.defaultValue !== null) values[field.name] = field.defaultValue; else if (field.required) values[field.name] = field.type === 'INTEGER' ? 95 : field.type === 'BOOLEAN' ? false : field.type === 'DECIMAL' ? 0.95 : field.type === 'JSON' ? {} : 'sample-value' }); return values }

export default App
