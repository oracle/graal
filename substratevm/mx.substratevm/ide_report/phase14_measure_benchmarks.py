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

"""Reproduce a Phase 14 large-application measurement matrix."""

import argparse
import collections
import hashlib
import json
import os
import pathlib
import shutil
import statistics
import subprocess
import sys
import time


REPO = pathlib.Path(__file__).resolve().parents[3]
SVM = REPO / "substratevm"
PACKAGE_ROOT = SVM / "mx.substratevm"
BENCHMARK_OUTPUT = REPO / "sdk" / "mxbuild" / "native-image-benchmarks"
OUTPUT_ROOT = pathlib.Path("/tmp/gr61707-phase14-final")

sys.path.insert(0, str(PACKAGE_ROOT))

from ide_report import envelope
from ide_report.canonicalize import canonical_bytes
from ide_report.image import extract_ide_report_envelope
from ide_report.phase14_measure_fixture import payload_contributions
from ide_report.sources import load_report_source


CONFIGURATIONS = (
    ("baseline", None, None),
    ("legacy-export", "legacy", "full"),
    ("canonical-export", "export", "full"),
    ("embed-full", "embed", "full"),
    ("embed-minimal", "embed", "minimal"),
    ("split-full", "split", "full"),
    ("split-minimal", "split", "minimal"),
    ("embed-split-full", "embed,split", "full"),
    ("embed-split-minimal", "embed,split", "minimal"),
)


APPLICATIONS = {
    "dacapo-h2": {
        "benchmark": "dacapo-native-image:h2",
        "filter": None,
        "output_pattern": "dacapo-*-h2-default-ce",
        "cwd": SVM,
        "mx_prefix": ["mx", "--java-home=lookup:default"],
        "benchmark_args": ["--no-scratch"],
    },
}


def parse_arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("application", choices=sorted(APPLICATIONS))
    parser.add_argument("--output-root", type=pathlib.Path, default=OUTPUT_ROOT)
    return parser.parse_args()


def latest_path(paths, description):
    candidates = [path for path in paths if path.exists()]
    if not candidates:
        raise RuntimeError("could not find {}".format(description))
    return max(candidates, key=lambda path: path.stat().st_mtime_ns)


def current_benchmark_output(config):
    return latest_path(BENCHMARK_OUTPUT.glob(config["output_pattern"]), "Native Image benchmark output directory")


def output_image(output_dir):
    image = output_dir / output_dir.name
    if not image.is_file():
        raise RuntimeError("could not find benchmark executable: {}".format(image))
    return image


def generated_candidates(config, output_dir, pattern, started_ns):
    candidates = []
    for root in (output_dir, config["cwd"]):
        candidates.extend(path for path in root.glob(pattern) if path.stat().st_mtime_ns >= started_ns)
    return candidates


def generated_report_path(config, output_dir, storage, started_ns):
    if storage == "legacy":
        pattern = "ide-reports/native_image_ide_report_*.json"
        return latest_path(generated_candidates(config, output_dir, pattern, started_ns), "legacy IDE report")
    if storage == "export":
        pattern = "ide-reports/native_image_ide_report.json"
        return latest_path(generated_candidates(config, output_dir, pattern, started_ns), "canonical IDE report")
    if "split" in storage:
        return latest_path(generated_candidates(config, output_dir, "*.ide-report", started_ns), "split IDE report")
    return None


def archive_generated_report(config, run_dir, report_path):
    archive = run_dir / "report-source" / report_path.name
    archive.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(report_path, archive)
    try:
        report_path.relative_to(config["cwd"])
    except ValueError:
        pass
    else:
        report_path.unlink()
        try:
            report_path.parent.rmdir()
        except OSError:
            pass
    return archive


def report_metrics(config, run_dir, output_dir, storage, started_ns):
    if storage is None:
        return {}

    image = output_image(output_dir)
    envelope_bytes = None
    raw_report_bytes = None
    extraction_samples_ms = []
    split_path = None
    if storage == "legacy":
        report_path = generated_report_path(config, output_dir, storage, started_ns)
        report_path = archive_generated_report(config, run_dir, report_path)
        source = "json:" + str(report_path)
        raw_report_bytes = report_path.stat().st_size
    elif storage == "export":
        report_path = generated_report_path(config, output_dir, storage, started_ns)
        report_path = archive_generated_report(config, run_dir, report_path)
        source = "canonical:" + str(report_path)
        raw_report_bytes = report_path.stat().st_size
    elif "embed" in storage:
        source = "image:" + str(image)
        envelope_bytes = extract_ide_report_envelope(image)
        for _ in range(25):
            start = time.perf_counter_ns()
            extract_ide_report_envelope(image)
            extraction_samples_ms.append((time.perf_counter_ns() - start) / 1_000_000)
        if "split" in storage:
            split_path = generated_report_path(config, output_dir, storage, started_ns)
            if envelope_bytes != split_path.read_bytes():
                raise RuntimeError("same-build embedded and split envelopes differ")
    else:
        split_path = generated_report_path(config, output_dir, storage, started_ns)
        source = "split:" + str(split_path)
        envelope_bytes = split_path.read_bytes()

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
            raise RuntimeError("decoded envelope and canonical bundle differ")
        result.update(
            {
                "envelope_bytes": len(envelope_bytes),
                "envelope_sha256": hashlib.sha256(envelope_bytes).hexdigest(),
                "compression": decoded.compression,
            }
        )
    if split_path is not None:
        result["split_side_file_bytes"] = split_path.stat().st_size
    if extraction_samples_ms:
        result["extraction_median_ms"] = statistics.median(extraction_samples_ms)
        result["extraction_min_ms"] = min(extraction_samples_ms)
    return result


def benchmark_command(config, run_dir, storage, scope):
    command = config["mx_prefix"] + [
        "benchmark",
        config["benchmark"],
        "--results-file",
        str(run_dir / "bench-results.json"),
        "--",
    ]
    command += config["benchmark_args"]
    command += [
        "--jvm=native-image",
        "--jvm-config=default-ce",
        "-Dnative-image.benchmark.stages=image",
    ]
    if storage == "legacy":
        command.append("-Dnative-image.benchmark.extra-image-build-argument=-H:+IDEReport")
    elif storage is not None:
        command += [
            "-Dnative-image.benchmark.extra-image-build-argument=-H:IDEReportStorage={}".format(storage),
            "-Dnative-image.benchmark.extra-image-build-argument=-H:IDEReportPayloadScope={}".format(scope),
        ]
    if storage is not None and config["filter"] is not None:
        command.append(
            "-Dnative-image.benchmark.extra-image-build-argument=-H:IDEReportFiltered={}".format(config["filter"])
        )
    command.append("--")
    return command


def run_one(application, config, output, name, storage, scope):
    run_dir = output / name
    run_dir.mkdir(parents=True, exist_ok=True)
    command = benchmark_command(config, run_dir, storage, scope)
    started_ns = time.time_ns()
    print("START {} {}".format(application, name), flush=True)
    start = time.monotonic()
    with (run_dir / "build.log").open("w", encoding="utf-8") as log:
        completed = subprocess.run(command, cwd=config["cwd"], stdout=log, stderr=subprocess.STDOUT, text=True)
    wall_seconds = time.monotonic() - start
    if completed.returncode != 0:
        tail = (run_dir / "build.log").read_text(encoding="utf-8", errors="replace").splitlines()[-100:]
        raise RuntimeError("{} failed:\n{}".format(name, "\n".join(tail)))

    benchmark_output = current_benchmark_output(config)
    image = output_image(benchmark_output)
    build_output_path = latest_path(benchmark_output.rglob("build-output-final.json"), "build output JSON")
    statistics_path = latest_path(
        benchmark_output.rglob("image_build_statistics-final.json"), "image build statistics JSON"
    )
    build_output = json.loads(build_output_path.read_text(encoding="utf-8"))
    statistics_json = json.loads(statistics_path.read_text(encoding="utf-8"))
    timer_metrics = {
        key: value for key, value in statistics_json.items() if key.startswith("ide-report-") and key.endswith("_time")
    }
    result = {
        "application": application,
        "configuration": name,
        "storage": storage,
        "scope": scope,
        "command": command,
        "wall_seconds": wall_seconds,
        "native_image_total_seconds": build_output["resource_usage"]["total_secs"],
        "analysis_milliseconds": statistics_json["analysis_time"],
        "peak_rss_bytes": build_output["resource_usage"]["memory"]["peak_rss_bytes"],
        "image_bytes": os.path.getsize(image),
        "image_total_bytes_metric": build_output["image_details"]["total_bytes"],
        "timers_milliseconds": timer_metrics,
        "report": report_metrics(config, run_dir, benchmark_output, storage, started_ns),
    }
    (run_dir / "measurement.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(
        "DONE {} {} total={:.3f}s image={}".format(
            application, name, result["native_image_total_seconds"], result["image_bytes"]
        ),
        flush=True,
    )
    return result


def summarize(results):
    baseline = results[0]
    summary = {}
    for result in results:
        summary[result["configuration"]] = {
            "wall_seconds": result["wall_seconds"],
            "native_image_total_seconds": result["native_image_total_seconds"],
            "total_time_delta_seconds_from_baseline": result["native_image_total_seconds"]
            - baseline["native_image_total_seconds"],
            "analysis_milliseconds": result["analysis_milliseconds"],
            "analysis_time_delta_milliseconds_from_baseline": result["analysis_milliseconds"]
            - baseline["analysis_milliseconds"],
            "peak_rss_bytes": result["peak_rss_bytes"],
            "image_bytes": result["image_bytes"],
            "image_delta_bytes_from_baseline": result["image_bytes"] - baseline["image_bytes"],
            "payload_bytes": result["report"].get("canonical_payload_bytes"),
            "envelope_bytes": result["report"].get("envelope_bytes"),
            "split_side_file_bytes": result["report"].get("split_side_file_bytes"),
            "extraction_median_ms": result["report"].get("extraction_median_ms"),
            "timers_milliseconds": result["timers_milliseconds"],
        }
    return summary


def main():
    arguments = parse_arguments()
    application = arguments.application
    config = APPLICATIONS[application]
    output = arguments.output_root / application
    output.mkdir(parents=True, exist_ok=True)

    results = []
    for configuration in CONFIGURATIONS:
        measurement = output / configuration[0] / "measurement.json"
        if measurement.exists():
            print("REUSE {} {}".format(application, configuration[0]), flush=True)
            results.append(json.loads(measurement.read_text(encoding="utf-8")))
        else:
            results.append(run_one(application, config, output, *configuration))
    manifest = {
        "application": application,
        "runs_per_configuration": 1,
        "results": results,
        "summary": summarize(results),
    }
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("MANIFEST {}".format(output / "manifest.json"), flush=True)


if __name__ == "__main__":
    main()
