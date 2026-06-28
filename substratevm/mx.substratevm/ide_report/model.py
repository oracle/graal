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

"""Neutral IDE report model."""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass(frozen=True)
class SourceLocation:
    filename: Optional[str] = None
    line: Optional[int] = None
    start_line: Optional[int] = None
    end_line: Optional[int] = None

    def has_location(self):
        return self.filename is not None

    def to_dict(self):
        result = {}
        if self.filename is not None:
            result["filename"] = self.filename
        if self.line is not None:
            result["line"] = self.line
        if self.start_line is not None:
            result["start-line"] = self.start_line
        if self.end_line is not None:
            result["end-line"] = self.end_line
        return result


@dataclass(frozen=True)
class MethodReference:
    filename: Optional[str] = None
    class_name: Optional[str] = None
    method_name: Optional[str] = None
    method_signature: Optional[str] = None
    extensions: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self):
        result = dict(self.extensions)
        if self.filename is not None:
            result["filename"] = self.filename
        if self.class_name is not None:
            result["class"] = self.class_name
        if self.method_name is not None:
            result["mthname"] = self.method_name
        if self.method_signature is not None:
            result["mthsig"] = self.method_signature
        return result


@dataclass(frozen=True)
class ReportRecord:
    kind: Optional[str] = None
    category: Optional[str] = None
    location: SourceLocation = field(default_factory=SourceLocation)
    message: Optional[str] = None
    class_name: Optional[str] = None
    field_name: Optional[str] = None
    method_name: Optional[str] = None
    method_signature: Optional[str] = None
    inline_context: List[SourceLocation] = field(default_factory=list)
    extensions: Dict[str, Any] = field(default_factory=dict)

    def has_source_location(self):
        return self.location.has_location()

    def to_dict(self):
        result = dict(self.extensions)
        if self.kind is not None:
            result["kind"] = self.kind
        if self.category is not None:
            result["category"] = self.category
        result.update(self.location.to_dict())
        if self.message is not None:
            result["msg"] = self.message
        if self.class_name is not None:
            result["class"] = self.class_name
        if self.field_name is not None:
            result["field"] = self.field_name
        if self.method_name is not None:
            result["mthname"] = self.method_name
        if self.method_signature is not None:
            result["mthsig"] = self.method_signature
        if self.inline_context:
            result["inlinectx"] = [location.to_dict() for location in self.inline_context]
        return result


@dataclass(frozen=True)
class ReportProvenance:
    source: str
    format_name: str

    def to_dict(self):
        return {
            "source": self.source,
            "format": self.format_name,
        }


@dataclass(frozen=True)
class ReportBundle:
    provenance: ReportProvenance
    reports: List[ReportRecord] = field(default_factory=list)
    used_methods: List[MethodReference] = field(default_factory=list)
    extensions: Dict[str, Any] = field(default_factory=dict)
    payload_scope: Optional[str] = None

    def to_dict(self):
        result = dict(self.extensions)
        result["provenance"] = self.provenance.to_dict()
        result["reports"] = [record.to_dict() for record in self.reports]
        result["used_methods"] = [method.to_dict() for method in self.used_methods]
        return result
