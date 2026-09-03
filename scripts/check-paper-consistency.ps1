param(
    [string]$PaperRoot = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) '毕业论文工作区\06_论文写作产物\07_论文源文件\njuthesis_正式初稿'),
    [switch]$RequireExperiment
)

$ErrorActionPreference = 'Stop'
$bodyPath = Join-Path $PaperRoot 'chapter\正文.tex'
$rootPath = Join-Path $PaperRoot '论文初稿.tex'
$bibPath = Join-Path $PaperRoot 'references.bib'
$reportPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'docs\实验证据\20260903_evolution_capability_control_plane\report.json'

foreach ($path in @($bodyPath, $rootPath, $bibPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "missing paper source: $path"
    }
}

$body = Get-Content -LiteralPath $bodyPath -Raw
$root = Get-Content -LiteralPath $rootPath -Raw
$bib = Get-Content -LiteralPath $bibPath -Raw

foreach ($term in @('RQ1', 'RQ2', 'RQ3', 'RQ4', 'ontologyTypeId', 'Ontology version', 'definition hash', 'COMMITTED', 'PREPARED', 'A/B/C/D/E', 'RigidMappingBaseline', 'duration\_ns', 'BEGIN IMMEDIATE', '92 条', '演化能力矩阵', 'Replay')) {
    if ($body.IndexOf($term, [StringComparison]::Ordinal) -lt 0 -and $root.IndexOf($term, [StringComparison]::Ordinal) -lt 0) {
        throw "paper source does not contain required term: $term"
    }
}

foreach ($legacy in @('74/74', '73 条自动化测试', '78 条当前 Maven 测试', '79 条自动化测试', '79/79', '尚未设置独立', 'source multiplicity 目前只被解析保存', 'schema 11', 'schema 12', '84 条', '84/84', '90 条', '90/90', 'A/B/C/D 四组')) {
    if ($body.IndexOf($legacy, [StringComparison]::Ordinal) -ge 0 -or $root.IndexOf($legacy, [StringComparison]::Ordinal) -ge 0) {
        throw "paper source still contains obsolete claim: $legacy"
    }
}

foreach ($key in @('W3CPROV2013', 'OpenTelemetryTrace2025', 'SQLiteTransactions2025', 'SQLiteIsolation2025', 'KleinOntologyEvolution2003', 'SoftwareReplication2018', 'ConsumerDrivenContract2025')) {
    if ($bib.IndexOf($key, [StringComparison]::Ordinal) -lt 0) {
        throw "missing bibliography entry: $key"
    }
}

if ($RequireExperiment) {
    if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
        throw "missing experiment report: $reportPath"
    }
    $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
    if (-not $report.A_mechanism_control.explicit_correct -or $report.A_mechanism_control.name_derived_baseline_correct) {
        throw 'mechanism comparison does not match the paper claim'
    }
    if (-not $report.B_fault_injection.persistence_failure_atomic -or -not $report.B_fault_injection.retry_recovered) {
        throw 'fault-injection result does not match the paper claim'
    }
    if ($report.C_repeatability_ablation.idempotent_unique_persisted_runs_after_restart -ne 1 -or
        -not $report.C_repeatability_ablation.contract_outcome_stable_across_seeds -or
        -not $report.C_repeatability_ablation.same_seed_report_stable -or
        -not $report.C_repeatability_ablation.restart_preserved_idempotency) {
        throw 'repeatability result does not match the paper claim'
    }
    if (-not $report.D_baseline_flexible_comparison) {
        throw 'missing baseline/flexible comparison section'
    }
    $dynamic = $report.D_baseline_flexible_comparison.cases.'questionnaire-dynamic-field'
    if ($dynamic.comparable_pairs -ne 12 -or $dynamic.baseline_passed -ne 0 -or
        $dynamic.flexible_passed -ne 12 -or $dynamic.outcome_improved_pairs -ne 12 -or
        -not $dynamic.input_hashes_consistent -or
        -not $dynamic.baseline_configuration_hash_consistent -or
        -not $dynamic.flexible_configuration_hash_consistent) {
        throw 'baseline/flexible comparison result does not match the paper claim'
    }
    $invariantCulture = [System.Globalization.CultureInfo]::InvariantCulture
    foreach ($caseSpec in @(
        @{ key = 'questionnaire-basic'; label = '基础问卷'; baseline = 12; flexible = 12; improved = 0 },
        @{ key = 'questionnaire-dynamic-field'; label = '动态字段'; baseline = 0; flexible = 12; improved = 12 },
        @{ key = 'questionnaire-knowledge-graph'; label = '知识图谱'; baseline = 12; flexible = 12; improved = 0 }
    )) {
        $case = $report.D_baseline_flexible_comparison.cases.PSObject.Properties[$caseSpec.key].Value
        $baselineP50 = ([long]$case.baseline_duration_ns.p50).ToString('N0', $invariantCulture)
        $flexibleP50 = ([long]$case.flexible_duration_ns.p50).ToString('N0', $invariantCulture)
        $deltaP50 = ([long]$case.duration_delta_ns.p50).ToString('N0', $invariantCulture)
        $expectedRow = "$($caseSpec.label) & 12/12 & $($caseSpec.baseline)/12 & $($caseSpec.flexible)/12 & $($caseSpec.improved)/12 & $baselineP50 & $flexibleP50 & $deltaP50 \\"
        if ($body.IndexOf($expectedRow, [StringComparison]::Ordinal) -lt 0) {
            throw "paper D p50 row does not match experiment report: $($caseSpec.key)"
        }
    }
    if (-not $report.E_evolution_capability_matrix) {
        throw 'missing evolution capability matrix section'
    }
    $evolution = $report.E_evolution_capability_matrix
    if ($evolution.comparable_pairs -ne 36 -or $evolution.baseline_passed -ne 12 -or
        $evolution.flexible_passed -ne 36 -or $evolution.outcome_improved_pairs -ne 24 -or
        $evolution.adaptation_gain_pairs -ne 24 -or $evolution.evidence_complete_pairs -ne 36 -or
        -not $evolution.all_input_hashes_consistent -or -not $evolution.all_evidence_complete) {
        throw 'evolution capability matrix result does not match the paper claim'
    }
    if ([math]::Abs(([double]$evolution.adaptation_gain_rate) - (24.0 / 36.0)) -gt 0.000001) {
        throw 'evolution adaptation gain rate does not match the paper claim'
    }
    foreach ($caseSpec in @(
        @{ key = 'interview-dynamic-field'; label = '面试新增字段'; migration = 0; baseline = 0; flexible = 12; improved = 12 },
        @{ key = 'interview-field-rename'; label = '面试字段重命名'; migration = 12; baseline = 0; flexible = 12; improved = 12 },
        @{ key = 'questionnaire-dynamic-graph'; label = '动态关系图谱'; migration = 0; baseline = 12; flexible = 12; improved = 0 }
    )) {
        $case = $evolution.cases.PSObject.Properties[$caseSpec.key].Value
        $expectedRow = "$($caseSpec.label) & 12/12 & $($caseSpec.baseline)/12 & $($caseSpec.flexible)/12 & $($caseSpec.improved)/12 & $($caseSpec.migration) & 12/12 \\"
        if ($body.IndexOf($expectedRow, [StringComparison]::Ordinal) -lt 0) {
            throw "paper E capability row does not match experiment report: $($caseSpec.key)"
        }
    }
}

Write-Output 'paper-consistency=PASS'
Write-Output "paper-root=$PaperRoot"
if ($RequireExperiment) {
    Write-Output "experiment-report=$reportPath"
}
