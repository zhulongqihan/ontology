param(
    [string]$PaperRoot = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) '毕业论文工作区\06_论文写作产物\07_论文源文件\njuthesis_正式初稿'),
    [switch]$RequireExperiment
)

$ErrorActionPreference = 'Stop'
$bodyPath = Join-Path $PaperRoot 'chapter\正文.tex'
$rootPath = Join-Path $PaperRoot '论文初稿.tex'
$bibPath = Join-Path $PaperRoot 'references.bib'
$reportPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'runs\reproduction-suite\latest\report.json'

foreach ($path in @($bodyPath, $rootPath, $bibPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "missing paper source: $path"
    }
}

$body = Get-Content -LiteralPath $bodyPath -Raw
$root = Get-Content -LiteralPath $rootPath -Raw
$bib = Get-Content -LiteralPath $bibPath -Raw

foreach ($term in @('RQ1', 'RQ2', 'RQ3', 'RQ4', 'ontologyTypeId', 'COMMITTED', 'PREPARED', 'A/B/C', 'BEGIN IMMEDIATE', '79 条')) {
    if ($body.IndexOf($term, [StringComparison]::Ordinal) -lt 0 -and $root.IndexOf($term, [StringComparison]::Ordinal) -lt 0) {
        throw "paper source does not contain required term: $term"
    }
}

foreach ($legacy in @('74/74', '73 条自动化测试', '尚未设置独立', 'source multiplicity 目前只被解析保存')) {
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
    if ($report.C_repeatability_ablation.idempotent_unique_persisted_runs -ne 1 -or
        -not $report.C_repeatability_ablation.contract_outcome_stable_across_seeds) {
        throw 'repeatability result does not match the paper claim'
    }
}

Write-Output 'paper-consistency=PASS'
Write-Output "paper-root=$PaperRoot"
if ($RequireExperiment) {
    Write-Output "experiment-report=$reportPath"
}
