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

"""Canonical IDE report representation helpers.

Canonical reports use a small stable JSON shape:

- schema_version
- payload_scope
- records
- used_methods
- extensions
"""

import hashlib
import json


SCHEMA_VERSION = 1
DEFAULT_PAYLOAD_SCOPE = "full"
SUPPORTED_PAYLOAD_SCOPES = frozenset(["full", "minimal"])
MINIMAL_REPORT_CATEGORIES = frozenset(["reflection", "inlining", "unreachable", "devirtualization", "return-value"])


def canonical_payload(report_bundle, payload_scope=None):
    payload_scope = payload_scope or report_bundle.payload_scope or DEFAULT_PAYLOAD_SCOPE
    if payload_scope not in SUPPORTED_PAYLOAD_SCOPES:
        raise ValueError("Unsupported IDE report payload scope: {}".format(payload_scope))
    records = report_bundle.reports
    used_methods = report_bundle.used_methods
    if payload_scope == "minimal":
        uncategorized = [record for record in records if record.category is None]
        if uncategorized:
            raise ValueError("Minimal IDE report payloads require every record to have a semantic category.")
        records = [record for record in records if record.category in MINIMAL_REPORT_CATEGORIES]
        used_methods = []
    return {
        "schema_version": SCHEMA_VERSION,
        "payload_scope": payload_scope,
        "records": sorted((record.to_dict() for record in records), key=_stable_object_key),
        "used_methods": sorted((method.to_dict() for method in used_methods), key=_stable_object_key),
        "extensions": _stable_value(report_bundle.extensions),
    }


def canonical_bytes(report_bundle, payload_scope=None):
    payload = canonical_payload(report_bundle, payload_scope)
    return (json.dumps(payload, indent=2, sort_keys=True, separators=(",", ": "), ensure_ascii=False) + "\n").encode(
        "utf-8"
    )


def canonical_sha256(report_bundle, payload_scope=None):
    return hashlib.sha256(canonical_bytes(report_bundle, payload_scope)).hexdigest()


def write_canonical(report_bundle, output_path, payload_scope=None):
    data = canonical_bytes(report_bundle, payload_scope)
    with open(output_path, "wb") as output_file:
        output_file.write(data)
    return hashlib.sha256(data).hexdigest()


def write_sha256(sha256_hash, output_path):
    with open(output_path, "w", encoding="utf-8") as output_file:
        output_file.write(sha256_hash + "\n")


def _stable_object_key(value):
    return json.dumps(_stable_value(value), sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def _stable_value(value):
    if isinstance(value, dict):
        return {key: _stable_value(value[key]) for key in sorted(value)}
    if isinstance(value, list):
        return [_stable_value(item) for item in value]
    return value
