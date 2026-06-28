#!/usr/bin/env python3

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

"""Reproduce the Phase 14 focused-fixture measurement matrix."""

import argparse
import collections
import hashlib
import json
import os
import pathlib
import statistics
import subprocess
import sys
import time


REPO = pathlib.Path(__file__).resolve().parents[3]
SVM = REPO / "substratevm"
PACKAGE_ROOT = SVM / "mx.substratevm"
DEFAULT_OUTPUT = pathlib.Path("/tmp/gr61707-phase14-final/fixture")
FILTER = "com.oracle.svm.test.ide"
RUNS = 3

sys.path.insert(0, str(PACKAGE_ROOT))

from ide_report import envelope
from ide_report.canonicalize import canonical_bytes, canonical_payload
from ide_report.image import extract_ide_report_envelope
from ide_report.sources import load_report_source


CONFIGURATIONS = (
    ("baseline", None, None, False),
    ("legacy-export", "legacy", "full", True),
    ("canonical-export", "export", "full", True),
    ("embed-full", "embed", "full", True),
    ("embed-minimal", "embed", "minimal", True),
    ("split-full", "split", "full", True),
    ("split-minimal", "split", "minimal", True),
    ("embed-split-full", "embed,split", "full", True),
    ("embed-split-minimal", "embed,split", "minimal", True),
)


def parse_arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-directory", type=pathlib.Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def encoded_payload_size(payload):
    return len(
        (json.dumps(payload, indent=2, sort_keys=True, separators=(",", ": "), ensure_ascii=False) + "\n").encode(
            "utf-8"
        )
    )


def payload_contributions(bundle):
    payload = canonical_payload(bundle)
    total = encoded_payload_size(payload)
    without_used = dict(payload, used_methods=[])
    inlined_records = [record for record in payload["records"] if record.get("category") == "inlined-only-method"]
    without_inlined_records = dict(
        payload, records=[record for record in payload["records"] if record.get("category") != "inlined-only-method"]
    )
    inlined_identities = {
        (record.get("filename"), record.get("class"), record.get("mthname"), record.get("mthsig"))
        for record in inlined_records
    }
    inlined_methods = [
        method
        for method in payload["used_methods"]
        if (method.get("filename"), method.get("class"), method.get("mthname"), method.get("mthsig"))
        in inlined_identities
    ]
    compiled_methods = [method for method in payload["used_methods"] if method not in inlined_methods]
    without_inlined_methods = dict(
        payload, used_methods=[method for method in payload["used_methods"] if method not in inlined_methods]
    )
    without_compiled_methods = dict(
        payload, used_methods=[method for method in payload["used_methods"] if method not in compiled_methods]
    )
    return {
        "payload_bytes": total,
        "used_methods_count": len(payload["used_methods"]),
        "used_methods_bytes": total - encoded_payload_size(without_used),
        "compiled_methods_count": len(compiled_methods),
        "compiled_methods_bytes": total - encoded_payload_size(without_compiled_methods),
        "inlined_only_methods_count": len(inlined_methods),
        "inlined_only_method_inventory_bytes": total - encoded_payload_size(without_inlined_methods),
        "inlined_only_records_count": len(inlined_records),
        "inlined_only_record_bytes": total - encoded_payload_size(without_inlined_records),
    }


def report_metrics(run_dir, storage):
    if storage is None:
        return {}
    image = run_dir / "ide-report-fixture"
    envelope_bytes = None
    raw_report_bytes = None
    extraction_samples_ms = []
    if storage == "legacy":
        reports = list((run_dir / "ide-reports").glob("native_image_ide_report_*.json"))
        if len(reports) != 1:
            raise RuntimeError("expected one legacy report in {}".format(run_dir))
        source = "json:" + str(reports[0])
        raw_report_bytes = reports[0].stat().st_size
    elif storage == "export":
        report = run_dir / "ide-reports" / "native_image_ide_report.json"
        source = "canonical:" + str(report)
        raw_report_bytes = report.stat().st_size
    elif "embed" in storage:
        source = "image:" + str(image)
        envelope_bytes = extract_ide_report_envelope(image)
        for _ in range(25):
            start = time.perf_counter_ns()
            extract_ide_report_envelope(image)
            extraction_samples_ms.append((time.perf_counter_ns() - start) / 1_000_000)
        if "split" in storage:
            split_bytes = (run_dir / "ide-report-fixture.ide-report").read_bytes()
            if envelope_bytes != split_bytes:
                raise RuntimeError("same-build embedded and split envelopes differ in {}".format(run_dir))
    else:
        report = run_dir / "ide-report-fixture.ide-report"
        source = "split:" + str(report)
        envelope_bytes = report.read_bytes()

    bundle = load_report_source(source)
    canonical = canonical_bytes(bundle)
    result = {
        "record_count": len(bundle.reports),
        "records_by_kind": dict(sorted(collections.Counter(record.kind for record in bundle.reports).items())),
        "records_by_category": dict(sorted(collections.Counter(record.category for record in bundle.reports).items())),
        "canonical_payload_bytes": len(canonical),
        "canonical_payload_sha256": hashlib.sha256(canonical).hexdigest(),
        "raw_report_bytes": raw_report_bytes,
        "contributions": payload_contributions(bundle),
    }
    if envelope_bytes is not None:
        decoded = envelope.decode(envelope_bytes)
        if decoded.payload != canonical:
            raise RuntimeError("decoded envelope and canonical bundle differ in {}".format(run_dir))
        result.update(
            {
                "envelope_bytes": len(envelope_bytes),
                "envelope_sha256": hashlib.sha256(envelope_bytes).hexdigest(),
                "compression": decoded.compression,
            }
        )
    split_path = run_dir / "ide-report-fixture.ide-report"
    if split_path.exists():
        result["split_side_file_bytes"] = split_path.stat().st_size
    if extraction_samples_ms:
        result["extraction_median_ms"] = statistics.median(extraction_samples_ms)
        result["extraction_min_ms"] = min(extraction_samples_ms)
    return result


def run_one(output, name, storage, scope, filtered, run_number):
    run_dir = output / name / "run-{}".format(run_number)
    run_dir.mkdir(parents=True)
    build_output_path = run_dir / "build-output.json"
    command = [
        "mx",
        "--java-home=lookup:default",
        "ide-report-fixture",
        "--output-path",
        str(run_dir),
        "--build-only",
    ]
    if filtered:
        command += ["--ide-report-filter", FILTER]
    command += [
        "--",
        "-H:+UnlockExperimentalVMOptions",
        "-H:+CollectImageBuildStatistics",
        "-H:BuildOutputJSONFile={}".format(build_output_path),
        "-H:+GenerateBuildArtifactsFile",
    ]
    if storage not in (None, "legacy"):
        command += ["-H:IDEReportStorage={}".format(storage), "-H:IDEReportPayloadScope={}".format(scope)]
    command += ["-H:-UnlockExperimentalVMOptions"]

    print("START {} run {}".format(name, run_number), flush=True)
    start = time.monotonic()
    with (run_dir / "build.log").open("w", encoding="utf-8") as log:
        completed = subprocess.run(command, cwd=SVM, stdout=log, stderr=subprocess.STDOUT, text=True)
    wall_seconds = time.monotonic() - start
    if completed.returncode != 0:
        tail = (run_dir / "build.log").read_text(encoding="utf-8", errors="replace").splitlines()[-80:]
        raise RuntimeError("{} failed:\n{}".format(name, "\n".join(tail)))

    build_output = json.loads(build_output_path.read_text(encoding="utf-8"))
    stats_path = run_dir / "reports" / "image_build_statistics.json"
    statistics_json = json.loads(stats_path.read_text(encoding="utf-8"))
    timer_metrics = {
        key: value for key, value in statistics_json.items() if key.startswith("ide-report-") and key.endswith("_time")
    }
    result = {
        "application": "fixture",
        "configuration": name,
        "storage": storage,
        "scope": scope,
        "run": run_number,
        "command": command,
        "wall_seconds": wall_seconds,
        "native_image_total_seconds": build_output["resource_usage"]["total_secs"],
        "analysis_milliseconds": statistics_json["analysis_time"],
        "peak_rss_bytes": build_output["resource_usage"]["memory"]["peak_rss_bytes"],
        "image_bytes": os.path.getsize(run_dir / "ide-report-fixture"),
        "image_total_bytes_metric": build_output["image_details"]["total_bytes"],
        "timers_milliseconds": timer_metrics,
        "report": report_metrics(run_dir, storage),
    }
    (run_dir / "measurement.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(
        "DONE {} run {} total={:.3f}s image={}".format(
            name, run_number, result["native_image_total_seconds"], result["image_bytes"]
        ),
        flush=True,
    )
    return result


def median_for(results, path):
    values = []
    for result in results:
        value = result
        for component in path:
            value = value.get(component) if isinstance(value, dict) else None
            if value is None:
                break
        if isinstance(value, (int, float)):
            values.append(value)
    return statistics.median(values) if values else None


def main():
    output = parse_arguments().output_directory
    if output.exists():
        raise RuntimeError("output already exists: {}".format(output))
    output.mkdir(parents=True)
    results = []
    for name, storage, scope, filtered in CONFIGURATIONS:
        for run_number in range(1, RUNS + 1):
            results.append(run_one(output, name, storage, scope, filtered, run_number))

    grouped = collections.defaultdict(list)
    for result in results:
        grouped[result["configuration"]].append(result)
    baseline_image = median_for(grouped["baseline"], ("image_bytes",))
    baseline_total = median_for(grouped["baseline"], ("native_image_total_seconds",))
    baseline_analysis = median_for(grouped["baseline"], ("analysis_milliseconds",))
    summary = {}
    for name, rows in grouped.items():
        image_bytes = median_for(rows, ("image_bytes",))
        total_seconds = median_for(rows, ("native_image_total_seconds",))
        analysis_ms = median_for(rows, ("analysis_milliseconds",))
        summary[name] = {
            "runs": len(rows),
            "median_wall_seconds": median_for(rows, ("wall_seconds",)),
            "median_native_image_total_seconds": total_seconds,
            "median_analysis_milliseconds": analysis_ms,
            "median_peak_rss_bytes": median_for(rows, ("peak_rss_bytes",)),
            "median_image_bytes": image_bytes,
            "image_delta_bytes_from_baseline": image_bytes - baseline_image,
            "total_time_delta_seconds_from_baseline": total_seconds - baseline_total,
            "analysis_time_delta_milliseconds_from_baseline": analysis_ms - baseline_analysis,
            "median_payload_bytes": median_for(rows, ("report", "canonical_payload_bytes")),
            "median_envelope_bytes": median_for(rows, ("report", "envelope_bytes")),
            "median_split_side_file_bytes": median_for(rows, ("report", "split_side_file_bytes")),
            "median_extraction_ms": median_for(rows, ("report", "extraction_median_ms")),
            "median_snapshot_ms": median_for(rows, ("timers_milliseconds", "ide-report-snapshot_time")),
            "median_serialization_ms": median_for(rows, ("timers_milliseconds", "ide-report-serialization_time")),
            "median_compression_ms": median_for(rows, ("timers_milliseconds", "ide-report-compression_time")),
            "median_embedding_ms": median_for(rows, ("timers_milliseconds", "ide-report-embedding_time")),
            "median_split_write_ms": median_for(rows, ("timers_milliseconds", "ide-report-split-write_time")),
            "median_canonical_write_ms": median_for(rows, ("timers_milliseconds", "ide-report-canonical-write_time")),
            "median_legacy_write_ms": median_for(rows, ("timers_milliseconds", "ide-report-legacy-write_time")),
        }
    manifest = {"application": "fixture", "runs_per_configuration": RUNS, "results": results, "summary": summary}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("MANIFEST {}".format(output / "manifest.json"), flush=True)


if __name__ == "__main__":
    main()
