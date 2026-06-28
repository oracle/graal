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
import hashlib
import os
import struct
import sys
import tempfile
import types
import unittest

from pathlib import Path

if "mx" not in sys.modules:

    def _abort(message):
        raise RuntimeError(message)

    sys.modules["mx"] = types.SimpleNamespace(log=lambda _message: None, abort=_abort)

from . import baseline, cli, elf, image, macho
from .canonicalize import canonical_bytes, canonical_payload, write_canonical, write_sha256
from .compare import check_expectations, compare_bundles, load_expectations, record_identity
from . import envelope
from .model import MethodReference, ReportBundle, ReportProvenance, ReportRecord, SourceLocation
from .sources import IDEReportSourceError, load_json_report, load_report_source, parse_source_uri


class IDEReportModelTests(unittest.TestCase):
    def test_report_bundle_serializes_minimal_report(self):
        bundle = ReportBundle(
            provenance=ReportProvenance(source="json:/tmp/report.json", format_name="json"),
            reports=[
                ReportRecord(kind="LINE", location=SourceLocation(filename="demo/App.java", line=7), message="message")
            ],
        )

        serialized = bundle.to_dict()

        self.assertEqual("json", serialized["provenance"]["format"])
        self.assertEqual("LINE", serialized["reports"][0]["kind"])
        self.assertEqual("demo/App.java", serialized["reports"][0]["filename"])
        self.assertEqual(7, serialized["reports"][0]["line"])


class IDEReportEnvelopeTests(unittest.TestCase):
    def test_small_payload_roundtrip_is_deterministic(self):
        payload = b"small canonical payload"

        encoded = envelope.encode(payload, "test-producer")
        decoded = envelope.decode(encoded)

        self.assertEqual(envelope.COMPRESSION_NONE, decoded.compression)
        self.assertEqual(payload, decoded.payload)
        self.assertEqual(encoded, envelope.encode(payload, "test-producer"))
        self.assertEqual(
            "277a73735a667a66266afc3f89ecdae44ad49762b185f58bed316d060ae43ed6", hashlib.sha256(encoded).hexdigest()
        )

    def test_large_payload_uses_deterministic_gzip(self):
        payload = b"a" * (envelope.COMPRESSION_THRESHOLD * 2)

        encoded = envelope.encode(payload, "test-producer")
        decoded = envelope.decode(encoded)

        self.assertEqual(envelope.COMPRESSION_GZIP, decoded.compression)
        self.assertEqual(payload, decoded.payload)
        self.assertEqual(encoded, envelope.encode(payload, "test-producer"))
        self.assertEqual(
            "a644cb1673f39f4d758abe4457d7649627e8d0d2fe3e13bb9243691481bab701", hashlib.sha256(encoded).hexdigest()
        )

    def test_corrupt_and_truncated_envelopes_are_rejected(self):
        encoded = envelope.encode(b"payload", "test-producer")
        corrupted = encoded[:-1] + bytes([encoded[-1] ^ 1])

        with self.assertRaises(ValueError):
            envelope.decode(corrupted)
        with self.assertRaises(ValueError):
            envelope.decode(encoded[:-1])


class IDEReportSourceTests(unittest.TestCase):
    def test_json_source_loads_reports_and_used_methods(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            report_path = os.path.join(temp_dir, "report.json")
            with open(report_path, "w", encoding="utf-8") as report_file:
                json.dump(
                    {
                        "reports": [
                            {
                                "kind": "LINE",
                                "filename": "demo/App.java",
                                "line": 12,
                                "class": "demo.App",
                                "msg": "reflection target resolved",
                                "extra": "preserved",
                            }
                        ],
                        "used_methods": [
                            {
                                "filename": "demo/App.java",
                                "class": "demo.Helper",
                                "mthname": "work",
                                "mthsig": "()V",
                                "method-extra": True,
                            }
                        ],
                        "top-level-extra": 1,
                    },
                    report_file,
                )

            bundle = load_report_source("json:" + report_path)

        self.assertEqual(1, len(bundle.reports))
        self.assertEqual(1, len(bundle.used_methods))
        self.assertEqual("preserved", bundle.reports[0].extensions["extra"])
        self.assertTrue(bundle.used_methods[0].extensions["method-extra"])
        self.assertEqual(1, bundle.extensions["top-level-extra"])

    def test_parse_source_uri_rejects_unknown_scheme(self):
        with self.assertRaises(IDEReportSourceError):
            parse_source_uri("unknown:/tmp/report.json")

    def test_parse_source_uri_requires_scheme(self):
        with self.assertRaises(IDEReportSourceError):
            parse_source_uri("/tmp/report.json")

    def test_auto_source_detects_raw_and_canonical_json(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            raw_path = os.path.join(temp_dir, "raw.json")
            canonical_path = os.path.join(temp_dir, "canonical.json")
            with open(raw_path, "w", encoding="utf-8") as report_file:
                json.dump(_sample_report(), report_file)
            with open(canonical_path, "wb") as report_file:
                report_file.write(canonical_bytes(_load_raw_report(_sample_report())))

            raw_bundle = load_report_source("auto:" + raw_path)
            canonical_bundle = load_report_source("auto:" + canonical_path)

        self.assertEqual("json", raw_bundle.provenance.format_name)
        self.assertEqual("canonical", canonical_bundle.provenance.format_name)
        self.assertEqual(canonical_bytes(raw_bundle), canonical_bytes(canonical_bundle))

    def test_auto_source_prefers_embedded_report_and_checks_split_copy(self):
        payload = canonical_bytes(_load_raw_report(_sample_report()))
        embedded_report = envelope.encode(payload, "embedded-producer")
        different_split_report = envelope.encode(payload, "split-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "demo")
            with open(image_path, "wb") as image_file:
                image_file.write(_macho_image(embedded_report))
            with open(image_path + ".ide-report", "wb") as split_file:
                split_file.write(different_split_report)

            with self.assertWarnsRegex(RuntimeWarning, "differs"):
                bundle = load_report_source("auto:" + image_path)

        self.assertEqual("image", bundle.provenance.format_name)
        self.assertEqual(payload, canonical_bytes(bundle))

    def test_auto_source_falls_back_to_image_adjacent_split_report(self):
        payload = canonical_bytes(_load_raw_report(_sample_report()))
        encoded_report = envelope.encode(payload, "test-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "demo")
            with open(image_path, "wb") as image_file:
                image_file.write(_macho_image(encoded_report, include_report_section=False))
            with open(image_path + ".ide-report", "wb") as split_file:
                split_file.write(encoded_report)

            bundle = load_report_source("auto:" + image_path)

        self.assertEqual("split", bundle.provenance.format_name)
        self.assertEqual(payload, canonical_bytes(bundle))

    def test_auto_source_uses_build_artifacts_entry(self):
        payload = canonical_bytes(_load_raw_report(_sample_report()))
        encoded_report = envelope.encode(payload, "test-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            report_dir = os.path.join(temp_dir, "reports")
            os.mkdir(report_dir)
            report_path = os.path.join(report_dir, "demo.ide-report")
            with open(report_path, "wb") as split_file:
                split_file.write(encoded_report)
            with open(os.path.join(temp_dir, "build-artifacts.json"), "w", encoding="utf-8") as artifact_file:
                json.dump({"ide_report": ["reports/demo.ide-report"]}, artifact_file)

            bundle = load_report_source("auto:" + os.path.join(temp_dir, "demo"))

        self.assertEqual("split", bundle.provenance.format_name)
        self.assertEqual(payload, canonical_bytes(bundle))

    def test_auto_source_lists_searched_locations(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "missing")
            with self.assertRaisesRegex(IDEReportSourceError, "missing.ide-report"):
                load_report_source("auto:" + image_path)

    def test_image_source_extracts_mach_o_envelope(self):
        payload = canonical_bytes(_load_raw_report(_sample_report()))
        encoded_report = envelope.encode(payload, "test-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "demo")
            with open(image_path, "wb") as image_file:
                image_file.write(_macho_image(encoded_report))

            image_bundle = load_report_source("image:" + image_path)
            extracted_report = macho.extract_ide_report_envelope(image_path)

        self.assertEqual(encoded_report, extracted_report)
        self.assertEqual(payload, canonical_bytes(image_bundle))
        self.assertEqual("full", image_bundle.payload_scope)
        self.assertEqual("image", image_bundle.provenance.format_name)

    def test_image_source_extracts_elf_envelope(self):
        payload = canonical_bytes(_load_raw_report(_sample_report()))
        encoded_report = envelope.encode(payload, "test-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "demo")
            with open(image_path, "wb") as image_file:
                image_file.write(_elf_image(encoded_report))

            image_bundle = load_report_source("image:" + image_path)
            extracted_report = elf.extract_ide_report_envelope(image_path)
            dispatched_report = image.extract_ide_report_envelope(image_path)

        self.assertEqual(encoded_report, extracted_report)
        self.assertEqual(encoded_report, dispatched_report)
        self.assertEqual(payload, canonical_bytes(image_bundle))
        self.assertEqual("full", image_bundle.payload_scope)
        self.assertEqual("image", image_bundle.provenance.format_name)

    def test_image_source_rejects_non_mach_o_image(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "demo")
            with open(image_path, "wb") as image_file:
                image_file.write(b"not a Mach-O image")

            with self.assertRaisesRegex(IDEReportSourceError, "ELF and Mach-O 64-bit"):
                load_report_source("image:" + image_path)

    def test_image_source_rejects_missing_locator_symbols(self):
        encoded_report = envelope.encode(canonical_bytes(_load_raw_report(_sample_report())), "test-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "demo")
            with open(image_path, "wb") as image_file:
                image_file.write(_macho_image(encoded_report, include_length_symbol=False))

            with self.assertRaisesRegex(IDEReportSourceError, "ide_report_length"):
                load_report_source("image:" + image_path)

    def test_elf_image_source_rejects_missing_locator_symbols(self):
        encoded_report = envelope.encode(canonical_bytes(_load_raw_report(_sample_report())), "test-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "demo")
            with open(image_path, "wb") as image_file:
                image_file.write(_elf_image(encoded_report, include_length_symbol=False))

            with self.assertRaisesRegex(IDEReportSourceError, "ide_report_length"):
                load_report_source("image:" + image_path)

    def test_elf_image_source_rejects_writable_report_section(self):
        encoded_report = envelope.encode(canonical_bytes(_load_raw_report(_sample_report())), "test-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "demo")
            with open(image_path, "wb") as image_file:
                image_file.write(_elf_image(encoded_report, writable=True))

            with self.assertRaisesRegex(IDEReportSourceError, "writable"):
                load_report_source("image:" + image_path)

    def test_elf_image_source_rejects_writable_load_segment(self):
        encoded_report = envelope.encode(canonical_bytes(_load_raw_report(_sample_report())), "test-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "demo")
            with open(image_path, "wb") as image_file:
                image_file.write(_elf_image(encoded_report, writable_segment=True))

            with self.assertRaisesRegex(IDEReportSourceError, "load segment is writable"):
                load_report_source("image:" + image_path)

    def test_elf_image_source_rejects_executable_report_section(self):
        encoded_report = envelope.encode(canonical_bytes(_load_raw_report(_sample_report())), "test-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "demo")
            with open(image_path, "wb") as image_file:
                image_file.write(_elf_image(encoded_report, executable=True))

            with self.assertRaisesRegex(IDEReportSourceError, "section is executable"):
                load_report_source("image:" + image_path)

    def test_elf_image_source_accepts_read_only_executable_load_segment(self):
        payload = canonical_bytes(_load_raw_report(_sample_report()))
        encoded_report = envelope.encode(payload, "test-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            image_path = os.path.join(temp_dir, "demo")
            with open(image_path, "wb") as image_file:
                image_file.write(_elf_image(encoded_report, executable_segment=True))

            image_bundle = load_report_source("image:" + image_path)

        self.assertEqual(payload, canonical_bytes(image_bundle))

    def test_canonical_source_loads_canonical_report(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            raw_report_path = os.path.join(temp_dir, "report.json")
            canonical_report_path = os.path.join(temp_dir, "canonical.json")
            with open(raw_report_path, "w", encoding="utf-8") as report_file:
                json.dump(_sample_report(), report_file)

            bundle = load_report_source("json:" + raw_report_path)
            write_canonical(bundle, canonical_report_path)
            canonical_bundle = load_report_source("canonical:" + canonical_report_path)

        self.assertEqual(3, len(canonical_bundle.reports))
        self.assertEqual(1, len(canonical_bundle.used_methods))
        self.assertEqual("canonical", canonical_bundle.provenance.format_name)

    def test_split_source_decodes_envelope_and_loads_canonical_report(self):
        payload = canonical_bytes(_load_raw_report(_sample_report()))
        encoded_report = envelope.encode(payload, "test-producer")
        with tempfile.TemporaryDirectory() as temp_dir:
            split_report_path = os.path.join(temp_dir, "demo.ide-report")
            with open(split_report_path, "wb") as report_file:
                report_file.write(encoded_report)

            split_bundle = load_report_source("split:" + split_report_path)

        self.assertEqual(3, len(split_bundle.reports))
        self.assertEqual(1, len(split_bundle.used_methods))
        self.assertEqual("full", split_bundle.payload_scope)
        self.assertEqual("split", split_bundle.provenance.format_name)
        self.assertEqual(payload, canonical_bytes(split_bundle))

    def test_split_source_rejects_corrupt_envelope(self):
        encoded_report = envelope.encode(canonical_bytes(_load_raw_report(_sample_report())), "test-producer")
        corrupted_report = encoded_report[:-1] + bytes([encoded_report[-1] ^ 1])
        with tempfile.TemporaryDirectory() as temp_dir:
            split_report_path = os.path.join(temp_dir, "demo.ide-report")
            with open(split_report_path, "wb") as report_file:
                report_file.write(corrupted_report)

            with self.assertRaisesRegex(IDEReportSourceError, "Invalid split IDE report"):
                load_report_source("split:" + split_report_path)

    def test_split_source_rejects_non_json_payload(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            split_report_path = os.path.join(temp_dir, "demo.ide-report")
            with open(split_report_path, "wb") as report_file:
                report_file.write(envelope.encode(b"not JSON", "test-producer"))

            with self.assertRaisesRegex(IDEReportSourceError, "Invalid split IDE report"):
                load_report_source("split:" + split_report_path)

    def test_split_source_rejects_non_utf8_payload(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            split_report_path = os.path.join(temp_dir, "demo.ide-report")
            with open(split_report_path, "wb") as report_file:
                report_file.write(envelope.encode(b"\xff", "test-producer"))

            with self.assertRaisesRegex(IDEReportSourceError, "Invalid split IDE report"):
                load_report_source("split:" + split_report_path)

    def test_canonical_bytes_are_deterministic(self):
        first_report = _sample_report()
        second_report = _sample_report()
        second_report["reports"] = list(reversed(second_report["reports"]))

        first_bundle = _load_raw_report(first_report)
        second_bundle = _load_raw_report(second_report)

        self.assertEqual(canonical_bytes(first_bundle), canonical_bytes(second_bundle))

    def test_canonical_payload_excludes_load_provenance(self):
        payload = canonical_payload(_load_raw_report(_sample_report()))

        self.assertNotIn("provenance", payload)

    def test_minimal_payload_uses_semantic_categories_and_excludes_method_inventory(self):
        bundle = _bundle_with_records(
            [
                ReportRecord(kind="LINE", category="reflection", message="resolved reflection"),
                ReportRecord(kind="CLASS", category="class-initialization", message="initialized at run time"),
            ],
            used_methods=[MethodReference(class_name="demo.App", method_name="main", method_signature="()V")],
        )

        payload = canonical_payload(bundle, "minimal")

        self.assertEqual("minimal", payload["payload_scope"])
        self.assertEqual(["reflection"], [record["category"] for record in payload["records"]])
        self.assertEqual([], payload["used_methods"])

    def test_minimal_payload_rejects_uncategorized_records(self):
        bundle = _bundle_with_records([ReportRecord(kind="LINE", message="legacy record")])

        with self.assertRaises(ValueError):
            canonical_payload(bundle, "minimal")

    def test_canonical_payload_matches_java_golden_vector(self):
        bundle = _bundle_with_records(
            [
                ReportRecord(
                    kind="LINE",
                    category="reflection",
                    location=SourceLocation(filename="demo/App.java", line=7),
                    message="café resolved",
                ),
                ReportRecord(
                    kind="CLASS",
                    category="class-initialization",
                    location=SourceLocation(filename="demo/App.java"),
                    class_name="demo.App",
                    message="runtime",
                ),
            ],
            used_methods=[
                MethodReference(
                    filename="demo/App.java", class_name="demo.App", method_name="main", method_signature="()V"
                )
            ],
        )

        expected = """{
  "extensions": {},
  "payload_scope": "full",
  "records": [
    {
      "category": "class-initialization",
      "class": "demo.App",
      "filename": "demo/App.java",
      "kind": "CLASS",
      "msg": "runtime"
    },
    {
      "category": "reflection",
      "filename": "demo/App.java",
      "kind": "LINE",
      "line": 7,
      "msg": "café resolved"
    }
  ],
  "schema_version": 1,
  "used_methods": [
    {
      "class": "demo.App",
      "filename": "demo/App.java",
      "mthname": "main",
      "mthsig": "()V"
    }
  ]
}
""".encode(
            "utf-8"
        )

        self.assertEqual(expected, canonical_bytes(bundle, "full"))

    def test_write_sha256_writes_hash_line(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            hash_path = os.path.join(temp_dir, "report.sha256")
            write_sha256("abc123", hash_path)
            with open(hash_path, encoding="utf-8") as hash_file:
                self.assertEqual("abc123\n", hash_file.read())


class IDEReportCompareTests(unittest.TestCase):
    def test_record_identity_covers_class_field_method_and_no_subject_records(self):
        class_identity = record_identity(
            ReportRecord(
                kind="LINE",
                location=SourceLocation(filename="./demo/App.java"),
                class_name="demo.App",
                message="reflection target",
            )
        )
        field_identity = record_identity(
            ReportRecord(kind="FIELD", class_name="demo.App", field_name="VALUE", message="constant field")
        )
        method_identity = record_identity(
            ReportRecord(
                kind="METHOD",
                class_name="demo.App",
                method_name="main",
                method_signature="([Ljava/lang/String;)V",
                message="compiled method",
            )
        )
        no_subject_identity = record_identity(ReportRecord(kind="LINE", message="global message"))

        self.assertEqual("demo/App.java", class_identity.source_file)
        self.assertEqual("VALUE", field_identity.field_name)
        self.assertEqual("main", method_identity.method_name)
        self.assertEqual("", no_subject_identity.class_name)

    def test_compare_ignores_record_order(self):
        before = _bundle_with_records(_sample_records())
        after = _bundle_with_records(list(reversed(_sample_records())))

        result = compare_bundles(before, after)

        self.assertFalse(result.has_differences())

    def test_compare_reports_missing_added_changed_and_duplicates(self):
        before = _bundle_with_records(
            [
                _method_record("demo.App", "stable", line=1),
                _method_record("demo.App", "missing", line=2),
                _method_record("demo.App", "changed", line=3),
                _method_record("demo.App", "duplicate", line=4),
                _method_record("demo.App", "duplicate", line=4),
            ]
        )
        after = _bundle_with_records(
            [
                _method_record("demo.App", "stable", line=1),
                _method_record("demo.App", "added", line=5),
                _method_record("demo.App", "changed", line=30),
                _method_record("demo.App", "duplicate", line=4),
            ]
        )

        result = compare_bundles(before, after)
        summary = result.to_dict()["summary"]["total"]

        self.assertEqual(2, summary["missing"])
        self.assertEqual(1, summary["added"])
        self.assertEqual(1, summary["changed"])

    def test_compare_reports_used_method_identity_changes(self):
        before = _bundle_with_records(
            [], used_methods=[MethodReference(class_name="demo.Before", method_name="work", method_signature="()V")]
        )
        after = _bundle_with_records(
            [], used_methods=[MethodReference(class_name="demo.After", method_name="work", method_signature="()V")]
        )

        result = compare_bundles(before, after)

        self.assertTrue(result.has_differences())
        self.assertEqual("demo.Before", result.missing_methods[0]["class"])
        self.assertEqual("demo.After", result.added_methods[0]["class"])

    def test_expectations_pass_for_matching_records_and_group_counts(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            expectation_path = os.path.join(temp_dir, "expectations.json")
            with open(expectation_path, "w", encoding="utf-8") as expectation_file:
                json.dump(
                    {
                        "reportCount": 1,
                        "usedMethods": {"min": 1, "max": 1},
                        "records": [
                            {
                                "kind": "METHOD",
                                "class": "demo.App",
                                "method": "main",
                                "signature": "()V",
                                "messageRegex": "compiled",
                                "sourceFile": "demo/App.java",
                                "line": {"mode": "range", "start": 10, "end": 20},
                            }
                        ],
                        "groupCounts": [{"kind": "METHOD", "count": 1}],
                    },
                    expectation_file,
                )

            expectations = load_expectations(expectation_path)

        result = check_expectations(
            _bundle_with_records(
                [_method_record("demo.App", "main", line=12)],
                used_methods=[_method_record("demo.Helper", "work", line=1)],
            ),
            expectations,
        )

        self.assertTrue(result.passed())

    def test_expectations_report_missing_records(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            expectation_path = os.path.join(temp_dir, "expectations.json")
            with open(expectation_path, "w", encoding="utf-8") as expectation_file:
                json.dump({"records": [{"kind": "FIELD", "class": "demo.App", "field": "VALUE"}]}, expectation_file)

            expectations = load_expectations(expectation_path)

        result = check_expectations(_bundle_with_records([]), expectations)

        self.assertFalse(result.passed())
        self.assertEqual(1, len(result.missing_records))

    def test_expectations_report_forbidden_records(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            expectation_path = os.path.join(temp_dir, "expectations.json")
            with open(expectation_path, "w", encoding="utf-8") as expectation_file:
                json.dump({"forbiddenRecords": [{"kind": "LINE", "messageRegex": "always returns"}]}, expectation_file)

            expectations = load_expectations(expectation_path)

        result = check_expectations(
            _bundle_with_records(
                [
                    ReportRecord(
                        kind="LINE",
                        location=SourceLocation(filename="demo/App.java", line=12),
                        message="value always returns 1",
                    )
                ]
            ),
            expectations,
        )

        self.assertFalse(result.passed())
        self.assertEqual(1, len(result.unexpected_records))

    def test_expectations_report_count_failures(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            expectation_path = os.path.join(temp_dir, "expectations.json")
            with open(expectation_path, "w", encoding="utf-8") as expectation_file:
                json.dump({"reportCount": {"min": 2}, "usedMethods": 1}, expectation_file)

            expectations = load_expectations(expectation_path)

        result = check_expectations(_bundle_with_records([_method_record("demo.App", "main", line=12)]), expectations)

        self.assertFalse(result.passed())
        self.assertEqual(2, len(result.count_failures))

    def test_expectation_parser_rejects_invalid_line_mode(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            expectation_path = os.path.join(temp_dir, "expectations.json")
            with open(expectation_path, "w", encoding="utf-8") as expectation_file:
                json.dump({"records": [{"kind": "LINE", "line": {"mode": "near"}}]}, expectation_file)

            with self.assertRaises(IDEReportSourceError):
                load_expectations(expectation_path)


class IDEReportCLITests(unittest.TestCase):
    def test_summarize_bundle_groups_report_kinds(self):
        summary = cli._summarize_bundle(
            _bundle_with_records(
                [
                    ReportRecord(kind="METHOD", location=SourceLocation(filename="demo/App.java"), message="method"),
                    ReportRecord(
                        kind="LINE", location=SourceLocation(filename="demo/App.java", line=7), message="line"
                    ),
                    ReportRecord(kind="METHOD", message="method without source"),
                ],
                used_methods=[_method_record("demo.Helper", "work", line=1)],
            )
        )

        self.assertEqual(3, summary["reports"])
        self.assertEqual(2, summary["reports_with_source_locations"])
        self.assertEqual(1, summary["used_methods"])
        self.assertEqual([("LINE", 1), ("METHOD", 2)], summary["reports_by_kind"])

    def test_matches_query_combines_filters(self):
        record = ReportRecord(
            kind="METHOD",
            category="inlined-only-method",
            location=SourceLocation(filename="demo/App.java", line=42),
            class_name="demo.App",
            method_name="main",
            method_signature="()V",
            message="compiled method",
        )
        parsed_args = types.SimpleNamespace(
            kind="METHOD",
            category="inlined-only-method",
            class_name=None,
            class_prefix="demo.",
            source_file="demo/App.java",
            method="main",
            field=None,
            message_regex="compiled",
        )

        self.assertTrue(cli._matches_query(record, parsed_args))
        parsed_args.category = "reflection"
        self.assertFalse(cli._matches_query(record, parsed_args))
        parsed_args.category = "inlined-only-method"
        parsed_args.message_regex = "reflection"
        self.assertFalse(cli._matches_query(record, parsed_args))

    def test_baseline_canonical_paths_are_sorted_and_skip_incomplete_runs(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            run_02 = os.path.join(temp_dir, "fixture", "runs", "run-02")
            run_01 = os.path.join(temp_dir, "fixture", "runs", "run-01")
            os.makedirs(run_02)
            os.makedirs(run_01)
            open(os.path.join(run_02, "canonical.json"), "w", encoding="utf-8").close()
            open(os.path.join(run_01, "canonical.json"), "w", encoding="utf-8").close()
            os.makedirs(os.path.join(temp_dir, "fixture", "runs", "run-03"))

            paths = cli._baseline_canonical_paths(temp_dir, "fixture")

        self.assertEqual(["run-01", "run-02"], [os.path.basename(os.path.dirname(path)) for path in paths])

    def test_smoke_fixture_parser_defaults_to_temporary_output(self):
        parsed_args = cli._create_parser().parse_args(["smoke-fixture"])

        self.assertIs(cli._smoke_fixture, parsed_args.command_func)
        self.assertIsNone(parsed_args.output_dir)
        self.assertEqual("mx", parsed_args.mx)


class IDEReportBaselineTests(unittest.TestCase):
    def test_copy_latest_report_requires_exactly_one_report(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_dir = Path(temp_dir) / "source"
            target_dir = Path(temp_dir) / "target"
            source_dir.mkdir()

            with self.assertRaises(baseline.BaselineCollectionError):
                baseline._copy_latest_report(source_dir, target_dir)

            report = source_dir / "native_image_ide_report_1.json"
            report.write_text('{"reports": []}', encoding="utf-8")
            copied = baseline._copy_latest_report(source_dir, target_dir)

            self.assertEqual(target_dir / report.name, copied)
            self.assertEqual('{"reports": []}', copied.read_text(encoding="utf-8"))

            second_report = source_dir / "native_image_ide_report_2.json"
            second_report.write_text("{}", encoding="utf-8")
            copied_second = baseline._copy_latest_report(source_dir, target_dir, excluded_reports={report})
            self.assertEqual(second_report.name, copied_second.name)

            with self.assertRaises(baseline.BaselineCollectionError):
                baseline._copy_latest_report(source_dir, target_dir)

    def test_disabled_fixture_rejects_report_output(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            baseline._assert_no_ide_reports(temp_dir)
            report_dir = Path(temp_dir) / "ide-reports"
            report_dir.mkdir()
            (report_dir / "unexpected.json").write_text("{}", encoding="utf-8")

            with self.assertRaises(baseline.BaselineCollectionError):
                baseline._assert_no_ide_reports(temp_dir)


def _load_raw_report(raw_report, source="json:sample-report"):
    with tempfile.TemporaryDirectory() as temp_dir:
        report_path = os.path.join(temp_dir, "report.json")
        with open(report_path, "w", encoding="utf-8") as report_file:
            json.dump(raw_report, report_file)
        return load_json_report(report_path, source)


def _macho_image(encoded_report, include_length_symbol=True, include_report_section=True):
    header_size = 32
    segment_command_size = 72 + 80
    symtab_command_size = 24
    commands_size = segment_command_size + symtab_command_size
    report_offset = header_size + commands_size
    section_content = encoded_report + struct.pack("<Q", len(encoded_report))
    symbol_count = 2 if include_length_symbol else 1
    symbol_offset = report_offset + len(section_content)
    string_table = b"\0_ide_report\0_ide_report_length\0"
    string_offset = symbol_offset + symbol_count * 16
    report_address = 0x100000000

    header = struct.pack("<IiiIIIII", 0xFEEDFACF, 0x0100000C, 0, 2, 2, commands_size, 0, 0)
    segment = struct.pack(
        "<II16sQQQQiiII",
        0x19,
        segment_command_size,
        b"__TEXT\0\0\0\0\0\0\0\0\0\0",
        report_address,
        len(section_content),
        report_offset,
        len(section_content),
        5,
        5,
        1,
        0,
    )
    section_name = b"__svm_idereport" if include_report_section else b"__not_report"
    section = struct.pack(
        "<16s16sQQIIIIIIII",
        section_name,
        b"__TEXT\0\0\0\0\0\0\0\0\0\0",
        report_address,
        len(section_content),
        report_offset,
        3,
        0,
        0,
        0,
        0,
        0,
        0,
    )
    symtab = struct.pack(
        "<IIIIII", 0x2, symtab_command_size, symbol_offset, symbol_count, string_offset, len(string_table)
    )
    report_symbol = struct.pack("<IBBHQ", 1, 0x0F, 1, 0, report_address)
    length_symbol = struct.pack(
        "<IBBHQ", string_table.index(b"_ide_report_length"), 0x0F, 1, 0, report_address + len(encoded_report)
    )
    symbols = report_symbol + (length_symbol if include_length_symbol else b"")
    return header + segment + section + symtab + section_content + symbols + string_table


def _elf_image(
    encoded_report,
    include_length_symbol=True,
    writable=False,
    writable_segment=False,
    executable=False,
    executable_segment=False,
):
    header_size = 64
    program_header_size = 56
    section_header_size = 64
    report_offset = 0x100
    report_address = 0x2000
    section_content = encoded_report + struct.pack("<Q", len(encoded_report))
    dynstr = b"\0ide_report\0ide_report_length\0"
    symbol_count = 3 if include_length_symbol else 2
    dynsym_size = symbol_count * 24
    dynstr_offset = report_offset + len(section_content)
    dynsym_offset = (dynstr_offset + len(dynstr) + 7) & ~7
    shstr = b"\0.svm_ide_report\0.dynstr\0.dynsym\0.shstrtab\0"
    shstr_offset = dynsym_offset + dynsym_size
    section_header_offset = (shstr_offset + len(shstr) + 7) & ~7
    section_count = 5
    image_bytes = bytearray(section_header_offset + section_count * section_header_size)

    ident = b"\x7fELF" + bytes([2, 1, 1, 0, 0]) + b"\0" * 7
    struct.pack_into(
        "<16sHHIQQQIHHHHHH",
        image_bytes,
        0,
        ident,
        3,
        183,
        1,
        0,
        header_size,
        section_header_offset,
        0,
        header_size,
        program_header_size,
        1,
        section_header_size,
        section_count,
        4,
    )
    segment_flags = 4 | (2 if writable or writable_segment else 0) | (1 if executable or executable_segment else 0)
    struct.pack_into(
        "<IIQQQQQQ",
        image_bytes,
        header_size,
        1,
        segment_flags,
        report_offset,
        report_address,
        report_address,
        len(section_content),
        len(section_content),
        8,
    )
    image_bytes[report_offset : report_offset + len(section_content)] = section_content
    image_bytes[dynstr_offset : dynstr_offset + len(dynstr)] = dynstr

    report_name = dynstr.index(b"ide_report")
    length_name = dynstr.index(b"ide_report_length")
    struct.pack_into(
        "<IBBHQQ", image_bytes, dynsym_offset + 24, report_name, 0x11, 0, 1, report_address, len(encoded_report)
    )
    if include_length_symbol:
        struct.pack_into(
            "<IBBHQQ",
            image_bytes,
            dynsym_offset + 48,
            length_name,
            0x11,
            0,
            1,
            report_address + len(encoded_report),
            8,
        )
    image_bytes[shstr_offset : shstr_offset + len(shstr)] = shstr

    section_flags = 2 | (1 if writable else 0) | (4 if executable else 0)
    report_section_name = shstr.index(b".svm_ide_report")
    dynstr_section_name = shstr.index(b".dynstr")
    dynsym_section_name = shstr.index(b".dynsym")
    shstr_section_name = shstr.index(b".shstrtab")
    section_format = "<IIQQQQIIQQ"
    struct.pack_into(
        section_format,
        image_bytes,
        section_header_offset + section_header_size,
        report_section_name,
        1,
        section_flags,
        report_address,
        report_offset,
        len(section_content),
        0,
        0,
        8,
        0,
    )
    struct.pack_into(
        section_format,
        image_bytes,
        section_header_offset + 2 * section_header_size,
        dynstr_section_name,
        3,
        0,
        0,
        dynstr_offset,
        len(dynstr),
        0,
        0,
        1,
        0,
    )
    struct.pack_into(
        section_format,
        image_bytes,
        section_header_offset + 3 * section_header_size,
        dynsym_section_name,
        11,
        0,
        0,
        dynsym_offset,
        dynsym_size,
        2,
        1,
        8,
        24,
    )
    struct.pack_into(
        section_format,
        image_bytes,
        section_header_offset + 4 * section_header_size,
        shstr_section_name,
        3,
        0,
        0,
        shstr_offset,
        len(shstr),
        0,
        0,
        1,
        0,
    )
    return bytes(image_bytes)


def _bundle_with_records(records, used_methods=None):
    return ReportBundle(
        provenance=ReportProvenance(source="test", format_name="synthetic"),
        reports=records,
        used_methods=[] if used_methods is None else used_methods,
    )


def _sample_records():
    return [
        ReportRecord(
            kind="LINE",
            location=SourceLocation(filename="demo/App.java", line=12),
            class_name="demo.App",
            message="reflection target resolved",
        ),
        ReportRecord(
            kind="FIELD",
            location=SourceLocation(filename="demo/App.java", line=14),
            class_name="demo.App",
            field_name="VALUE",
            message="constant field",
        ),
        _method_record("demo.App", "main", line=16),
    ]


def _method_record(class_name, method_name, line):
    return ReportRecord(
        kind="METHOD",
        location=SourceLocation(filename="demo/App.java", line=line),
        class_name=class_name,
        method_name=method_name,
        method_signature="()V",
        message="compiled method",
    )


def _sample_report():
    return {
        "reports": [
            {
                "kind": "LINE",
                "filename": "demo/App.java",
                "line": 12,
                "class": "demo.App",
                "msg": "reflection target resolved",
                "extra": "preserved",
            },
            {
                "kind": "FIELD",
                "filename": "demo/App.java",
                "class": "demo.App",
                "field": "VALUE",
                "msg": "constant field",
            },
            {
                "kind": "METHOD",
                "filename": "demo/App.java",
                "class": "demo.Helper",
                "mthname": "work",
                "mthsig": "()V",
                "msg": "compiled method",
            },
        ],
        "used_methods": [
            {
                "filename": "demo/App.java",
                "class": "demo.Helper",
                "mthname": "work",
                "mthsig": "()V",
                "method-extra": True,
            }
        ],
        "top-level-extra": 1,
    }


if __name__ == "__main__":
    unittest.main()
