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

"""IDE report comparison and expectation helpers."""

from collections import Counter, defaultdict
from dataclasses import dataclass, field
import json
import re

from .sources import IDEReportSourceError


@dataclass(frozen=True)
class RecordIdentity:
    kind: str
    category: str
    source_file: str
    class_name: str
    field_name: str
    method_name: str
    method_signature: str
    message_category: str

    def to_dict(self):
        return {
            "kind": self.kind,
            "category": self.category,
            "source_file": self.source_file,
            "class": self.class_name,
            "field": self.field_name,
            "method": self.method_name,
            "signature": self.method_signature,
            "message_category": self.message_category,
        }


@dataclass(frozen=True)
class RecordDifference:
    identity: RecordIdentity
    before: dict = None
    after: dict = None

    def to_dict(self):
        result = {
            "identity": self.identity.to_dict(),
        }
        if self.before is not None:
            result["before"] = self.before
        if self.after is not None:
            result["after"] = self.after
        return result


@dataclass(frozen=True)
class CompareResult:
    before_count: int
    after_count: int
    before_used_method_count: int
    after_used_method_count: int
    missing: list = field(default_factory=list)
    added: list = field(default_factory=list)
    changed: list = field(default_factory=list)
    missing_methods: list = field(default_factory=list)
    added_methods: list = field(default_factory=list)

    def has_differences(self):
        return bool(self.missing or self.added or self.changed or self.missing_methods or self.added_methods)

    def to_dict(self):
        return {
            "before_count": self.before_count,
            "after_count": self.after_count,
            "before_used_method_count": self.before_used_method_count,
            "after_used_method_count": self.after_used_method_count,
            "missing": [difference.to_dict() for difference in self.missing],
            "added": [difference.to_dict() for difference in self.added],
            "changed": [difference.to_dict() for difference in self.changed],
            "missing_methods": self.missing_methods,
            "added_methods": self.added_methods,
            "summary": grouped_summary(self),
        }


@dataclass(frozen=True)
class ExpectedRecord:
    kind: str = None
    category: str = None
    source_file: str = None
    class_name: str = None
    field_name: str = None
    method_name: str = None
    method_signature: str = None
    message: str = None
    message_regex: str = None
    line: dict = field(default_factory=dict)
    count: int = 1

    def matches(self, record):
        if self.kind is not None and record.kind != self.kind:
            return False
        if self.category is not None and record.category != self.category:
            return False
        if self.source_file is not None and _normalize_source_file(record.location.filename) != _normalize_source_file(
            self.source_file
        ):
            return False
        if self.class_name is not None and record.class_name != self.class_name:
            return False
        if self.field_name is not None and record.field_name != self.field_name:
            return False
        if self.method_name is not None and record.method_name != self.method_name:
            return False
        if self.method_signature is not None and record.method_signature != self.method_signature:
            return False
        if self.message is not None and record.message != self.message:
            return False
        if self.message_regex is not None and not re.search(self.message_regex, record.message or ""):
            return False
        return _matches_line(self.line, record.location.line)

    def to_dict(self):
        return {
            "kind": self.kind,
            "category": self.category,
            "sourceFile": self.source_file,
            "class": self.class_name,
            "field": self.field_name,
            "method": self.method_name,
            "signature": self.method_signature,
            "message": self.message,
            "messageRegex": self.message_regex,
            "line": self.line,
            "count": self.count,
        }


@dataclass(frozen=True)
class GroupCountExpectation:
    kind: str
    count: int = None
    minimum: int = None
    maximum: int = None

    def check(self, actual_count):
        if self.count is not None:
            return actual_count == self.count
        if self.minimum is not None and actual_count < self.minimum:
            return False
        if self.maximum is not None and actual_count > self.maximum:
            return False
        return True

    def to_dict(self):
        return {
            "kind": self.kind,
            "count": self.count,
            "min": self.minimum,
            "max": self.maximum,
        }


@dataclass(frozen=True)
class CountExpectation:
    name: str
    count: int = None
    minimum: int = None
    maximum: int = None

    def check(self, actual_count):
        if self.count is not None:
            return actual_count == self.count
        if self.minimum is not None and actual_count < self.minimum:
            return False
        if self.maximum is not None and actual_count > self.maximum:
            return False
        return True

    def to_dict(self):
        return {
            "name": self.name,
            "count": self.count,
            "min": self.minimum,
            "max": self.maximum,
        }


@dataclass(frozen=True)
class ReportExpectations:
    records: list = field(default_factory=list)
    forbidden_records: list = field(default_factory=list)
    group_counts: list = field(default_factory=list)
    report_count: CountExpectation = None
    used_methods: CountExpectation = None


@dataclass(frozen=True)
class ExpectationResult:
    missing_records: list = field(default_factory=list)
    unexpected_records: list = field(default_factory=list)
    group_count_failures: list = field(default_factory=list)
    count_failures: list = field(default_factory=list)

    def passed(self):
        return (
            not self.missing_records
            and not self.unexpected_records
            and not self.group_count_failures
            and not self.count_failures
        )

    def to_dict(self):
        return {
            "passed": self.passed(),
            "missing_records": self.missing_records,
            "unexpected_records": self.unexpected_records,
            "group_count_failures": self.group_count_failures,
            "count_failures": self.count_failures,
        }


def record_identity(record):
    return RecordIdentity(
        kind=_identity_value(record.kind),
        category=_identity_value(record.category),
        source_file=_normalize_source_file(record.location.filename),
        class_name=_identity_value(record.class_name),
        field_name=_identity_value(record.field_name),
        method_name=_identity_value(record.method_name),
        method_signature=_identity_value(record.method_signature),
        message_category=_message_category(record.message),
    )


def compare_bundles(before_bundle, after_bundle):
    before_groups = _group_records_by_identity(before_bundle.reports)
    after_groups = _group_records_by_identity(after_bundle.reports)
    missing = []
    added = []
    changed = []

    for identity in sorted(set(before_groups) | set(after_groups), key=_identity_sort_key):
        unmatched_before, unmatched_after = _unmatched_details(
            before_groups.get(identity, []), after_groups.get(identity, [])
        )
        changed_count = min(len(unmatched_before), len(unmatched_after))
        for index in range(changed_count):
            changed.append(
                RecordDifference(identity=identity, before=unmatched_before[index], after=unmatched_after[index])
            )
        for details in unmatched_before[changed_count:]:
            missing.append(RecordDifference(identity=identity, before=details))
        for details in unmatched_after[changed_count:]:
            added.append(RecordDifference(identity=identity, after=details))

    missing_methods, added_methods = _unmatched_method_references(before_bundle.used_methods, after_bundle.used_methods)

    return CompareResult(
        before_count=len(before_bundle.reports),
        after_count=len(after_bundle.reports),
        before_used_method_count=len(before_bundle.used_methods),
        after_used_method_count=len(after_bundle.used_methods),
        missing=missing,
        added=added,
        changed=changed,
        missing_methods=missing_methods,
        added_methods=added_methods,
    )


def grouped_summary(compare_result):
    summary = {
        "missing": _group_differences(compare_result.missing),
        "added": _group_differences(compare_result.added),
        "changed": _group_differences(compare_result.changed),
    }
    summary["total"] = {
        "missing": len(compare_result.missing),
        "added": len(compare_result.added),
        "changed": len(compare_result.changed),
        "missing_methods": len(compare_result.missing_methods),
        "added_methods": len(compare_result.added_methods),
    }
    return summary


def load_expectations(path):
    with open(path, encoding="utf-8") as expectation_file:
        raw_expectations = json.load(expectation_file)
    if not isinstance(raw_expectations, dict):
        raise IDEReportSourceError("IDE report expectations must be a JSON object.")

    records = raw_expectations.get("records", [])
    if not isinstance(records, list):
        raise IDEReportSourceError("IDE report expectation field 'records' must be a list.")

    forbidden_records = raw_expectations.get("forbiddenRecords", raw_expectations.get("forbidden_records", []))
    if not isinstance(forbidden_records, list):
        raise IDEReportSourceError("IDE report expectation field 'forbiddenRecords' must be a list.")

    group_counts = raw_expectations.get("groupCounts", raw_expectations.get("group_counts", []))
    if not isinstance(group_counts, list):
        raise IDEReportSourceError("IDE report expectation field 'groupCounts' must be a list.")

    return ReportExpectations(
        records=[_parse_expected_record(record) for record in records],
        forbidden_records=[_parse_expected_record(record) for record in forbidden_records],
        group_counts=[_parse_group_count(group_count) for group_count in group_counts],
        report_count=_parse_optional_count(raw_expectations, "reportCount", "report_count"),
        used_methods=_parse_optional_count(raw_expectations, "usedMethods", "used_methods"),
    )


def check_expectations(report_bundle, expectations):
    missing_records = []
    for expected_record in expectations.records:
        actual_count = sum(1 for record in report_bundle.reports if expected_record.matches(record))
        if actual_count < expected_record.count:
            missing_records.append(
                {
                    "expected": expected_record.to_dict(),
                    "actualCount": actual_count,
                    "expectedCount": expected_record.count,
                }
            )

    unexpected_records = []
    for forbidden_record in expectations.forbidden_records:
        matching_records = [record.to_dict() for record in report_bundle.reports if forbidden_record.matches(record)]
        if matching_records:
            unexpected_records.append(
                {
                    "forbidden": forbidden_record.to_dict(),
                    "actualCount": len(matching_records),
                    "records": matching_records,
                }
            )

    actual_counts_by_kind = Counter(record.kind for record in report_bundle.reports)
    group_count_failures = []
    for group_count in expectations.group_counts:
        actual_count = actual_counts_by_kind.get(group_count.kind, 0)
        if not group_count.check(actual_count):
            group_count_failures.append(
                {
                    "expected": group_count.to_dict(),
                    "actualCount": actual_count,
                }
            )

    count_failures = []
    _check_count_expectation(count_failures, expectations.report_count, len(report_bundle.reports))
    _check_count_expectation(count_failures, expectations.used_methods, len(report_bundle.used_methods))

    return ExpectationResult(
        missing_records=missing_records,
        unexpected_records=unexpected_records,
        group_count_failures=group_count_failures,
        count_failures=count_failures,
    )


def _group_records_by_identity(records):
    groups = defaultdict(list)
    for record in records:
        groups[record_identity(record)].append(_record_details(record))
    return groups


def _unmatched_method_references(before_methods, after_methods):
    before = [_stable_value(method.to_dict()) for method in before_methods]
    after = [_stable_value(method.to_dict()) for method in after_methods]
    before_counter = Counter(_details_key(method) for method in before)
    after_counter = Counter(_details_key(method) for method in after)
    missing_counter = before_counter - after_counter
    added_counter = after_counter - before_counter
    missing = [method for method in before if _consume_counter(missing_counter, _details_key(method))]
    added = [method for method in after if _consume_counter(added_counter, _details_key(method))]
    return sorted(missing, key=_details_key), sorted(added, key=_details_key)


def _consume_counter(counter, key):
    if counter[key] == 0:
        return False
    counter[key] -= 1
    return True


def _unmatched_details(before_details, after_details):
    before_counter = Counter(_details_key(details) for details in before_details)
    after_counter = Counter(_details_key(details) for details in after_details)
    common_counter = before_counter & after_counter
    unmatched_before = []
    unmatched_after = []

    for details in before_details:
        details_key = _details_key(details)
        if common_counter[details_key] > 0:
            common_counter[details_key] -= 1
        else:
            unmatched_before.append(details)

    common_counter = before_counter & after_counter
    for details in after_details:
        details_key = _details_key(details)
        if common_counter[details_key] > 0:
            common_counter[details_key] -= 1
        else:
            unmatched_after.append(details)

    return sorted(unmatched_before, key=_details_key), sorted(unmatched_after, key=_details_key)


def _record_details(record):
    details = record.to_dict()
    for identity_field in ("kind", "category", "filename", "class", "field", "mthname", "mthsig"):
        details.pop(identity_field, None)
    return _stable_value(details)


def _group_differences(differences):
    by_kind = Counter(difference.identity.kind for difference in differences)
    by_class = Counter(difference.identity.class_name for difference in differences if difference.identity.class_name)
    return {
        "count": len(differences),
        "by_kind": sorted(by_kind.items()),
        "by_class": sorted(by_class.items()),
    }


def _parse_expected_record(raw_record):
    if not isinstance(raw_record, dict):
        raise IDEReportSourceError("IDE report record expectations must be objects.")
    line = raw_record.get("line", {"mode": "ignore"})
    _validate_line(line)
    count = raw_record.get("count", 1)
    if not isinstance(count, int) or count < 1:
        raise IDEReportSourceError("IDE report record expectation 'count' must be a positive integer.")
    return ExpectedRecord(
        kind=raw_record.get("kind"),
        category=raw_record.get("category"),
        source_file=raw_record.get("sourceFile", raw_record.get("filename")),
        class_name=raw_record.get("class"),
        field_name=raw_record.get("field"),
        method_name=raw_record.get("method", raw_record.get("mthname")),
        method_signature=raw_record.get("signature", raw_record.get("mthsig")),
        message=raw_record.get("message"),
        message_regex=raw_record.get("messageRegex"),
        line=line,
        count=count,
    )


def _parse_group_count(raw_group_count):
    if not isinstance(raw_group_count, dict):
        raise IDEReportSourceError("IDE report group count expectations must be objects.")
    kind = raw_group_count.get("kind")
    if kind is None:
        raise IDEReportSourceError("IDE report group count expectation requires 'kind'.")
    count = raw_group_count.get("count")
    minimum = raw_group_count.get("min")
    maximum = raw_group_count.get("max")
    if count is None and minimum is None and maximum is None:
        raise IDEReportSourceError("IDE report group count expectation requires 'count', 'min', or 'max'.")
    return GroupCountExpectation(kind=kind, count=count, minimum=minimum, maximum=maximum)


def _parse_optional_count(raw_expectations, camel_name, snake_name):
    raw_count = raw_expectations.get(camel_name, raw_expectations.get(snake_name))
    if raw_count is None:
        return None
    return _parse_count(camel_name, raw_count)


def _parse_count(name, raw_count):
    if isinstance(raw_count, int):
        return CountExpectation(name=name, count=raw_count)
    if not isinstance(raw_count, dict):
        raise IDEReportSourceError("IDE report expectation field '{}' must be an integer or object.".format(name))
    count = raw_count.get("count")
    minimum = raw_count.get("min")
    maximum = raw_count.get("max")
    if count is None and minimum is None and maximum is None:
        raise IDEReportSourceError("IDE report expectation field '{}' requires 'count', 'min', or 'max'.".format(name))
    _validate_optional_int(count, name, "count")
    _validate_optional_int(minimum, name, "min")
    _validate_optional_int(maximum, name, "max")
    return CountExpectation(name=name, count=count, minimum=minimum, maximum=maximum)


def _check_count_expectation(count_failures, expectation, actual_count):
    if expectation is not None and not expectation.check(actual_count):
        count_failures.append(
            {
                "expected": expectation.to_dict(),
                "actualCount": actual_count,
            }
        )


def _validate_optional_int(value, expectation_name, field_name):
    if value is not None and not isinstance(value, int):
        raise IDEReportSourceError(
            "IDE report expectation '{}' requires integer '{}'.".format(expectation_name, field_name)
        )


def _validate_line(line):
    if not isinstance(line, dict):
        raise IDEReportSourceError("IDE report expectation field 'line' must be an object.")
    mode = line.get("mode", "ignore")
    if mode == "ignore":
        return
    if mode == "exact":
        _require_int(line, "value", "exact line expectation")
        return
    if mode == "range":
        _require_int(line, "start", "range line expectation")
        _require_int(line, "end", "range line expectation")
        return
    if mode == "delta":
        _require_int(line, "value", "delta line expectation")
        _require_int(line, "delta", "delta line expectation")
        return
    raise IDEReportSourceError("Unsupported IDE report line expectation mode '{}'.".format(mode))


def _matches_line(line, actual_line):
    mode = line.get("mode", "ignore")
    if mode == "ignore":
        return True
    if actual_line is None:
        return False
    if mode == "exact":
        return actual_line == line["value"]
    if mode == "range":
        return line["start"] <= actual_line <= line["end"]
    if mode == "delta":
        return abs(actual_line - line["value"]) <= line["delta"]
    return False


def _require_int(value, field_name, context):
    if not isinstance(value.get(field_name), int):
        raise IDEReportSourceError("IDE report {} requires integer '{}'.".format(context, field_name))


def _identity_sort_key(identity):
    return json.dumps(identity.to_dict(), sort_keys=True, separators=(",", ":"))


def _details_key(details):
    return json.dumps(_stable_value(details), sort_keys=True, separators=(",", ":"))


def _identity_value(value):
    return "" if value is None else value


def _normalize_source_file(filename):
    if filename is None:
        return ""
    normalized = filename.replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    while "//" in normalized:
        normalized = normalized.replace("//", "/")
    return normalized


def _message_category(message):
    if message is None:
        return ""
    tokens = re.findall(r"[A-Za-z0-9_]+", message.lower())
    if not tokens:
        return ""
    return tokens[0]


def _stable_value(value):
    if isinstance(value, dict):
        return {key: _stable_value(value[key]) for key in sorted(value)}
    if isinstance(value, list):
        return [_stable_value(item) for item in value]
    return value
