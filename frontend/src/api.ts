export type FieldType = 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'JSON' | 'OBJECT'

export interface EngineField {
  name: string
  type: FieldType
  required: boolean
  version: number
  defaultValue?: unknown
}

export interface EngineTransition {
  fromState: string
  event: string
  toState: string
}

export interface EngineModel {
  id: string
  name: string
  description: string
  ontologyTypeId?: string | null
  schemaVersion: number
  workflowVersion: number
  initialState: string
  updatedAt: string
  unknownFieldPolicy: string
  fields: EngineField[]
  states: string[]
  transitions: EngineTransition[]
  schemaVersions: SchemaVersion[]
  workflowVersions: WorkflowVersion[]
}

export interface SchemaVersion {
  version: number
  publishedAt: string
  fields: EngineField[]
}

export interface WorkflowVersion {
  version: number
  publishedAt: string
  initialState: string
  transitions: EngineTransition[]
}

export interface OntologyRelation {
  name: string
  targetType: string
  cardinality: string
}

export interface OntologyType {
  id: string
  label: string
  description: string
  version: number
  fixedAttributes: string[]
  dynamicAttributes: string[]
  relations: OntologyRelation[]
}

export interface ServiceRegistration {
  id: string
  name: string
  provider: string
  status: string
  endpoint: string
  version: string
}

export interface RuntimeRun {
  id: string
  modelId: string
  ontologyTypeId?: string | null
  ontologyVersion?: number
  ontologyDefinitionSha256?: string | null
  contextId: string
  status: 'PASSED' | 'FAILED' | 'ROLLED_BACK'
  dataIdentity: string
  event: string
  fromState: string
  toState: string
  traceId: string
  createdAt: string
  durationMs: number
  engineVersion?: string
  schemaVersion?: number
  workflowVersion?: number
  idempotencyKey?: string | null
  retryOfRunId?: string | null
  replayOfRunId?: string | null
  attempt?: number
  contextRevision?: number
  contextCommitted?: boolean
  errorCode?: string | null
  inputValues?: Record<string, unknown>
  beforeSnapshot?: ExecutionSnapshot
  afterSnapshot?: ExecutionSnapshot
  trace?: TraceRecord
  ontologyGraph?: Record<string, unknown>
  ontologyInput?: unknown
  values: Record<string, unknown>
  validationErrors: string[]
}

export interface ExecutionSnapshot {
  phase: string
  contextId: string
  modelId: string
  schemaVersion: number
  workflowVersion: number
  state: string
  status: string
  lifecycle?: string
  capturedAt: string
  values: Record<string, unknown>
  sha256: string
}

export interface TraceSpan {
  spanId: string
  traceId: string
  name: string
  startedAt: string
  endedAt: string
  durationMs: number
  status: string
  attributes: Record<string, string>
}

export interface TraceRecord {
  runId: string
  traceId: string
  startedAt: string
  endedAt: string
  durationMs: number
  status: string
  sealed: boolean
  lifecycle?: string
  spans: TraceSpan[]
}

export interface AuditEvent {
  id: string
  action: string
  targetType: string
  targetId: string
  createdAt: string
  details: string
  beforeRevision: number
  afterRevision: number
  changes: Array<{
    path: string
    beforeValue: unknown
    afterValue: unknown
  }>
}

export interface IdempotencyRecord {
  scope: string
  key: string
  requestSha256: string
  runId: string
  createdAt: string
}

export interface EngineOverview {
  engine: {
    id: string
    name: string
    version: string
    updatedAt: string
    dataIdentity: string
  }
  counts: {
    models: number
    fields: number
    ontologyTypes: number
    services: number
    runs: number
  }
  models: EngineModel[]
  recentRuns: RuntimeRun[]
  capabilities: string[]
}

export class ApiError extends Error {
  constructor(public readonly status: number, message: string,
              public readonly errorCode?: string, public readonly traceId?: string) {
    super(message)
    this.name = 'ApiError'
  }
}

const apiBase = import.meta.env.VITE_API_BASE_URL ?? ''
let lastRevision: string | null = null

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('Content-Type', 'application/json')
  const method = (init?.method ?? 'GET').toUpperCase()
  if (method !== 'GET' && method !== 'OPTIONS' && lastRevision && !headers.has('If-Match')) {
    headers.set('If-Match', lastRevision)
  }
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers,
  })
  const revision = response.headers.get('ETag')
  if (revision) lastRevision = revision
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(response.status, payload?.message ?? payload?.error ?? `API request failed: ${response.status}`,
      payload?.errorCode, payload?.traceId)
  }
  return payload as T
}

export const engineApi = {
  overview: () => request<EngineOverview>('/api/overview'),
  models: () => request<EngineModel[]>('/api/models'),
  addModel: (payload: { id: string; name: string; description: string; initialState: string; ontologyTypeId?: string | null }) =>
    request<EngineModel>('/api/models', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  updateModelOntologyBinding: (modelId: string, ontologyTypeId: string | null) =>
    request<EngineModel>(`/api/models/${encodeURIComponent(modelId)}/ontology-binding`, {
      method: 'PUT',
      body: JSON.stringify({ ontologyTypeId }),
    }),
  ontologyTypes: () => request<OntologyType[]>('/api/ontology/types'),
  services: () => request<ServiceRegistration[]>('/api/services'),
  runs: () => request<RuntimeRun[]>('/api/runs'),
  run: (runId: string) => request<RuntimeRun>(`/api/runs/${encodeURIComponent(runId)}`),
  trace: (runId: string) => request<TraceRecord>(`/api/runs/${encodeURIComponent(runId)}/trace`),
  snapshots: (runId: string) => request<ExecutionSnapshot[]>(`/api/runs/${encodeURIComponent(runId)}/snapshots`),
  auditEvents: () => request<AuditEvent[]>('/api/audit-events'),
  idempotencyRecords: () => request<IdempotencyRecord[]>('/api/idempotency-records'),
  exportState: () => request<Record<string, unknown>>('/api/export'),
  retry: (runId: string) => request<RuntimeRun>(`/api/runs/${encodeURIComponent(runId)}/retry`, { method: 'POST', body: '{}' }),
  replay: (runId: string) => request<RuntimeRun>(`/api/runs/${encodeURIComponent(runId)}/replay`, { method: 'POST', body: '{}' }),
  rollback: (runId: string) => request<RuntimeRun>(`/api/runs/${encodeURIComponent(runId)}/rollback`, { method: 'POST', body: '{}' }),
  addField: (modelId: string, payload: { name: string; type: FieldType; required: boolean; defaultValue?: unknown }) =>
    request<EngineField>(`/api/models/${encodeURIComponent(modelId)}/fields`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  addTransition: (modelId: string, payload: EngineTransition) =>
    request<EngineTransition>(`/api/models/${encodeURIComponent(modelId)}/transitions`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  addOntologyType: (payload: { id: string; label: string; description: string; fixedAttributes: string[]; dynamicAttributes: string[] }) =>
    request<OntologyType>('/api/ontology/types', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  addOntologyRelation: (typeId: string, payload: { name: string; targetType: string; cardinality: string }) =>
    request<OntologyRelation>(`/api/ontology/types/${encodeURIComponent(typeId)}/relations`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  addService: (payload: { id: string; name: string; provider: string; status: string; endpoint: string; version: string }) =>
    request<ServiceRegistration>('/api/services', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  execute: (payload: { modelId: string; contextId?: string; event: string; values: Record<string, unknown>; ontology?: unknown; idempotencyKey?: string }) =>
    request<RuntimeRun>('/api/runtime/execute', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
}
