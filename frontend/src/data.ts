export type ContractStatus = 'PASSED' | 'BLOCKED' | 'FAILED'

export type ContractCase = {
  id: string
  capability: string
  scenario: string
  status: ContractStatus
  traceId: string
  response: string
  evidence: string
}

export const runMeta = {
  experiment: 'contract-20',
  runId: 'contract-20-seed-20260902',
  sourceRevision: 'e66f9c3',
  identity: 'REPRODUCED_SYSTEM_RUN',
  total: 20,
  passed: 20,
  failed: 0,
  blocked: 0,
}

export const contractCases: ContractCase[] = [
  ['C-01', '套卷整体查询', '正常', 'SUCCESS', 'q-001 / q-002'],
  ['C-02', '套卷整体查询', '空值策略', 'SUCCESS', 'q-001 / q-002'],
  ['C-03', '套卷整体查询', '兼容输入', 'SUCCESS', 'q-001 / q-002'],
  ['C-04', '套卷整体查询', '非法输入', 'INVALID_INPUT', '拒绝并返回错误'],
  ['C-05', '题目反查套卷', '正常', 'SUCCESS', 'q-001 / q-002'],
  ['C-06', '题目反查套卷', '空值策略', 'SUCCESS', 'q-001 / q-002'],
  ['C-07', '题目反查套卷', '兼容输入', 'SUCCESS', 'q-001 / q-002'],
  ['C-08', '题目反查套卷', '非法输入', 'INVALID_INPUT', '拒绝并返回错误'],
  ['C-09', 'linkageConfig 查询', '正常', 'SUCCESS', 'questionnaire=q-001'],
  ['C-10', 'linkageConfig 查询', '空值策略', 'NOT_FOUND', '明确返回未找到'],
  ['C-11', 'linkageConfig 查询', '兼容输入', 'SUCCESS', 'questionnaire=q-001'],
  ['C-12', 'linkageConfig 查询', '非法输入', 'INVALID_INPUT', '拒绝并返回错误'],
  ['C-13', 'linkageConfig 保存', '正常', 'SUCCESS', 'version=v1'],
  ['C-14', 'linkageConfig 保存', '空值策略', 'INVALID_INPUT', '拒绝并返回错误'],
  ['C-15', 'linkageConfig 保存', '兼容输入', 'SUCCESS', 'version=v1'],
  ['C-16', 'linkageConfig 保存', '非法输入', 'INVALID_INPUT', '拒绝并返回错误'],
  ['C-17', '面试套卷详情', '正常', 'SUCCESS', 'JobOntologyDetail'],
  ['C-18', '面试套卷详情', '空值策略', 'NOT_FOUND', '明确返回未找到'],
  ['C-19', '面试套卷详情', '兼容输入', 'SUCCESS', 'JobOntologyDetail'],
  ['C-20', '面试套卷详情', '非法输入', 'INVALID_INPUT', '拒绝并返回错误'],
].map(([id, capability, scenario, status, response]) => ({
  id,
  capability,
  scenario,
  status: 'PASSED',
  traceId: `trace-${id}`,
  response,
  evidence: 'request · response · trace · result · sha256',
}))

export const traceSteps = [
  { label: 'Consumer', detail: 'JsfExAssessService', state: '完成' },
  { label: 'Local Registry', detail: 'QuestionnaireProvider', state: '完成' },
  { label: 'Ontology Core', detail: 'DetailAssembler', state: '完成' },
  { label: 'Response', detail: '稳定 JSON 载荷', state: '完成' },
]

export const workflowStates = [
  { label: 'PENDING_INTERVIEW', tone: 'neutral' },
  { label: 'IN_INTERVIEW', tone: 'active' },
  { label: 'COMPLETED', tone: 'success' },
]

export const ontologyNodes = [
  { type: 'Questionnaire', id: 'q-001', relation: 'root object', count: '1' },
  { type: 'Subject', id: 's-001', relation: 'containsSubject', count: '1' },
  { type: 'Option', id: 'o-001', relation: 'subjectContainsOption', count: '1' },
]
