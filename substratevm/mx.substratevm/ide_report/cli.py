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

import json
import os
import re
import tempfile
from argparse import ArgumentParser, ArgumentTypeError

import mx

from .baseline import (
    BASELINE_CASES,
    VALIDATION_CASES,
    BaselineCollectionError,
    collect_baseline,
    verify_disabled_fixture,
)
from .canonicalize import write_canonical, write_sha256
from .compare import check_expectations, compare_bundles, load_expectations
from .envelope import DEFAULT_MAX_DECODED_PAYLOAD_BYTES, MAX_CONFIGURABLE_DECODED_PAYLOAD_BYTES
from .sources import IDEReportSourceError, load_report_source


def _payload_limit(value):
    try:
        limit = int(value)
    except ValueError as error:
        raise ArgumentTypeError("Payload limit must be an integer.") from error
    if limit <= 0 or limit > MAX_CONFIGURABLE_DECODED_PAYLOAD_BYTES:
        raise ArgumentTypeError(
            "Payload limit must be between 1 and {} bytes.".format(MAX_CONFIGURABLE_DECODED_PAYLOAD_BYTES)
        )
    return limit


def _add_payload_limit_argument(parser):
    parser.add_argument(
        "--max-payload-bytes",
        type=_payload_limit,
        default=DEFAULT_MAX_DECODED_PAYLOAD_BYTES,
        help="Maximum decoded envelope payload size (default: 512 MiB; trusted inputs only above that).",
    )


def main(args):
    parser = _create_parser()
    parsed_args = parser.parse_args(args)
    try:
        parsed_args.command_func(parsed_args)
    except IDEReportSourceError as e:
        mx.abort(str(e))
    except BaselineCollectionError as e:
        mx.abort(str(e))


def _create_parser():
    parser = ArgumentParser(
        prog="mx ide-report", description="Inspect, canonicalize, compare, and assert Native Image IDE reports."
    )
    subparsers = parser.add_subparsers(dest="command", metavar="<command>", required=True)

    summarize_parser = subparsers.add_parser(
        "summarize", help="Summarize report families and counts.", description="Summarize report families and counts."
    )
    summarize_parser.add_argument(
        "source", help="IDE report source, for example json:/path/to/native_image_ide_report.json."
    )
    summarize_parser.add_argument("--format", choices=["text", "json"], default="text", help="Output format.")
    _add_payload_limit_argument(summarize_parser)
    summarize_parser.set_defaults(command_func=_summarize)

    query_parser = subparsers.add_parser("query", help="Query report records.", description="Query report records.")
    query_parser.add_argument(
        "source", help="IDE report source, for example json:/path/to/native_image_ide_report.json."
    )
    query_parser.add_argument("--kind", help="Keep only reports with this kind.")
    query_parser.add_argument("--category", help="Keep only reports with this semantic category.")
    query_parser.add_argument("--class", dest="class_name", help="Keep only reports for this exact class.")
    query_parser.add_argument("--class-prefix", help="Keep only reports whose class starts with this prefix.")
    query_parser.add_argument("--source-file", help="Keep only reports with this source file path.")
    query_parser.add_argument("--method", help="Keep only reports for this method name.")
    query_parser.add_argument("--field", help="Keep only reports for this field name.")
    query_parser.add_argument(
        "--message-regex", help="Keep only reports whose message matches this regular expression."
    )
    query_parser.add_argument("--format", choices=["table", "json", "jsonl"], default="table", help="Output format.")
    _add_payload_limit_argument(query_parser)
    query_parser.set_defaults(command_func=_query)

    compare_parser = subparsers.add_parser(
        "compare", help="Compare two IDE report sources.", description="Compare two IDE report sources."
    )
    compare_parser.add_argument("before_source", help="Baseline IDE report source.")
    compare_parser.add_argument("after_source", help="Candidate IDE report source.")
    compare_parser.add_argument("--format", choices=["text", "json"], default="text", help="Output format.")
    compare_parser.add_argument(
        "--no-fail", action="store_true", help="Return successfully even when differences are found."
    )
    _add_payload_limit_argument(compare_parser)
    compare_parser.set_defaults(command_func=_compare)

    assert_parser = subparsers.add_parser(
        "assert",
        help="Assert that a report satisfies an expectation file.",
        description="Assert that a report satisfies an expectation file.",
    )
    assert_parser.add_argument("source", help="IDE report source to check.")
    assert_parser.add_argument("--expect", required=True, help="Expectation JSON file.")
    assert_parser.add_argument("--format", choices=["text", "json"], default="text", help="Output format.")
    _add_payload_limit_argument(assert_parser)
    assert_parser.set_defaults(command_func=_assert_expectations)

    collect_baseline_parser = subparsers.add_parser(
        "collect-baseline",
        help="Collect repeated-run IDE report baselines.",
        description="Collect repeated-run IDE report baselines.",
    )
    collect_baseline_parser.add_argument("output_dir", help="Output baseline directory.")
    collect_baseline_parser.add_argument(
        "--case",
        action="append",
        choices=BASELINE_CASES,
        help="Baseline case to collect. Can be repeated. Defaults to all known cases.",
    )
    collect_baseline_parser.add_argument("--runs", type=int, default=5, help="Number of repeated runs per case.")
    collect_baseline_parser.add_argument(
        "--overwrite", action="store_true", help="Replace existing run directories under the output directory."
    )
    collect_baseline_parser.add_argument(
        "--no-validate", action="store_true", help="Skip validate-baseline after collection."
    )
    collect_baseline_parser.add_argument(
        "--mx", default="mx", help="mx executable to invoke for nested build commands."
    )
    collect_baseline_parser.add_argument("--java-home", help="Optional Java home to pass to nested mx commands.")
    collect_baseline_parser.set_defaults(command_func=_collect_baseline)

    validate_baseline_parser = subparsers.add_parser(
        "validate-baseline",
        help="Validate a repeated-run baseline directory.",
        description="Validate a repeated-run baseline directory.",
    )
    validate_baseline_parser.add_argument(
        "baseline_dir", help="Baseline directory containing <case>/runs/run-XX/canonical.json files."
    )
    validate_baseline_parser.add_argument(
        "--expectation-dir",
        default=_default_expectation_dir(),
        help="Directory containing <case>.json expectation files.",
    )
    validate_baseline_parser.add_argument(
        "--case",
        action="append",
        choices=VALIDATION_CASES,
        help="Baseline case to validate. Can be repeated. Defaults to all known cases.",
    )
    validate_baseline_parser.add_argument("--format", choices=["text", "json"], default="text", help="Output format.")
    validate_baseline_parser.set_defaults(command_func=_validate_baseline)

    smoke_fixture_parser = subparsers.add_parser(
        "smoke-fixture",
        help="Run a gate-scale IDE report fixture smoke.",
        description="Run a gate-scale IDE report fixture smoke.",
    )
    smoke_fixture_parser.add_argument(
        "--output-dir", help="Optional output directory. Defaults to a temporary directory."
    )
    smoke_fixture_parser.add_argument("--mx", default="mx", help="mx executable to invoke for nested build commands.")
    smoke_fixture_parser.add_argument("--java-home", help="Optional Java home to pass to nested mx commands.")
    smoke_fixture_parser.set_defaults(command_func=_smoke_fixture)

    canonicalize_parser = subparsers.add_parser(
        "canonicalize",
        help="Write a canonical IDE report representation.",
        description="Write a canonical IDE report representation.",
    )
    canonicalize_parser.add_argument(
        "source", help="IDE report source to canonicalize, for example json:/path/to/native_image_ide_report.json."
    )
    canonicalize_parser.add_argument("--output", required=True, help="Canonical report output path.")
    canonicalize_parser.add_argument(
        "--payload-scope",
        choices=["full", "minimal"],
        help="Payload scope to write. Defaults to the input canonical scope or full for other sources.",
    )
    canonicalize_parser.add_argument("--sha256-output", help="Optional path for a SHA-256 file over canonical bytes.")
    _add_payload_limit_argument(canonicalize_parser)
    canonicalize_parser.set_defaults(command_func=_canonicalize)

    return parser


def _summarize(parsed_args):
    report_bundle = load_report_source(parsed_args.source, parsed_args.max_payload_bytes)
    summary = _summarize_bundle(report_bundle)
    if parsed_args.format == "json":
        mx.log(json.dumps(summary, indent=2, sort_keys=True))
        return

    mx.log("Source: {}".format(summary["source"]))
    mx.log("Format: {}".format(summary["format"]))
    mx.log("Reports: {}".format(summary["reports"]))
    mx.log("Reports with source locations: {}".format(summary["reports_with_source_locations"]))
    mx.log("Used methods: {}".format(summary["used_methods"]))
    mx.log("Reports by kind:")
    for kind, count in summary["reports_by_kind"]:
        mx.log("  {}\t{}".format(kind, count))


def _query(parsed_args):
    report_bundle = load_report_source(parsed_args.source, parsed_args.max_payload_bytes)
    records = [record for record in report_bundle.reports if _matches_query(record, parsed_args)]
    if parsed_args.format == "json":
        mx.log(json.dumps([record.to_dict() for record in records], indent=2, sort_keys=True))
    elif parsed_args.format == "jsonl":
        for record in records:
            mx.log(json.dumps(record.to_dict(), sort_keys=True))
    else:
        _log_records_table(records)


def _canonicalize(parsed_args):
    report_bundle = load_report_source(parsed_args.source, parsed_args.max_payload_bytes)
    sha256_hash = write_canonical(report_bundle, parsed_args.output, parsed_args.payload_scope)
    if parsed_args.sha256_output:
        write_sha256(sha256_hash, parsed_args.sha256_output)
    mx.log("Wrote canonical IDE report to {}".format(parsed_args.output))
    mx.log("SHA-256: {}".format(sha256_hash))


def _compare(parsed_args):
    before_bundle = load_report_source(parsed_args.before_source, parsed_args.max_payload_bytes)
    after_bundle = load_report_source(parsed_args.after_source, parsed_args.max_payload_bytes)
    result = compare_bundles(before_bundle, after_bundle)
    if parsed_args.format == "json":
        mx.log(json.dumps(result.to_dict(), indent=2, sort_keys=True))
    else:
        _log_compare_result(result)
    if result.has_differences() and not parsed_args.no_fail:
        mx.abort("IDE report comparison found differences.")


def _assert_expectations(parsed_args):
    report_bundle = load_report_source(parsed_args.source, parsed_args.max_payload_bytes)
    expectations = load_expectations(parsed_args.expect)
    result = check_expectations(report_bundle, expectations)
    if parsed_args.format == "json":
        mx.log(json.dumps(result.to_dict(), indent=2, sort_keys=True))
    else:
        _log_expectation_result(result)
    if not result.passed():
        mx.abort("IDE report expectations failed.")


def _collect_baseline(parsed_args):
    collect_baseline(
        parsed_args.output_dir,
        parsed_args.case,
        parsed_args.runs,
        overwrite=parsed_args.overwrite,
        validate=not parsed_args.no_validate,
        mx_command=parsed_args.mx,
        java_home=parsed_args.java_home,
    )


def _validate_baseline(parsed_args):
    results = []
    for case in parsed_args.case or BASELINE_CASES:
        expectation_path = os.path.join(parsed_args.expectation_dir, case + ".json")
        expectations = load_expectations(expectation_path)
        canonical_paths = _baseline_canonical_paths(parsed_args.baseline_dir, case)
        if not canonical_paths:
            raise IDEReportSourceError(
                "No canonical IDE reports found for baseline case '{}' under '{}'.".format(
                    case, parsed_args.baseline_dir
                )
            )
        for canonical_path in canonical_paths:
            source = "canonical:" + canonical_path
            result = check_expectations(load_report_source(source), expectations)
            results.append(
                {
                    "case": case,
                    "source": canonical_path,
                    "result": result.to_dict(),
                }
            )

    if parsed_args.format == "json":
        mx.log(json.dumps(results, indent=2, sort_keys=True))
    else:
        _log_baseline_results(results)

    if any(not result["result"]["passed"] for result in results):
        mx.abort("IDE report baseline validation failed.")


def _smoke_fixture(parsed_args):
    if parsed_args.output_dir:
        collect_baseline(
            parsed_args.output_dir,
            ["fixture"],
            1,
            overwrite=True,
            validate=True,
            mx_command=parsed_args.mx,
            java_home=parsed_args.java_home,
        )
        verify_disabled_fixture(parsed_args.output_dir, mx_command=parsed_args.mx, java_home=parsed_args.java_home)
        mx.log("IDE report fixture smoke passed: {}".format(parsed_args.output_dir))
        return

    with tempfile.TemporaryDirectory(prefix="ide-report-fixture-smoke-") as output_dir:
        collect_baseline(
            output_dir,
            ["fixture"],
            1,
            overwrite=True,
            validate=True,
            mx_command=parsed_args.mx,
            java_home=parsed_args.java_home,
        )
        verify_disabled_fixture(output_dir, mx_command=parsed_args.mx, java_home=parsed_args.java_home)
        mx.log("IDE report fixture smoke passed.")


def _baseline_canonical_paths(baseline_dir, case):
    run_dir = os.path.join(baseline_dir, case, "runs")
    if not os.path.isdir(run_dir):
        return []
    return [
        os.path.join(run_dir, run_name, "canonical.json")
        for run_name in sorted(os.listdir(run_dir))
        if os.path.isfile(os.path.join(run_dir, run_name, "canonical.json"))
    ]


def _default_expectation_dir():
    return os.path.join(os.path.dirname(__file__), "expectations")


def _summarize_bundle(report_bundle):
    reports_by_kind = {}
    for record in report_bundle.reports:
        kind = record.kind if record.kind is not None else "<unknown>"
        reports_by_kind[kind] = reports_by_kind.get(kind, 0) + 1
    return {
        "source": report_bundle.provenance.source,
        "format": report_bundle.provenance.format_name,
        "reports": len(report_bundle.reports),
        "reports_with_source_locations": sum(1 for record in report_bundle.reports if record.has_source_location()),
        "used_methods": len(report_bundle.used_methods),
        "reports_by_kind": sorted(reports_by_kind.items()),
    }


def _matches_query(record, parsed_args):
    if parsed_args.kind and record.kind != parsed_args.kind:
        return False
    if parsed_args.category and record.category != parsed_args.category:
        return False
    if parsed_args.class_name and record.class_name != parsed_args.class_name:
        return False
    if parsed_args.class_prefix and (
        record.class_name is None or not record.class_name.startswith(parsed_args.class_prefix)
    ):
        return False
    if parsed_args.source_file and record.location.filename != parsed_args.source_file:
        return False
    if parsed_args.method and record.method_name != parsed_args.method:
        return False
    if parsed_args.field and record.field_name != parsed_args.field:
        return False
    if parsed_args.message_regex and not re.search(parsed_args.message_regex, record.message or ""):
        return False
    return True


def _log_records_table(records):
    mx.log("kind\tcategory\tlocation\tclass\tmember\tmessage")
    for record in records:
        mx.log(
            "{}\t{}\t{}\t{}\t{}\t{}".format(
                _table_value(record.kind),
                _table_value(record.category),
                _format_location(record),
                _table_value(record.class_name),
                _format_member(record),
                _table_value(record.message),
            )
        )


def _log_compare_result(result):
    summary = result.to_dict()["summary"]["total"]
    mx.log("Before reports: {}".format(result.before_count))
    mx.log("After reports: {}".format(result.after_count))
    mx.log("Before used methods: {}".format(result.before_used_method_count))
    mx.log("After used methods: {}".format(result.after_used_method_count))
    mx.log("Missing: {}".format(summary["missing"]))
    mx.log("Added: {}".format(summary["added"]))
    mx.log("Changed: {}".format(summary["changed"]))
    mx.log("Missing used methods: {}".format(summary["missing_methods"]))
    mx.log("Added used methods: {}".format(summary["added_methods"]))
    if not result.has_differences():
        mx.log("No IDE report differences found.")


def _log_expectation_result(result):
    if result.passed():
        mx.log("IDE report expectations passed.")
        return
    mx.log("IDE report expectations failed.")
    mx.log("Missing records: {}".format(len(result.missing_records)))
    mx.log("Unexpected records: {}".format(len(result.unexpected_records)))
    mx.log("Group count failures: {}".format(len(result.group_count_failures)))
    mx.log("Count failures: {}".format(len(result.count_failures)))


def _log_baseline_results(results):
    mx.log("case\tsource\tpassed\tmissing\tunexpected\tgroup-counts\tcounts")
    for result in results:
        expectation_result = result["result"]
        mx.log(
            "{}\t{}\t{}\t{}\t{}\t{}\t{}".format(
                result["case"],
                result["source"],
                expectation_result["passed"],
                len(expectation_result["missing_records"]),
                len(expectation_result["unexpected_records"]),
                len(expectation_result["group_count_failures"]),
                len(expectation_result["count_failures"]),
            )
        )


def _format_location(record):
    location = record.location
    if location.filename is None:
        return "-"
    if location.line is not None:
        return "{}:{}".format(location.filename, location.line)
    if location.start_line is not None or location.end_line is not None:
        return "{}:{}-{}".format(location.filename, _table_value(location.start_line), _table_value(location.end_line))
    return location.filename


def _format_member(record):
    if record.field_name is not None:
        return record.field_name
    if record.method_name is not None:
        if record.method_signature is not None:
            return "{}{}".format(record.method_name, record.method_signature)
        return record.method_name
    return "-"


def _table_value(value):
    if value is None:
        return "-"
    return str(value).replace("\t", " ").replace("\n", "\\n")
