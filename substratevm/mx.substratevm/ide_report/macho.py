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

"""Mach-O extraction for embedded IDE report envelopes."""

import mmap
import os
import struct
from dataclasses import dataclass


_MACH_O_64_MAGIC = 0xFEEDFACF
_LC_SYMTAB = 0x2
_LC_SEGMENT_64 = 0x19
_REPORT_SECTION_NAME = "__svm_idereport"
_REPORT_SEGMENT_NAME = "__TEXT"
_REPORT_SYMBOL_NAME = "_ide_report"
_REPORT_LENGTH_SYMBOL_NAME = "_ide_report_length"
_VM_PROT_WRITE = 0x2
_S_ATTR_PURE_INSTRUCTIONS = 0x80000000
_S_ATTR_SOME_INSTRUCTIONS = 0x00000400

_HEADER = struct.Struct("<IiiIIIII")
_LOAD_COMMAND = struct.Struct("<II")
_SEGMENT_64 = struct.Struct("<II16sQQQQiiII")
_SECTION_64 = struct.Struct("<16s16sQQIIIIIIII")
_SYMTAB_COMMAND = struct.Struct("<IIIIII")
_NLIST_64 = struct.Struct("<IBBHQ")
_UINT32 = struct.Struct("<I")
_UINT64 = struct.Struct("<Q")


class MachOIDEReportError(ValueError):
    pass


class MachOIDEReportNotFoundError(MachOIDEReportError):
    pass


@dataclass(frozen=True)
class _Segment:
    virtual_address: int
    virtual_size: int
    file_offset: int
    file_size: int
    initial_protection: int


@dataclass(frozen=True)
class _Section:
    segment_name: str
    virtual_address: int
    size: int
    file_offset: int
    flags: int


@dataclass(frozen=True)
class _Symtab:
    symbol_offset: int
    symbol_count: int
    string_offset: int
    string_size: int


def extract_ide_report_envelope(path):
    with open(path, "rb") as image_file:
        if os.fstat(image_file.fileno()).st_size == 0:
            raise MachOIDEReportError("Mach-O image is empty")
        with mmap.mmap(image_file.fileno(), 0, access=mmap.ACCESS_READ) as image:
            return _extract_ide_report_envelope(image)


def _extract_ide_report_envelope(image):
    if len(image) < _UINT32.size or _UINT32.unpack_from(image, 0)[0] != _MACH_O_64_MAGIC:
        raise MachOIDEReportError("image: currently supports little-endian Mach-O 64-bit images only")
    header = _unpack(_HEADER, image, 0, "Mach-O header")
    magic, _, _, _, command_count, commands_size, _, _ = header
    assert magic == _MACH_O_64_MAGIC

    commands_end = _checked_end(_HEADER.size, commands_size, len(image), "Mach-O load commands")
    command_offset = _HEADER.size
    segments = {}
    report_section = None
    symtab = None
    for _ in range(command_count):
        command, command_size = _unpack(_LOAD_COMMAND, image, command_offset, "Mach-O load command")
        if command_size < _LOAD_COMMAND.size:
            raise MachOIDEReportError("Invalid Mach-O load command size")
        command_end = _checked_end(command_offset, command_size, commands_end, "Mach-O load command")

        if command == _LC_SEGMENT_64:
            segment_values = _unpack(_SEGMENT_64, image, command_offset, "Mach-O segment command")
            (
                _,
                _,
                raw_segment_name,
                virtual_address,
                virtual_size,
                file_offset,
                file_size,
                _,
                initial_protection,
                section_count,
                _,
            ) = segment_values
            segment_name = _fixed_string(raw_segment_name)
            required_size = _SEGMENT_64.size + section_count * _SECTION_64.size
            if required_size > command_size:
                raise MachOIDEReportError("Mach-O segment command has a truncated section table")
            segments[segment_name] = _Segment(virtual_address, virtual_size, file_offset, file_size, initial_protection)
            section_offset = command_offset + _SEGMENT_64.size
            for section_index in range(section_count):
                values = _unpack(
                    _SECTION_64,
                    image,
                    section_offset + section_index * _SECTION_64.size,
                    "Mach-O section",
                )
                raw_section_name, raw_section_segment, address, size, offset = values[:5]
                if _fixed_string(raw_section_name) == _REPORT_SECTION_NAME:
                    section_segment_name = _fixed_string(raw_section_segment)
                    if section_segment_name != _REPORT_SEGMENT_NAME:
                        raise MachOIDEReportError(
                            "Embedded IDE report section is not in the read-only Mach-O __TEXT segment"
                        )
                    report_section = _Section(section_segment_name, address, size, offset, values[8])
        elif command == _LC_SYMTAB:
            values = _unpack(_SYMTAB_COMMAND, image, command_offset, "Mach-O symbol table command")
            _, _, symbol_offset, symbol_count, string_offset, string_size = values
            symtab = _Symtab(symbol_offset, symbol_count, string_offset, string_size)

        command_offset = command_end

    if command_offset != commands_end:
        raise MachOIDEReportError("Mach-O load command size does not match the header")
    if report_section is None:
        raise MachOIDEReportNotFoundError("Could not find the Mach-O __svm_idereport section")
    report_segment = segments.get(report_section.segment_name)
    if report_segment is None:
        raise MachOIDEReportError("Could not find the Mach-O segment containing the embedded IDE report")
    if report_segment.initial_protection & _VM_PROT_WRITE:
        raise MachOIDEReportError("Embedded IDE report segment is writable")
    if report_section.flags & (_S_ATTR_PURE_INSTRUCTIONS | _S_ATTR_SOME_INSTRUCTIONS):
        raise MachOIDEReportError("Embedded IDE report section is marked as executable instructions")
    if symtab is None:
        raise MachOIDEReportError("Could not find the Mach-O symbol table")

    report_address, length_address = _find_symbols(image, symtab)
    if report_address != report_section.virtual_address:
        raise MachOIDEReportError("IDE report symbol does not point to the embedded report section")
    report_offset = _address_to_file_offset(report_segment, report_address, len(image))
    if report_offset != report_section.file_offset:
        raise MachOIDEReportError("IDE report section address and file offset do not match")
    length_offset = _address_to_file_offset(report_segment, length_address, len(image))
    envelope_size = _unpack(_UINT64, image, length_offset, "IDE report length")[0]
    if length_address != report_address + envelope_size:
        raise MachOIDEReportError("IDE report length symbol does not immediately follow the envelope")
    if envelope_size + _UINT64.size > report_section.size:
        raise MachOIDEReportError("IDE report envelope exceeds its Mach-O section")
    _checked_end(report_section.file_offset, envelope_size, len(image), "IDE report envelope")
    return bytes(image[report_section.file_offset : report_section.file_offset + envelope_size])


def _find_symbols(image, symtab):
    _checked_end(
        symtab.symbol_offset,
        symtab.symbol_count * _NLIST_64.size,
        len(image),
        "Mach-O symbol table",
    )
    string_end = _checked_end(symtab.string_offset, symtab.string_size, len(image), "Mach-O string table")
    report_address = None
    length_address = None
    for symbol_index in range(symtab.symbol_count):
        symbol_offset = symtab.symbol_offset + symbol_index * _NLIST_64.size
        string_index, _, _, _, value = _unpack(_NLIST_64, image, symbol_offset, "Mach-O symbol")
        if string_index >= symtab.string_size:
            raise MachOIDEReportError("Mach-O symbol has an invalid string-table offset")
        name = _null_terminated_string(image, symtab.string_offset + string_index, string_end)
        if name == _REPORT_SYMBOL_NAME:
            report_address = value
        elif name == _REPORT_LENGTH_SYMBOL_NAME:
            length_address = value
    if report_address is None or length_address is None:
        raise MachOIDEReportError("Could not find exported ide_report and ide_report_length symbols")
    return report_address, length_address


def _address_to_file_offset(segment, address, image_size):
    if address < segment.virtual_address or address >= segment.virtual_address + segment.virtual_size:
        raise MachOIDEReportError("IDE report symbol is outside its Mach-O segment")
    offset_in_segment = address - segment.virtual_address
    if offset_in_segment >= segment.file_size:
        raise MachOIDEReportError("IDE report symbol does not map to file data")
    file_offset = segment.file_offset + offset_in_segment
    _checked_end(file_offset, _UINT64.size, image_size, "IDE report symbol")
    return file_offset


def _unpack(formatter, data, offset, description):
    _checked_end(offset, formatter.size, len(data), description)
    return formatter.unpack_from(data, offset)


def _checked_end(offset, size, limit, description):
    if offset < 0 or size < 0 or offset > limit or size > limit - offset:
        raise MachOIDEReportError("Truncated or invalid {}".format(description))
    return offset + size


def _fixed_string(value):
    try:
        return value.split(b"\0", 1)[0].decode("ascii")
    except UnicodeDecodeError as error:
        raise MachOIDEReportError("Mach-O name is not ASCII") from error


def _null_terminated_string(data, offset, limit):
    end = data.find(b"\0", offset, limit)
    if end < 0:
        raise MachOIDEReportError("Mach-O string table entry is not null-terminated")
    try:
        return bytes(data[offset:end]).decode("utf-8")
    except UnicodeDecodeError as error:
        raise MachOIDEReportError("Mach-O symbol name is not UTF-8") from error
