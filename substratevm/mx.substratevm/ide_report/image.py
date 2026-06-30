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

"""Object-file format dispatch for embedded IDE reports."""

from . import elf, macho


_ELF_MAGIC = b"\x7fELF"
_MACH_O_64_LITTLE_ENDIAN_MAGIC = b"\xcf\xfa\xed\xfe"


class IDEReportImageError(ValueError):
    pass


def extract_ide_report_envelope(path):
    with open(path, "rb") as image_file:
        magic = image_file.read(4)
    image_format = detect_image_format(magic)
    if image_format == "elf":
        return elf.extract_ide_report_envelope(path)
    if image_format == "mach-o":
        return macho.extract_ide_report_envelope(path)
    raise IDEReportImageError("image: currently supports little-endian ELF and Mach-O 64-bit images only")


def detect_image_format(magic):
    if magic == _ELF_MAGIC:
        return "elf"
    if magic == _MACH_O_64_LITTLE_ENDIAN_MAGIC:
        return "mach-o"
    return None
