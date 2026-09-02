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
  schemaVersion: number
  initialState: string
  updatedAt: string
  fields: EngineField[]
  states: string[]
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
  contextId: string
  status: 'PASSED' | 'FAILED'
  dataIdentity: string
  event: string
  fromState: string
  toState: string
  traceId: string
  createdAt: string
  durationMs: number
  values: Record<string, unknown>
  validationErrors: string[]
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
  constructor(public readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

const apiBase = import.meta.env.VITE_API_BASE_URL ?? ''

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(response.status, payload?.error ?? `API request failed: ${response.status}`)
  }
  return payload as T
}

export const engineApi = {
  overview: () => request<EngineOverview>('/api/overview'),
  models: () => request<EngineModel[]>('/api/models'),
  addModel: (payload: { id: string; name: string; description: string; initialState: string }) =>
    request<EngineModel>('/api/models', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  ontologyTypes: () => request<OntologyType[]>('/api/ontology/types'),
  services: () => request<ServiceRegistration[]>('/api/services'),
  runs: () => request<RuntimeRun[]>('/api/runs'),
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
  execute: (payload: { modelId: string; contextId?: string; event: string; values: Record<string, unknown> }) =>
    request<RuntimeRun>('/api/runtime/execute', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
}
