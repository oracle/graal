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

"""ELF extraction for embedded IDE report envelopes."""

import mmap
import os
import struct
from dataclasses import dataclass


_ELF_MAGIC = b"\x7fELF"
_ELFCLASS64 = 2
_ELFDATA2LSB = 1
_EV_CURRENT = 1
_ET_EXEC = 2
_ET_DYN = 3
_PT_LOAD = 1
_PF_W = 2
_SHT_PROGBITS = 1
_SHT_SYMTAB = 2
_SHT_STRTAB = 3
_SHT_DYNSYM = 11
_SHF_WRITE = 1
_SHF_ALLOC = 2
_SHF_EXECINSTR = 4
_SHN_XINDEX = 0xFFFF
_STB_GLOBAL = 1
_STT_OBJECT = 1
_REPORT_SECTION_NAME = ".svm_ide_report"
_REPORT_SYMBOL_NAME = "ide_report"
_REPORT_LENGTH_SYMBOL_NAME = "ide_report_length"

_HEADER = struct.Struct("<16sHHIQQQIHHHHHH")
_PROGRAM_HEADER = struct.Struct("<IIQQQQQQ")
_SECTION_HEADER = struct.Struct("<IIQQQQIIQQ")
_SYMBOL = struct.Struct("<IBBHQQ")
_UINT64 = struct.Struct("<Q")


class ELFIDEReportError(ValueError):
    pass


class ELFIDEReportNotFoundError(ELFIDEReportError):
    pass


@dataclass(frozen=True)
class _Header:
    program_offset: int
    program_entry_size: int
    program_count: int
    section_offset: int
    section_entry_size: int
    section_count: int
    section_names_index: int


@dataclass(frozen=True)
class _Section:
    index: int
    name_index: int
    type: int
    flags: int
    address: int
    file_offset: int
    size: int
    link: int
    entry_size: int


def extract_ide_report_envelope(path):
    with open(path, "rb") as image_file:
        if os.fstat(image_file.fileno()).st_size == 0:
            raise ELFIDEReportError("ELF image is empty")
        with mmap.mmap(image_file.fileno(), 0, access=mmap.ACCESS_READ) as image:
            return _extract_ide_report_envelope(image)


def _extract_ide_report_envelope(image):
    header = _parse_header(image)
    sections = _parse_sections(image, header)
    names = _string_table(image, sections[header.section_names_index], "ELF section-name string table")
    report_sections = [section for section in sections if _string(names, section.name_index) == _REPORT_SECTION_NAME]
    if not report_sections:
        raise ELFIDEReportNotFoundError("Could not find the ELF .svm_ide_report section")
    if len(report_sections) != 1:
        raise ELFIDEReportError("Expected exactly one ELF .svm_ide_report section")
    report_section = report_sections[0]
    _validate_report_section(image, header, report_section)

    length_offset = report_section.file_offset + report_section.size - _UINT64.size
    envelope_size = _unpack(_UINT64, image, length_offset, "IDE report length")[0]
    if envelope_size + _UINT64.size != report_section.size:
        raise ELFIDEReportError("IDE report length does not match its ELF section size")
    _validate_symbols(image, sections, report_section, envelope_size)
    return bytes(image[report_section.file_offset : report_section.file_offset + envelope_size])


def _parse_header(image):
    values = _unpack(_HEADER, image, 0, "ELF header")
    ident = values[0]
    if ident[:4] != _ELF_MAGIC:
        raise ELFIDEReportError("Image is not an ELF file")
    if ident[4] != _ELFCLASS64 or ident[5] != _ELFDATA2LSB:
        raise ELFIDEReportError("image: currently supports little-endian ELF 64-bit images only")
    if ident[6] != _EV_CURRENT or values[3] != _EV_CURRENT:
        raise ELFIDEReportError("Unsupported ELF version")
    if values[1] not in (_ET_EXEC, _ET_DYN):
        raise ELFIDEReportError("ELF image is not an executable or shared object")

    program_offset, section_offset = values[5], values[6]
    program_entry_size, program_count = values[9], values[10]
    section_entry_size, section_count, section_names_index = values[11], values[12], values[13]
    if section_offset == 0 or section_entry_size < _SECTION_HEADER.size:
        raise ELFIDEReportError("ELF image has an invalid section-header table")
    _checked_end(section_offset, section_entry_size, len(image), "ELF section-header table")
    section_zero = _unpack(_SECTION_HEADER, image, section_offset, "ELF section header")
    if section_count == 0:
        section_count = section_zero[5]
    if section_names_index == _SHN_XINDEX:
        section_names_index = section_zero[6]
    if section_count == 0 or section_names_index >= section_count:
        raise ELFIDEReportError("ELF image has invalid section counts")
    _checked_end(section_offset, section_count * section_entry_size, len(image), "ELF section-header table")
    if program_count:
        if program_offset == 0 or program_entry_size < _PROGRAM_HEADER.size:
            raise ELFIDEReportError("ELF image has an invalid program-header table")
        _checked_end(program_offset, program_count * program_entry_size, len(image), "ELF program-header table")
    return _Header(
        program_offset,
        program_entry_size,
        program_count,
        section_offset,
        section_entry_size,
        section_count,
        section_names_index,
    )


def _parse_sections(image, header):
    sections = []
    for index in range(header.section_count):
        offset = header.section_offset + index * header.section_entry_size
        values = _unpack(_SECTION_HEADER, image, offset, "ELF section header")
        sections.append(
            _Section(index, values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[9])
        )
    return sections


def _validate_report_section(image, header, section):
    if section.type != _SHT_PROGBITS:
        raise ELFIDEReportError("Embedded IDE report is not stored in an ELF PROGBITS section")
    if not section.flags & _SHF_ALLOC:
        raise ELFIDEReportError("Embedded IDE report ELF section is not allocated")
    if section.flags & _SHF_WRITE:
        raise ELFIDEReportError("Embedded IDE report ELF section is writable")
    if section.flags & _SHF_EXECINSTR:
        raise ELFIDEReportError("Embedded IDE report ELF section is executable")
    if section.size < _UINT64.size:
        raise ELFIDEReportError("Embedded IDE report ELF section is truncated")
    _checked_end(section.file_offset, section.size, len(image), "embedded IDE report ELF section")
    if header.program_count:
        _validate_load_segment(image, header, section)


def _validate_load_segment(image, header, section):
    containing_segments = []
    for index in range(header.program_count):
        offset = header.program_offset + index * header.program_entry_size
        values = _unpack(_PROGRAM_HEADER, image, offset, "ELF program header")
        if values[0] != _PT_LOAD:
            continue
        flags, file_offset, virtual_address, file_size, memory_size = (
            values[1],
            values[2],
            values[3],
            values[5],
            values[6],
        )
        file_contains = (
            file_offset <= section.file_offset and section.file_offset + section.size <= file_offset + file_size
        )
        memory_contains = (
            virtual_address <= section.address and section.address + section.size <= virtual_address + memory_size
        )
        if file_contains and memory_contains:
            containing_segments.append(flags)
    if len(containing_segments) != 1:
        raise ELFIDEReportError("Could not identify the ELF load segment containing the embedded IDE report")
    if containing_segments[0] & _PF_W:
        raise ELFIDEReportError("Embedded IDE report ELF load segment is writable")


def _validate_symbols(image, sections, report_section, envelope_size):
    symbol_tables = sorted(
        (section for section in sections if section.type in (_SHT_DYNSYM, _SHT_SYMTAB)),
        key=lambda section: section.type != _SHT_DYNSYM,
    )
    for symbol_table in symbol_tables:
        symbols = _find_symbols(image, sections, symbol_table)
        if _REPORT_SYMBOL_NAME in symbols and _REPORT_LENGTH_SYMBOL_NAME in symbols:
            report_symbol = symbols[_REPORT_SYMBOL_NAME]
            length_symbol = symbols[_REPORT_LENGTH_SYMBOL_NAME]
            if report_symbol[0] != report_section.index or length_symbol[0] != report_section.index:
                raise ELFIDEReportError("IDE report symbols do not reference the embedded report ELF section")
            if report_symbol[1] != report_section.address:
                raise ELFIDEReportError("IDE report symbol does not point to the embedded report ELF section")
            if length_symbol[1] != report_symbol[1] + envelope_size:
                raise ELFIDEReportError("IDE report length symbol does not immediately follow the envelope")
            return
    raise ELFIDEReportError("Could not find exported ide_report and ide_report_length ELF symbols")


def _find_symbols(image, sections, symbol_table):
    if symbol_table.link >= len(sections):
        raise ELFIDEReportError("ELF symbol table has an invalid string-table link")
    strings = _string_table(image, sections[symbol_table.link], "ELF symbol string table")
    if symbol_table.entry_size < _SYMBOL.size or symbol_table.size % symbol_table.entry_size:
        raise ELFIDEReportError("ELF symbol table has an invalid entry size")
    _checked_end(symbol_table.file_offset, symbol_table.size, len(image), "ELF symbol table")
    result = {}
    for index in range(symbol_table.size // symbol_table.entry_size):
        offset = symbol_table.file_offset + index * symbol_table.entry_size
        name_index, info, _, section_index, value, _ = _unpack(_SYMBOL, image, offset, "ELF symbol")
        name = _string(strings, name_index)
        if info >> 4 == _STB_GLOBAL and info & 0xF == _STT_OBJECT:
            if name in (_REPORT_SYMBOL_NAME, _REPORT_LENGTH_SYMBOL_NAME):
                result[name] = (section_index, value)
    return result


def _string_table(image, section, description):
    if section.type != _SHT_STRTAB:
        raise ELFIDEReportError("{} has an invalid section type".format(description))
    end = _checked_end(section.file_offset, section.size, len(image), description)
    return image[section.file_offset : end]


def _string(table, offset):
    if offset >= len(table):
        raise ELFIDEReportError("ELF string-table offset is out of bounds")
    end = table.find(b"\0", offset)
    if end < 0:
        raise ELFIDEReportError("ELF string-table entry is not null-terminated")
    try:
        return bytes(table[offset:end]).decode("utf-8")
    except UnicodeDecodeError as error:
        raise ELFIDEReportError("ELF string-table entry is not UTF-8") from error


def _unpack(formatter, data, offset, description):
    _checked_end(offset, formatter.size, len(data), description)
    return formatter.unpack_from(data, offset)


def _checked_end(offset, size, limit, description):
    if offset < 0 or size < 0 or offset > limit or size > limit - offset:
        raise ELFIDEReportError("{} is outside the image".format(description))
    return offset + size
