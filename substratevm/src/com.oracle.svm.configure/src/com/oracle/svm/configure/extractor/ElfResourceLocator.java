/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.configure.extractor;

import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;

import com.oracle.svm.configure.ConfigurationUsageException;

/**
 * Resource locator for ELF 64-bit images.
 */
final class ElfResourceLocator extends ResourceLocator {
    private static final byte[] MAGIC = {0x7F, 'E', 'L', 'F'};
    private static final int ELFCLASS64 = 2;
    private static final int ELFDATA2LSB = 1;
    private static final int ET_EXEC = 2;
    private static final int ET_DYN = 3;

    private static final class SectionNames {
        static final String SHSTRTAB = ".shstrtab";
        static final String DYNSTR = ".dynstr";
        static final String DYNSYM = ".dynsym";
        static final String DATA = ".data";
    }

    private record HeaderInfo(long sectionHeaderTableOffset, long sectionHeaderEntrySize, long numberOfSections, long sectionHeaderStringIndex) {
        static final int EI_CLASS_OFFSET = 0x4;
        static final int EI_DATA_OFFSET = 0x5;
        static final int E_TYPE_OFFSET = 0x10;
        static final int E_SHOFF_OFFSET = 0x28;
        static final int E_SHENTSIZE_OFFSET = 0x3A;
        static final int E_SHNUM_OFFSET = 0x3C;
        static final int E_SHSTRNDX_OFFSET = 0x3E;
    }

    private record SectionHeaderInfo(long nameIndex, long type, long virtualAddress, long fileOffset, long size, long entrySize) {
        static final long SIZE = 64;

        static final int NAME_OFFSET = 0x0;
        static final int TYPE_OFFSET = 0x4;
        static final int ADDR_OFFSET = 0x10;
        static final int OFFSET_OFFSET = 0x18;
        static final int SIZE_OFFSET = 0x20;
        static final int ENTSIZE_OFFSET = 0x38;

        static final int SHT_PROGBITS = 1;
        static final int SHT_STRTAB = 3;
        static final int SHT_DYNSYM = 11;
    }

    private record SymbolInfo(long nameIndex, int info, long value) {
        static final int NAME_OFFSET = 0x0;
        static final int INFO_OFFSET = 0x4;
        static final int VALUE_OFFSET = 0x8;

        static final int STB_GLOBAL = 1;
        static final int STT_OBJECT = 1;
    }

    private record StringTableInfo(long offset, long size) {
    }

    private record SymbolTableInfo(long offset, long size, long entrySize) {
    }

    private record DataSectionInfo(long virtualAddress, long fileOffset, long size) {
    }

    private record SectionsInfo(StringTableInfo shstrtab, StringTableInfo dynstr, SymbolTableInfo dynsym, DataSectionInfo data) {
    }

    ElfResourceLocator(MemorySegment segment) {
        super(segment);
    }

    @Override
    String getSupportedFileFormat() {
        return "ELF 64-bit";
    }

    @Override
    ByteOrder getByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    boolean matchesMagic() {
        if (segment.byteSize() < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (getUInt8(i) != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected ResourceLocation locateResourceImpl(String symbol) {
        HeaderInfo headerInfo = parseHeader();
        SectionsInfo sectionsInfo = parseSections(headerInfo);
        SymbolAddresses symbolAddresses = findSymbolAddresses(symbol, sectionsInfo);
        return computeResourceLocation(sectionsInfo, symbolAddresses);
    }

    private HeaderInfo parseHeader() {
        if (getUInt8(HeaderInfo.EI_CLASS_OFFSET) != ELFCLASS64) {
            throw new ConfigurationUsageException("Only ELF64 is supported.");
        }
        if (getUInt8(HeaderInfo.EI_DATA_OFFSET) != ELFDATA2LSB) {
            throw new ConfigurationUsageException("Only little-endian ELF is supported.");
        }
        int eType = getUInt16(HeaderInfo.E_TYPE_OFFSET);
        if (eType != ET_EXEC && eType != ET_DYN) {
            throw new ConfigurationUsageException("Not an executable or shared object.");
        }

        long sectionHeaderTableOffset = getUInt64(HeaderInfo.E_SHOFF_OFFSET);
        long sectionHeaderEntrySize = getUInt16(HeaderInfo.E_SHENTSIZE_OFFSET);
        long numberOfSections = getUInt16(HeaderInfo.E_SHNUM_OFFSET);
        long sectionHeaderStringIndex = getUInt16(HeaderInfo.E_SHSTRNDX_OFFSET);

        if (sectionHeaderEntrySize < SectionHeaderInfo.SIZE) {
            throw new ConfigurationUsageException("Invalid section header size.");
        }
        long sectionHeaderTableSize = Math.multiplyExact(numberOfSections, sectionHeaderEntrySize);
        validateRange(sectionHeaderTableOffset, sectionHeaderTableSize);

        return new HeaderInfo(sectionHeaderTableOffset, sectionHeaderEntrySize, numberOfSections, sectionHeaderStringIndex);
    }

    private SectionsInfo parseSections(HeaderInfo headerInfo) {
        StringTableInfo shstrtab = parseShstrtab(headerInfo);
        StringTableInfo dynstr = null;
        SymbolTableInfo dynsym = null;
        DataSectionInfo data = null;

        for (long sectionIndex = 0; sectionIndex < headerInfo.numberOfSections(); sectionIndex++) {
            long sectionHeaderOffset = Math.addExact(headerInfo.sectionHeaderTableOffset(), Math.multiplyExact(sectionIndex, headerInfo.sectionHeaderEntrySize()));
            validateRange(sectionHeaderOffset, headerInfo.sectionHeaderEntrySize());

            SectionHeaderInfo sectionHeader = parseSectionHeader(sectionHeaderOffset);
            long nameOffset = Math.addExact(shstrtab.offset(), sectionHeader.nameIndex());
            if (sectionHeader.nameIndex() >= shstrtab.size()) {
                // Invalid name index
                continue;
            }
            String sectionName = getNullTerminatedString(nameOffset, shstrtab.size() - sectionHeader.nameIndex());

            if (sectionHeader.type() == SectionHeaderInfo.SHT_STRTAB && SectionNames.DYNSTR.equals(sectionName)) {
                dynstr = new StringTableInfo(sectionHeader.fileOffset(), sectionHeader.size());
                validateRange(dynstr.offset(), dynstr.size());
            } else if (sectionHeader.type() == SectionHeaderInfo.SHT_DYNSYM && SectionNames.DYNSYM.equals(sectionName)) {
                dynsym = new SymbolTableInfo(sectionHeader.fileOffset(), sectionHeader.size(), sectionHeader.entrySize());
                validateRange(dynsym.offset(), dynsym.size());
            } else if (sectionHeader.type() == SectionHeaderInfo.SHT_PROGBITS && SectionNames.DATA.equals(sectionName)) {
                data = new DataSectionInfo(sectionHeader.virtualAddress(), sectionHeader.fileOffset(), sectionHeader.size());
            }
        }

        if (dynstr == null) {
            throw new ConfigurationUsageException("Could not find %s.".formatted(SectionNames.DYNSTR));
        }
        if (dynsym == null) {
            throw new ConfigurationUsageException("Could not find %s.".formatted(SectionNames.DYNSYM));
        }
        if (data == null) {
            throw new ConfigurationUsageException("Could not find %s.".formatted(SectionNames.DATA));
        }

        return new SectionsInfo(shstrtab, dynstr, dynsym, data);
    }

    private StringTableInfo parseShstrtab(HeaderInfo headerInfo) {
        long shstrHeaderOffset = Math.addExact(headerInfo.sectionHeaderTableOffset(), Math.multiplyExact(headerInfo.sectionHeaderStringIndex(), headerInfo.sectionHeaderEntrySize()));
        validateRange(shstrHeaderOffset, headerInfo.sectionHeaderEntrySize());

        SectionHeaderInfo shstrHeader = parseSectionHeader(shstrHeaderOffset);
        if (shstrHeader.type() != SectionHeaderInfo.SHT_STRTAB) {
            throw new ConfigurationUsageException("Invalid %s type.".formatted(SectionNames.SHSTRTAB));
        }
        long shstrOffset = shstrHeader.fileOffset();
        long shstrSize = shstrHeader.size();
        validateRange(shstrOffset, shstrSize);
        return new StringTableInfo(shstrOffset, shstrSize);
    }

    private SectionHeaderInfo parseSectionHeader(long offset) {
        long nameIndex = getUInt32(offset + SectionHeaderInfo.NAME_OFFSET);
        long type = getUInt32(offset + SectionHeaderInfo.TYPE_OFFSET);
        long virtualAddress = getUInt64(offset + SectionHeaderInfo.ADDR_OFFSET);
        long fileOffset = getUInt64(offset + SectionHeaderInfo.OFFSET_OFFSET);
        long size = getUInt64(offset + SectionHeaderInfo.SIZE_OFFSET);
        long entrySize = getUInt64(offset + SectionHeaderInfo.ENTSIZE_OFFSET);
        return new SectionHeaderInfo(nameIndex, type, virtualAddress, fileOffset, size, entrySize);
    }

    private SymbolAddresses findSymbolAddresses(String symbol, SectionsInfo sectionsInfo) {
        SymbolTableInfo dynsym = sectionsInfo.dynsym();
        StringTableInfo dynstr = sectionsInfo.dynstr();

        if (dynsym.entrySize() == 0 || dynsym.size() % dynsym.entrySize() != 0) {
            throw new ConfigurationUsageException("Invalid .dynsym entry size.");
        }
        long numberOfSymbols = dynsym.size() / dynsym.entrySize();

        String lengthSymbolName = lengthSymbolName(symbol);
        long resourceVirtualAddress = -1;
        long lengthVirtualAddress = -1;

        for (long symbolIndex = 0; symbolIndex < numberOfSymbols; symbolIndex++) {
            long symbolOffset = Math.addExact(dynsym.offset(), Math.multiplyExact(symbolIndex, dynsym.entrySize()));
            validateRange(symbolOffset, dynsym.entrySize());

            long nameIndex = getUInt32(symbolOffset + SymbolInfo.NAME_OFFSET);
            if (nameIndex >= dynstr.size()) {
                continue;
            }
            int info = getUInt8(symbolOffset + SymbolInfo.INFO_OFFSET);
            int bind = (info >> 4);
            int type = (info & 0xF);
            if (type != SymbolInfo.STT_OBJECT || bind != SymbolInfo.STB_GLOBAL) {
                continue;
            }

            String name = getNullTerminatedString(dynstr.offset() + nameIndex, dynstr.size() - nameIndex);
            long value = getUInt64(symbolOffset + SymbolInfo.VALUE_OFFSET);

            if (symbol.equals(name)) {
                resourceVirtualAddress = value;
            } else if (lengthSymbolName.equals(name)) {
                lengthVirtualAddress = value;
            }
        }

        if (resourceVirtualAddress == -1 || lengthVirtualAddress == -1) {
            throw symbolsNotFoundException(symbol, lengthSymbolName(symbol));
        }

        return new SymbolAddresses(resourceVirtualAddress, lengthVirtualAddress);
    }

    private ResourceLocation computeResourceLocation(SectionsInfo sectionsInfo, SymbolAddresses symbolAddresses) {
        DataSectionInfo data = sectionsInfo.data();

        if (symbolAddresses.resourceVirtualAddress() < data.virtualAddress() || symbolAddresses.lengthVirtualAddress() < data.virtualAddress()) {
            throw new ConfigurationUsageException("Invalid offset or address.");
        }

        long resourceOffset = Math.addExact(data.fileOffset(), symbolAddresses.resourceVirtualAddress() - data.virtualAddress());
        long lengthOffset = Math.addExact(data.fileOffset(), symbolAddresses.lengthVirtualAddress() - data.virtualAddress());
        long length = getUInt64(lengthOffset);
        return new ResourceLocation(resourceOffset, length);
    }
}
