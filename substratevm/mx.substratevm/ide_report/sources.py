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

"""IDE report source adapters."""

import json
import os
import warnings
from dataclasses import dataclass
from typing import Any, Dict

from . import envelope, image
from .elf import ELFIDEReportError, ELFIDEReportNotFoundError
from .image import IDEReportImageError
from .macho import MachOIDEReportError, MachOIDEReportNotFoundError
from .model import MethodReference, ReportBundle, ReportProvenance, ReportRecord, SourceLocation


_REPORTS_KEY = "reports"
_USED_METHODS_KEY = "used_methods"
_SUPPORTED_SOURCE_SCHEMES = frozenset(["json", "canonical", "image", "split", "auto"])

_REPORT_KNOWN_KEYS = frozenset(
    [
        "kind",
        "category",
        "filename",
        "line",
        "start-line",
        "end-line",
        "msg",
        "inlinectx",
        "class",
        "field",
        "mthname",
        "mthsig",
    ]
)
_METHOD_KNOWN_KEYS = frozenset(["filename", "class", "mthname", "mthsig"])


class IDEReportSourceError(ValueError):
    pass


@dataclass(frozen=True)
class SourceSpec:
    scheme: str
    path: str


def parse_source_uri(source):
    scheme, separator, path = source.partition(":")
    if not separator:
        raise IDEReportSourceError("Report source must use '<scheme>:<path>' syntax.")
    if scheme not in _SUPPORTED_SOURCE_SCHEMES:
        raise IDEReportSourceError(
            "Unsupported report source scheme '{}'. Expected one of: {}.".format(
                scheme, ", ".join(sorted(_SUPPORTED_SOURCE_SCHEMES))
            )
        )
    if not path:
        raise IDEReportSourceError("Report source path must not be empty.")
    return SourceSpec(scheme=scheme, path=path)


def load_report_source(source, max_decoded_payload_bytes=envelope.DEFAULT_MAX_DECODED_PAYLOAD_BYTES):
    source_spec = parse_source_uri(source)
    if source_spec.scheme == "json":
        return load_json_report(source_spec.path, source)
    if source_spec.scheme == "canonical":
        return load_canonical_report(source_spec.path, source)
    if source_spec.scheme == "split":
        return load_split_report(source_spec.path, source, max_decoded_payload_bytes)
    if source_spec.scheme == "image":
        return load_image_report(source_spec.path, source, max_decoded_payload_bytes)
    return load_auto_report(source_spec.path, source, max_decoded_payload_bytes)


def load_json_report(path, source):
    with open(path, encoding="utf-8") as report_file:
        raw_report = json.load(report_file)
    return _load_json_report(raw_report, source)


def _load_json_report(raw_report, source):
    if not isinstance(raw_report, dict):
        raise IDEReportSourceError("JSON IDE report must be an object.")

    raw_records = raw_report.get(_REPORTS_KEY, [])
    if not isinstance(raw_records, list):
        raise IDEReportSourceError("JSON IDE report field '{}' must be a list.".format(_REPORTS_KEY))

    raw_used_methods = raw_report.get(_USED_METHODS_KEY, [])
    if not isinstance(raw_used_methods, list):
        raise IDEReportSourceError("JSON IDE report field '{}' must be a list.".format(_USED_METHODS_KEY))

    extensions = {key: value for key, value in raw_report.items() if key not in {_REPORTS_KEY, _USED_METHODS_KEY}}
    return ReportBundle(
        provenance=ReportProvenance(source=source, format_name="json"),
        reports=[_parse_report_record(record) for record in raw_records],
        used_methods=[_parse_method_reference(method) for method in raw_used_methods],
        extensions=extensions,
    )


def load_canonical_report(path, source):
    with open(path, encoding="utf-8") as report_file:
        raw_report = json.load(report_file)
    return _load_canonical_report(raw_report, source, "canonical")


def load_split_report(path, source, max_decoded_payload_bytes=envelope.DEFAULT_MAX_DECODED_PAYLOAD_BYTES):
    envelope.validate_decoded_payload_limit(max_decoded_payload_bytes)
    encoded_report = _read_enveloped_file(path, max_decoded_payload_bytes)
    return _load_enveloped_report(encoded_report, path, source, "split", max_decoded_payload_bytes)


def load_image_report(path, source, max_decoded_payload_bytes=envelope.DEFAULT_MAX_DECODED_PAYLOAD_BYTES):
    envelope.validate_decoded_payload_limit(max_decoded_payload_bytes)
    try:
        encoded_report = image.extract_ide_report_envelope(path)
    except (ELFIDEReportError, IDEReportImageError, MachOIDEReportError, OSError) as error:
        raise IDEReportSourceError("Could not extract embedded IDE report from '{}': {}".format(path, error)) from error
    return _load_enveloped_report(encoded_report, path, source, "image", max_decoded_payload_bytes)


def load_auto_report(path, source, max_decoded_payload_bytes=envelope.DEFAULT_MAX_DECODED_PAYLOAD_BYTES):
    envelope.validate_decoded_payload_limit(max_decoded_payload_bytes)
    searched = [path]
    if os.path.isfile(path):
        detected = _detect_file_kind(path)
        if detected == "image":
            try:
                encoded_report = image.extract_ide_report_envelope(path)
            except (ELFIDEReportNotFoundError, MachOIDEReportNotFoundError):
                pass
            except (ELFIDEReportError, IDEReportImageError, MachOIDEReportError, OSError) as error:
                raise IDEReportSourceError(
                    "Could not extract embedded IDE report from '{}': {}".format(path, error)
                ) from error
            else:
                split_path = path + ".ide-report"
                if os.path.isfile(split_path):
                    split_report = _read_enveloped_file(split_path, max_decoded_payload_bytes)
                    if split_report != encoded_report:
                        warnings.warn(
                            "Embedded IDE report differs from '{}'; using embedded data.".format(split_path),
                            RuntimeWarning,
                            stacklevel=2,
                        )
                return _load_enveloped_report(encoded_report, path, source, "image", max_decoded_payload_bytes)
        elif detected == "split":
            return load_split_report(path, source, max_decoded_payload_bytes)
        elif detected == "json":
            return _load_auto_json(path, source)

    split_path = path + ".ide-report"
    searched.append(split_path)
    if os.path.isfile(split_path):
        return load_split_report(split_path, source, max_decoded_payload_bytes)

    artifact_path = os.path.join(os.path.dirname(os.path.abspath(path)), "build-artifacts.json")
    searched.append(artifact_path)
    artifact_candidates = _ide_report_artifacts(artifact_path)
    if len(artifact_candidates) == 1:
        return _load_auto_existing_file(artifact_candidates[0], source, max_decoded_payload_bytes)
    if len(artifact_candidates) > 1:
        raise IDEReportSourceError(
            "auto: found multiple IDE report entries in '{}': {}".format(artifact_path, ", ".join(artifact_candidates))
        )
    raise IDEReportSourceError("auto: could not find an IDE report. Searched: {}.".format(", ".join(searched)))


def _detect_file_kind(path):
    with open(path, "rb") as report_file:
        prefix = report_file.read(max(len(envelope.MAGIC), 4))
    if image.detect_image_format(prefix[:4]) is not None:
        return "image"
    if prefix.startswith(envelope.MAGIC):
        return "split"
    if prefix.lstrip().startswith((b"{", b"[")):
        return "json"
    return None


def _load_auto_json(path, source):
    try:
        with open(path, encoding="utf-8") as report_file:
            raw_report = json.load(report_file)
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise IDEReportSourceError("Invalid JSON IDE report '{}': {}".format(path, error)) from error
    if isinstance(raw_report, dict) and "schema_version" in raw_report:
        return _load_canonical_report(raw_report, source, "canonical")
    return _load_json_report(raw_report, source)


def _ide_report_artifacts(path):
    if not os.path.isfile(path):
        return []
    try:
        with open(path, encoding="utf-8") as artifact_file:
            artifacts = json.load(artifact_file)
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise IDEReportSourceError("Invalid build artifacts file '{}': {}".format(path, error)) from error
    entries = artifacts.get("ide_report", []) if isinstance(artifacts, dict) else []
    if not isinstance(entries, list) or any(not isinstance(entry, str) for entry in entries):
        raise IDEReportSourceError("Build artifacts field 'ide_report' must be a list of paths.")
    directory = os.path.dirname(path)
    candidates = [entry if os.path.isabs(entry) else os.path.join(directory, entry) for entry in entries]
    return [candidate for candidate in candidates if os.path.isfile(candidate)]


def _load_auto_existing_file(path, source, max_decoded_payload_bytes):
    detected = _detect_file_kind(path)
    if detected == "image":
        return load_image_report(path, source, max_decoded_payload_bytes)
    if detected == "split":
        return load_split_report(path, source, max_decoded_payload_bytes)
    if detected == "json":
        return _load_auto_json(path, source)
    raise IDEReportSourceError("Could not identify IDE report artifact '{}'.".format(path))


def _load_enveloped_report(encoded_report, path, source, format_name, max_decoded_payload_bytes):
    try:
        decoded_report = envelope.decode(encoded_report, max_decoded_payload_bytes)
        raw_report = json.loads(decoded_report.payload.decode("utf-8"))
    except ValueError as error:
        raise IDEReportSourceError("Invalid {} IDE report '{}': {}".format(format_name, path, error)) from error
    return _load_canonical_report(raw_report, source, format_name)


def _read_enveloped_file(path, max_decoded_payload_bytes):
    max_encoded_size = max_decoded_payload_bytes + envelope.MAX_ENVELOPE_OVERHEAD
    try:
        if os.path.getsize(path) > max_encoded_size:
            raise IDEReportSourceError("IDE report input '{}' exceeds the configured payload limit.".format(path))
        with open(path, "rb") as report_file:
            encoded_report = report_file.read(max_encoded_size + 1)
    except OSError as error:
        raise IDEReportSourceError("Could not read IDE report '{}': {}".format(path, error)) from error
    if len(encoded_report) > max_encoded_size:
        raise IDEReportSourceError("IDE report input '{}' exceeds the configured payload limit.".format(path))
    return encoded_report


def _load_canonical_report(raw_report, source, format_name):
    if not isinstance(raw_report, dict):
        raise IDEReportSourceError("Canonical IDE report must be an object.")
    schema_version = raw_report.get("schema_version")
    if schema_version != 1:
        raise IDEReportSourceError(
            "Unsupported canonical IDE report schema_version '{}'. Expected 1.".format(schema_version)
        )
    payload_scope = raw_report.get("payload_scope")
    if payload_scope not in ("full", "minimal"):
        raise IDEReportSourceError(
            "Unsupported canonical IDE report payload_scope '{}'. Expected 'full' or 'minimal'.".format(payload_scope)
        )

    raw_records = raw_report.get("records", [])
    if not isinstance(raw_records, list):
        raise IDEReportSourceError("Canonical IDE report field 'records' must be a list.")

    raw_used_methods = raw_report.get(_USED_METHODS_KEY, [])
    if not isinstance(raw_used_methods, list):
        raise IDEReportSourceError("Canonical IDE report field '{}' must be a list.".format(_USED_METHODS_KEY))

    extensions = raw_report.get("extensions", {})
    if not isinstance(extensions, dict):
        raise IDEReportSourceError("Canonical IDE report field 'extensions' must be an object.")

    return ReportBundle(
        provenance=ReportProvenance(source=source, format_name=format_name),
        reports=[_parse_report_record(record) for record in raw_records],
        used_methods=[_parse_method_reference(method) for method in raw_used_methods],
        extensions=extensions,
        payload_scope=payload_scope,
    )


def _parse_report_record(raw_record):
    if not isinstance(raw_record, dict):
        raise IDEReportSourceError("JSON IDE report records must be objects.")
    inline_context = raw_record.get("inlinectx") or []
    if not isinstance(inline_context, list):
        raise IDEReportSourceError("JSON IDE report field 'inlinectx' must be a list when present.")
    return ReportRecord(
        kind=raw_record.get("kind"),
        category=raw_record.get("category"),
        location=_parse_source_location(raw_record),
        message=raw_record.get("msg"),
        class_name=raw_record.get("class"),
        field_name=raw_record.get("field"),
        method_name=raw_record.get("mthname"),
        method_signature=raw_record.get("mthsig"),
        inline_context=[_parse_source_location(location) for location in inline_context],
        extensions=_extensions(raw_record, _REPORT_KNOWN_KEYS),
    )


def _parse_method_reference(raw_method):
    if not isinstance(raw_method, dict):
        raise IDEReportSourceError("JSON IDE report used methods must be objects.")
    return MethodReference(
        filename=raw_method.get("filename"),
        class_name=raw_method.get("class"),
        method_name=raw_method.get("mthname"),
        method_signature=raw_method.get("mthsig"),
        extensions=_extensions(raw_method, _METHOD_KNOWN_KEYS),
    )


def _parse_source_location(raw_location):
    if not isinstance(raw_location, dict):
        raise IDEReportSourceError("JSON IDE report source locations must be objects.")
    return SourceLocation(
        filename=raw_location.get("filename"),
        line=_optional_int(raw_location.get("line"), "line"),
        start_line=_optional_int(raw_location.get("start-line"), "start-line"),
        end_line=_optional_int(raw_location.get("end-line"), "end-line"),
    )


def _optional_int(value, field_name):
    if value is None:
        return None
    if isinstance(value, int):
        return value
    raise IDEReportSourceError("JSON IDE report field '{}' must be an integer.".format(field_name))


def _extensions(raw_data: Dict[str, Any], known_keys):
    return {key: value for key, value in raw_data.items() if key not in known_keys}
