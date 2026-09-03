#!/usr/bin/env python3
"""Read-only audit for the canonical SQLite state and derived evidence files."""

import argparse
import hashlib
import json
import sqlite3
import sys
from pathlib import Path


def finding(items, severity, message, evidence):
    items.append({"severity": severity, "message": message, "evidence": evidence})


def read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        return {"_error": str(exc)}


def audit_database(path, expected_schema, findings):
    if not path.exists():
        finding(findings, "P0", "canonical SQLite state is missing", str(path))
        return {"path": str(path), "exists": False}

    result = {"path": str(path), "exists": True}
    try:
        connection = sqlite3.connect(str(path))
        connection.row_factory = sqlite3.Row
    except sqlite3.Error as exc:
        finding(findings, "P0", "canonical SQLite state cannot be opened", "%s: %s" % (path, exc))
        return result

    with connection:
        versions = [row[0] for row in connection.execute("SELECT version FROM schema_version ORDER BY version")]
        result["schema_versions"] = versions
        result["schema_version"] = max(versions) if versions else None
        if not versions or max(versions) < expected_schema:
            finding(findings, "P1", "SQLite schema is behind the current loader and requires migration on open",
                    "schema_version=%s, expected_at_least=%s" % (result["schema_version"], expected_schema))

        state = connection.execute(
            "SELECT revision, payload_json, payload_sha256 FROM engine_state WHERE state_id = 1").fetchone()
        if state is None:
            finding(findings, "P0", "engine_state row is missing", "engine_state.state_id=1")
        else:
            result["revision"] = state["revision"]
            result["payload_sha256"] = state["payload_sha256"]
            actual_hash = hashlib.sha256(state["payload_json"].encode("utf-8")).hexdigest()
            result["payload_hash_matches"] = actual_hash == state["payload_sha256"]
            if not result["payload_hash_matches"]:
                finding(findings, "P0", "SQLite compatibility payload hash does not match its bytes",
                        "engine_state.revision=%s" % state["revision"])
            latest_write = connection.execute(
                "SELECT payload_sha256 FROM state_write ORDER BY write_id DESC LIMIT 1").fetchone()
            result["latest_state_write_hash_matches"] = latest_write is not None and latest_write[0] == state["payload_sha256"]
            if not result["latest_state_write_hash_matches"]:
                finding(findings, "P0", "latest state_write hash does not describe the current payload",
                        "state_write ORDER BY write_id DESC")

        tables = ["engine_model", "schema_definition", "workflow_definition", "ontology_type",
                  "service_registration", "runtime_context", "runtime_run", "execution_snapshot",
                  "trace", "trace_span", "audit_event", "idempotency_record"]
        result["counts"] = {
            table: connection.execute("SELECT count(*) FROM %s" % table).fetchone()[0]
            for table in tables
        }

        legacy_runs = []
        current_runs_missing_trace = []
        runtime_columns = {row[1] for row in connection.execute("PRAGMA table_info(runtime_run)")}
        has_evidence_identity = {"ontology_version", "ontology_definition_sha256", "data_identity"}.issubset(runtime_columns)
        runtime_query = "SELECT run_id, engine_version, schema_version, workflow_version, trace_id, data_identity"
        if has_evidence_identity:
            runtime_query += ", ontology_type_id, ontology_version, ontology_definition_sha256"
        runtime_query += " FROM runtime_run ORDER BY created_at, run_id"
        for run in connection.execute(runtime_query):
            valid_identity = bool(run["engine_version"]) and run["schema_version"] >= 1 and run["workflow_version"] >= 1
            trace_exists = connection.execute(
                "SELECT 1 FROM trace WHERE trace_id = ? AND run_id = ?", (run["trace_id"], run["run_id"])
            ).fetchone() is not None
            snapshot_count = connection.execute(
                "SELECT count(*) FROM execution_snapshot WHERE run_id = ?", (run["run_id"],)
            ).fetchone()[0]
            current_identity = run["data_identity"] in (None, "ENGINE_RUNTIME_RESULT")
            if has_evidence_identity and current_identity and run["ontology_type_id"] is not None:
                valid_identity = valid_identity and run["ontology_version"] >= 1 and bool(run["ontology_definition_sha256"])
            if valid_identity and not trace_exists:
                current_runs_missing_trace.append(run["run_id"])
            if not valid_identity or not trace_exists or snapshot_count < 2:
                legacy_runs.append(run["run_id"])
        result["legacy_runtime_runs"] = legacy_runs
        result["current_runs_missing_trace"] = current_runs_missing_trace
        if current_runs_missing_trace:
            finding(findings, "P0", "current-version runtime rows have no matching Trace",
                    ", ".join(current_runs_missing_trace))
        if legacy_runs:
            finding(findings, "P1", "runtime rows are evidence-incomplete historical records and cannot support current-run claims",
                    ", ".join(legacy_runs))
        if has_evidence_identity:
            invalid_lifecycle = [row[0] for row in connection.execute(
                "SELECT run_id FROM runtime_run JOIN trace USING (run_id) "
                "WHERE data_identity = 'ENGINE_RUNTIME_RESULT' AND lifecycle NOT IN ('PREPARED', 'COMMITTED')")]
            if invalid_lifecycle:
                finding(findings, "P0", "current runtime rows have an invalid Trace lifecycle",
                        ", ".join(invalid_lifecycle))

        providers = [dict(row) for row in connection.execute(
            "SELECT service_id, provider, status FROM service_registration ORDER BY service_id")]
        result["providers"] = providers
        for provider in providers:
            if provider["service_id"] == "ontology-assembler" and provider["provider"] == "OntologyAssembler":
                finding(findings, "P1", "compatibility projection still names the pre-boundary ontology provider",
                        "service_registration.ontology-assembler.provider=OntologyAssembler")
    connection.close()
    return result


def audit_json(path, findings):
    if not path:
        return None
    if not path.exists():
        finding(findings, "P1", "legacy JSON compatibility view is missing", str(path))
        return {"path": str(path), "exists": False}
    value = read_json(path)
    result = {"path": str(path), "exists": True}
    if "_error" in value:
        finding(findings, "P1", "legacy JSON compatibility view cannot be decoded", "%s: %s" % (path, value["_error"]))
        return result
    result["data_identity"] = value.get("dataIdentity")
    result["engine"] = value.get("engineId") or value.get("engine", {}).get("id")
    if value.get("dataIdentity") == "REPRODUCED_SYSTEM_RUN":
        finding(findings, "P1", "JSON file is a historical compatibility view, not current runtime evidence",
                "%s:dataIdentity=REPRODUCED_SYSTEM_RUN" % path)
    services = value.get("services", [])
    if any(item.get("provider") == "OntologyAssembler" for item in services if isinstance(item, dict)):
        finding(findings, "P1", "JSON compatibility view uses the pre-boundary ontology provider name", str(path))
    return result


def audit_contract(path, findings):
    if not path:
        return None
    if not path.exists():
        finding(findings, "P1", "contract experiment report is missing", str(path))
        return {"path": str(path), "exists": False}
    value = read_json(path)
    result = {"path": str(path), "exists": True}
    if "_error" in value:
        finding(findings, "P1", "contract experiment report cannot be decoded", "%s: %s" % (path, value["_error"]))
        return result
    result.update({key: value.get(key) for key in ("experiment_id", "total", "passed", "failed", "data_identity")})
    if value.get("data_identity") != "ENGINE_EXPERIMENT_RESULT":
        finding(findings, "P1", "contract report has the wrong data identity", str(value.get("data_identity")))
    if value.get("total") != 20 or value.get("passed") != 20 or value.get("failed") != 0:
        finding(findings, "P1", "contract report is not a complete 20-case pass", json.dumps(result, ensure_ascii=False))
    return result


def render(audit):
    lines = ["# 项目状态一致性审计", "", "- 总体状态：%s" % audit["status"],
             "- SQLite：`%s`" % audit["database"]["path"], "- 只读检查：是", ""]
    if audit.get("database", {}).get("schema_version") is not None:
        lines.append("- SQLite schema version：%s" % audit["database"]["schema_version"])
    if audit.get("database", {}).get("revision") is not None:
        lines.append("- SQLite revision：%s" % audit["database"]["revision"])
    lines.append("")
    lines.append("## 发现")
    lines.append("")
    if not audit["findings"]:
        lines.append("- 无")
    else:
        for item in audit["findings"]:
            lines.append("- **%s** %s；证据：`%s`" % (item["severity"], item["message"], item["evidence"]))
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description="Audit SQLite canonical state and derived evidence files without modifying them")
    parser.add_argument("--sqlite", default="data/flexible-engine.db", type=Path)
    parser.add_argument("--legacy-json", default="data/engine-state.json", type=Path)
    parser.add_argument("--contract-report", type=Path)
    parser.add_argument("--expected-schema-version", default=14, type=int)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    findings = []
    audit = {"database": audit_database(args.sqlite, args.expected_schema_version, findings),
             "legacy_json": audit_json(args.legacy_json, findings),
             "contract": audit_contract(args.contract_report, findings),
             "findings": findings}
    audit["status"] = "FAIL" if any(item["severity"] == "P0" for item in findings) else (
        "WARN" if findings else "PASS")
    rendered = render(audit)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        sys.stdout.write(rendered)
    return 1 if audit["status"] == "FAIL" else 0


if __name__ == "__main__":
    sys.exit(main())
