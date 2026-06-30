#
# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

"""Repeated-run IDE report baseline collection."""

from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import subprocess


BASELINE_CASES = ("fixture", "helloworld", "dacapo-h2")
VALIDATION_CASES = BASELINE_CASES + ("spring-petclinic",)


class BaselineCollectionError(RuntimeError):
    pass


def collect_baseline(output_dir, cases, runs, overwrite=False, validate=True, mx_command="mx", java_home=None):
    if runs < 1:
        raise BaselineCollectionError("Baseline collection requires at least one run.")

    repo = Path(__file__).resolve().parents[3]
    output = _resolve_output_dir(output_dir, repo)
    mx_base = _mx_base(mx_command, java_home, repo / "substratevm")
    requested_cases = list(cases or BASELINE_CASES)
    results = {}

    for case in requested_cases:
        collector = _collector_for(case)
        manifests = []
        for run_id in range(1, runs + 1):
            manifests.append(collector(repo, output, mx_base, run_id, overwrite))
        compare_summary = _compare_case(output, mx_base, case, runs)
        case_manifest = {
            "case": case,
            "runs": manifests,
            "determinism": compare_summary,
        }
        _write_json(output / case / "manifest.json", case_manifest)
        results[case] = case_manifest

    _write_json(output / "local-manifest.json", results)
    if validate:
        _run(mx_base + ["ide-report", "validate-baseline", str(output)] + _case_args(requested_cases), cwd=repo)
    return results


def verify_disabled_fixture(output_dir, mx_command="mx", java_home=None):
    repo = Path(__file__).resolve().parents[3]
    output = _resolve_output_dir(output_dir, repo)
    run_dir = output / "fixture-disabled"
    if run_dir.exists():
        shutil.rmtree(run_dir)
    run_dir.mkdir(parents=True)
    mx_base = _mx_base(mx_command, java_home, repo / "substratevm")
    _run(
        mx_base
        + [
            "ide-report-fixture",
            "--output-path",
            str(run_dir),
            "--build-only",
        ],
        cwd=repo,
        log_path=run_dir / "build.log",
    )
    _assert_no_ide_reports(run_dir)


def _assert_no_ide_reports(run_dir):
    reports_dir = Path(run_dir) / "ide-reports"
    if not reports_dir.exists():
        return
    if not reports_dir.is_dir():
        raise BaselineCollectionError(
            "IDE reporting was disabled, but report output was generated: {}.".format(reports_dir)
        )
    outputs = sorted(reports_dir.iterdir())
    if outputs:
        raise BaselineCollectionError(
            "IDE reporting was disabled, but report output was generated: {}.".format(
                ", ".join(str(output) for output in outputs)
            )
        )


def _resolve_output_dir(output_dir, repo):
    output = Path(output_dir)
    if not output.is_absolute():
        output = repo / output
    output.mkdir(parents=True, exist_ok=True)
    return output


def _mx_base(mx_command, java_home, svm_suite):
    command = [mx_command]
    if java_home is not None:
        command += ["--java-home", java_home]
    return command + ["-p", str(svm_suite)]


def _collector_for(case):
    collectors = {
        "fixture": _collect_fixture,
        "helloworld": _collect_helloworld,
        "dacapo-h2": _collect_dacapo_h2,
    }
    return collectors[case]


def _collect_fixture(repo, output, mx_base, run_id, overwrite):
    run_dir = _prepare_run_dir(output, "fixture", run_id, overwrite)
    _run(
        mx_base
        + [
            "ide-report-fixture",
            "--output-path",
            str(run_dir),
            "--build-only",
            "--ide-report-filter",
            "com.oracle.svm.test.ide",
        ],
        cwd=repo,
        log_path=run_dir / "build.log",
    )
    return _postprocess(output, "fixture", run_id, run_dir, _latest_report(run_dir), mx_base, repo)


def _collect_helloworld(repo, output, mx_base, run_id, overwrite):
    run_dir = _prepare_run_dir(output, "helloworld", run_id, overwrite)
    _run(
        mx_base
        + [
            "helloworld",
            "--output-path",
            str(run_dir),
            "--build-only",
            "--",
            "-H:+UnlockExperimentalVMOptions",
            "-H:+IDEReport",
            "-H:IDEReportFiltered=HelloWorld",
            "-H:-UnlockExperimentalVMOptions",
        ],
        cwd=run_dir,
        log_path=run_dir / "build.log",
    )
    return _postprocess(output, "helloworld", run_id, run_dir, _latest_report(run_dir), mx_base, repo)


def _collect_dacapo_h2(repo, output, mx_base, run_id, overwrite):
    run_dir = _prepare_run_dir(output, "dacapo-h2", run_id, overwrite)
    _run(
        mx_base
        + [
            "benchmark",
            "dacapo-native-image:h2",
            "--results-file",
            str(run_dir / "bench-results.json"),
            "--",
            "--no-scratch",
            "--jvm=native-image",
            "--jvm-config=default-ce",
            "-Dnative-image.benchmark.stages=image",
            "-Dnative-image.benchmark.extra-image-build-argument=-H:+IDEReport",
            "--",
        ],
        cwd=run_dir,
        log_path=run_dir / "build.log",
    )
    return _postprocess(output, "dacapo-h2", run_id, run_dir, _latest_report(run_dir), mx_base, repo)


def _prepare_run_dir(output, case, run_id, overwrite):
    run_dir = output / case / "runs" / "run-{:02d}".format(run_id)
    if run_dir.exists():
        if not overwrite:
            raise BaselineCollectionError(
                "Output directory already exists: {}. Use --overwrite to replace it.".format(run_dir)
            )
        shutil.rmtree(run_dir)
    run_dir.mkdir(parents=True)
    return run_dir


def _latest_report(run_dir):
    reports = sorted((run_dir / "ide-reports").glob("native_image_ide_report_*.json"))
    if len(reports) != 1:
        raise BaselineCollectionError("Expected exactly one IDE report in {}, found {}.".format(run_dir, len(reports)))
    return reports[0]


def _copy_latest_report(source_dir, target_dir, excluded_reports=None):
    excluded_reports = set() if excluded_reports is None else set(excluded_reports)
    reports = sorted(
        report for report in source_dir.glob("native_image_ide_report_*.json") if report not in excluded_reports
    )
    if len(reports) != 1:
        raise BaselineCollectionError(
            "Expected exactly one IDE report in {}, found {}.".format(source_dir, len(reports))
        )
    target_dir.mkdir(parents=True, exist_ok=True)
    target = target_dir / reports[0].name
    shutil.copy2(reports[0], target)
    return target


def _postprocess(output, case, run_id, run_dir, raw_report, mx_base, repo):
    canonical = run_dir / "canonical.json"
    sha256 = run_dir / "canonical.sha256"
    summary = run_dir / "summary.json"
    _run(
        mx_base
        + [
            "ide-report",
            "canonicalize",
            "json:" + str(raw_report),
            "--output",
            str(canonical),
            "--sha256-output",
            str(sha256),
        ],
        cwd=repo,
        log_path=run_dir / "canonicalize.log",
    )
    summary_process = _run(
        mx_base + ["ide-report", "summarize", "canonical:" + str(canonical), "--format", "json"],
        cwd=repo,
        log_path=run_dir / "summary.log",
    )
    summary.write_text(summary_process.stdout)
    manifest = {
        "case": case,
        "run": run_id,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "raw_report": str(raw_report.relative_to(output)),
        "canonical_report": str(canonical.relative_to(output)),
        "sha256": sha256.read_text().strip(),
        "summary": json.loads(summary.read_text()),
    }
    _write_json(run_dir / "manifest.json", manifest)
    return manifest


def _compare_case(output, mx_base, case, runs):
    runs_dir = output / case / "runs"
    baseline = runs_dir / "run-01" / "canonical.json"
    comparisons = []
    for run_id in range(2, runs + 1):
        candidate = runs_dir / "run-{:02d}".format(run_id) / "canonical.json"
        output_path = runs_dir / "run-{:02d}".format(run_id) / "compare-to-run-01.json"
        process = _run(
            mx_base
            + [
                "ide-report",
                "compare",
                "canonical:" + str(baseline),
                "canonical:" + str(candidate),
                "--format",
                "json",
                "--no-fail",
            ],
            cwd=output,
        )
        output_path.write_text(process.stdout)
        data = json.loads(output_path.read_text())
        comparisons.append({"run": run_id, "summary": data["summary"]})
    return {
        "case": case,
        "baseline": str(baseline.relative_to(output)),
        "comparisons": comparisons,
    }


def _run(command, cwd, log_path=None):
    print("$ " + " ".join(command), flush=True)
    process = subprocess.run(command, cwd=str(cwd), text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if log_path is not None:
        log_path.parent.mkdir(parents=True, exist_ok=True)
        log_path.write_text(process.stdout)
    if process.returncode != 0:
        if log_path is None:
            print(process.stdout)
        message = "Command failed with exit code {}: {}".format(process.returncode, " ".join(command))
        if log_path is not None:
            message += ". See {}".format(log_path)
        raise BaselineCollectionError(message)
    return process


def _case_args(cases):
    args = []
    for case in cases:
        args += ["--case", case]
    return args


def _write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")
