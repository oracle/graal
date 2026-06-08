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
import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.configure.ConfigurationUsageException;

/**
 * Resource locator for PE/COFF 64-bit images targeting AMD64.
 */
final class PECoffResourceLocator extends ResourceLocator {
    private static final int MAGIC = 0x5A4D;
    private static final int MACHINE_AMD64 = 0x8664;

    private static final int EXPORT_NAME_RVA_SIZE = 4;
    private static final int EXPORT_ORDINAL_SIZE = 2;
    private static final int EXPORT_FUNCTION_RVA_SIZE = 4;

    private record DOSHeaderInfo(long peHeaderOffset) {
        static final int SIZE = 64;

        /**
         * Points to the start of {@link PEHeaderInfo}.
         */
        static final int E_LFANEW_OFFSET = 0x3C;
    }

    private record PEHeaderInfo(long machine, long numberOfSections, long optionalHeaderOffset, long optionalHeaderSize) {
        static final int SIZE = 24;
        static final long PE_SIGNATURE = 0x4550;

        static final int SIGNATURE_OFFSET = 0x0;
        static final int MACHINE_OFFSET = 0x4;
        static final int NUMBER_OF_SECTIONS_OFFSET = 0x6;
        static final int OPTIONAL_HEADER_SIZE_OFFSET = 0x14;
    }

    private record OptionalHeaderInfo(DataDirectoryInfo exportDataDirectory) {
        static final int MAGIC = 0x20B;

        static final int MAGIC_OFFSET = 0x0;
        static final int DATA_DIRECTORIES_ARRAY_OFFSET = 0x70;
    }

    private record DataDirectoryInfo(long virtualAddress, long size) {
        static final int SIZE = 8;

        static final int VA_OFFSET = 0x0;
        static final int SIZE_OFFSET = 0x4;
    }

    private record SectionInfo(String name, long virtualAddress, long virtualSize, long pointerToRawData, long sizeOfRawData) {
        static final long SIZE = 40;

        static final int NAME_OFFSET = 0x0;
        static final int NAME_LENGTH = 8;
        static final int VIRTUAL_SIZE_OFFSET = 0x8;
        static final int VIRTUAL_ADDRESS_OFFSET = 0xC;
        static final int SIZE_OF_RAW_DATA_OFFSET = 0x10;
        static final int POINTER_TO_RAW_DATA_OFFSET = 0x14;
    }

    private record SectionsInfo(List<SectionInfo> sections) {
    }

    private record ExportDirectoryInfo(long numberOfFunctions, long numberOfNames, long addressOfFunctions, long addressOfNames, long addressOfNameOrdinals) {
        static final int SIZE = 40;

        static final int NUMBER_OF_FUNCTIONS_OFFSET = 0x14;
        static final int NUMBER_OF_NAMES_OFFSET = 0x18;
        static final int ADDRESS_OF_FUNCTIONS_OFFSET = 0x1C;
        static final int ADDRESS_OF_NAMES_OFFSET = 0x20;
        static final int ADDRESS_OF_NAME_ORDINALS_OFFSET = 0x24;
    }

    PECoffResourceLocator(MemorySegment segment) {
        super(segment);
    }

    @Override
    String getSupportedFileFormat() {
        return "PE/COFF 64-bit";
    }

    @Override
    ByteOrder getByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    boolean matchesMagic() {
        return getUInt16(0) == MAGIC;
    }

    @Override
    protected ResourceLocation locateResourceImpl(String symbol) {
        DOSHeaderInfo dosHeader = parseDOSHeader();
        PEHeaderInfo peHeader = parsePEHeader(dosHeader);
        OptionalHeaderInfo optionalHeader = parseOptionalHeader(peHeader);
        SectionsInfo sectionsInfo = parseSections(dosHeader, peHeader);
        SymbolAddresses symbolAddresses = findSymbolAddresses(symbol, optionalHeader, sectionsInfo);
        return computeResourceLocation(sectionsInfo, symbolAddresses);
    }

    private DOSHeaderInfo parseDOSHeader() {
        validateRange(0, DOSHeaderInfo.SIZE);
        long peHeaderOffset = getUInt32(DOSHeaderInfo.E_LFANEW_OFFSET);
        return new DOSHeaderInfo(peHeaderOffset);
    }

    private PEHeaderInfo parsePEHeader(DOSHeaderInfo dosHeader) {
        long baseOffset = dosHeader.peHeaderOffset();
        validateRange(baseOffset, PEHeaderInfo.SIZE);

        long peSignature = getUInt32(baseOffset + PEHeaderInfo.SIGNATURE_OFFSET);
        if (peSignature != PEHeaderInfo.PE_SIGNATURE) {
            throw new ConfigurationUsageException("Missing PE signature.");
        }

        long machine = getUInt16(baseOffset + PEHeaderInfo.MACHINE_OFFSET);
        if (machine != MACHINE_AMD64) {
            throw new ConfigurationUsageException("Only AMD64 PE is supported.");
        }
        long numberOfSections = getUInt16(baseOffset + PEHeaderInfo.NUMBER_OF_SECTIONS_OFFSET);
        long optionalHeaderOffset = baseOffset + PEHeaderInfo.SIZE;
        long optionalHeaderSize = getUInt16(baseOffset + PEHeaderInfo.OPTIONAL_HEADER_SIZE_OFFSET);
        return new PEHeaderInfo(machine, numberOfSections, optionalHeaderOffset, optionalHeaderSize);
    }

    private OptionalHeaderInfo parseOptionalHeader(PEHeaderInfo peHeader) {
        long baseOffset = peHeader.optionalHeaderOffset();
        int optionalMagic = getUInt16(baseOffset + OptionalHeaderInfo.MAGIC_OFFSET);
        if (optionalMagic != OptionalHeaderInfo.MAGIC) {
            throw new ConfigurationUsageException("Missing optional header.");
        }

        long dataDirOffset = baseOffset + OptionalHeaderInfo.DATA_DIRECTORIES_ARRAY_OFFSET;
        validateRange(dataDirOffset, DataDirectoryInfo.SIZE);
        long exportRVA = getUInt32(dataDirOffset + DataDirectoryInfo.VA_OFFSET);
        long exportSize = getUInt32(dataDirOffset + DataDirectoryInfo.SIZE_OFFSET);
        if (exportRVA == 0 || exportSize == 0) {
            throw new ConfigurationUsageException("No export table found.");
        }
        DataDirectoryInfo exportDataDirectory = new DataDirectoryInfo(exportRVA, exportSize);
        return new OptionalHeaderInfo(exportDataDirectory);
    }

    private SectionsInfo parseSections(DOSHeaderInfo dosHeader, PEHeaderInfo peHeader) {
        long sectionsOffset = peHeader.optionalHeaderOffset() + peHeader.optionalHeaderSize();
        assert sectionsOffset == dosHeader.peHeaderOffset() + PEHeaderInfo.SIZE + peHeader.optionalHeaderSize();
        List<SectionInfo> sections = new ArrayList<>();
        for (long i = 0; i < peHeader.numberOfSections(); i++) {
            long baseOffset = Math.addExact(sectionsOffset, i * SectionInfo.SIZE);
            validateRange(baseOffset, SectionInfo.SIZE);
            String name = getFixedLengthString(baseOffset + SectionInfo.NAME_OFFSET, SectionInfo.NAME_LENGTH);
            long virtualSize = getUInt32(baseOffset + SectionInfo.VIRTUAL_SIZE_OFFSET);
            long virtualAddress = getUInt32(baseOffset + SectionInfo.VIRTUAL_ADDRESS_OFFSET);
            long sizeOfRawData = getUInt32(baseOffset + SectionInfo.SIZE_OF_RAW_DATA_OFFSET);
            long pointerToRawData = getUInt32(baseOffset + SectionInfo.POINTER_TO_RAW_DATA_OFFSET);
            sections.add(new SectionInfo(name, virtualAddress, virtualSize, pointerToRawData, sizeOfRawData));
        }
        return new SectionsInfo(sections);
    }

    private SymbolAddresses findSymbolAddresses(String symbol, OptionalHeaderInfo optionalHeader, SectionsInfo sectionsInfo) {
        long exportOffset = rvaToOffset(optionalHeader.exportDataDirectory().virtualAddress(), sectionsInfo.sections());

        validateRange(exportOffset, ExportDirectoryInfo.SIZE);
        long numberOfFunctions = getUInt32(exportOffset + ExportDirectoryInfo.NUMBER_OF_FUNCTIONS_OFFSET);
        long numberOfNames = getUInt32(exportOffset + ExportDirectoryInfo.NUMBER_OF_NAMES_OFFSET);
        long addressOfFunctions = getUInt32(exportOffset + ExportDirectoryInfo.ADDRESS_OF_FUNCTIONS_OFFSET);
        long addressOfNames = getUInt32(exportOffset + ExportDirectoryInfo.ADDRESS_OF_NAMES_OFFSET);
        long addressOfNameOrdinals = getUInt32(exportOffset + ExportDirectoryInfo.ADDRESS_OF_NAME_ORDINALS_OFFSET);

        long functionsOffset = rvaToOffset(addressOfFunctions, sectionsInfo.sections());
        long namesOffset = rvaToOffset(addressOfNames, sectionsInfo.sections());
        long ordinalsOffset = rvaToOffset(addressOfNameOrdinals, sectionsInfo.sections());
        validateRange(namesOffset, numberOfNames * EXPORT_NAME_RVA_SIZE);
        validateRange(functionsOffset, numberOfFunctions * EXPORT_FUNCTION_RVA_SIZE);
        validateRange(ordinalsOffset, numberOfNames * EXPORT_ORDINAL_SIZE);

        String lengthSymbol = lengthSymbolName(symbol);
        long resourceRVA = -1;
        long lengthRVA = -1;
        for (long i = 0; i < numberOfNames; i++) {
            long nameRVAOffset = Math.addExact(namesOffset, i * EXPORT_NAME_RVA_SIZE);
            long nameRVA = getUInt32(nameRVAOffset);
            long nameOffset = rvaToOffset(nameRVA, sectionsInfo.sections());
            String name = getNullTerminatedString(nameOffset).trim();
            if (name.equals(symbol) || name.equals(lengthSymbol)) {
                long ordinalOffset = Math.addExact(ordinalsOffset, i * EXPORT_ORDINAL_SIZE);
                int ordinal = getUInt16(ordinalOffset);
                if (ordinal < 0 || ordinal >= numberOfFunctions) {
                    continue;
                }
                long funcRVAOffset = Math.addExact(functionsOffset, (long) ordinal * EXPORT_FUNCTION_RVA_SIZE);
                long funcRVA = getUInt32(funcRVAOffset);
                if (name.equals(symbol)) {
                    resourceRVA = funcRVA;
                } else {
                    lengthRVA = funcRVA;
                }
            }
        }

        if (resourceRVA == -1 || lengthRVA == -1) {
            throw symbolsNotFoundException(symbol, lengthSymbol);
        }

        return new SymbolAddresses(resourceRVA, lengthRVA);
    }

    private ResourceLocation computeResourceLocation(SectionsInfo sectionsInfo, SymbolAddresses symbolAddresses) {
        long resourceOffset = rvaToOffset(symbolAddresses.resourceVirtualAddress(), sectionsInfo.sections());
        long lengthOffset = rvaToOffset(symbolAddresses.lengthVirtualAddress(), sectionsInfo.sections());
        long length = getUInt64(lengthOffset);
        return new ResourceLocation(resourceOffset, length);
    }

    private long rvaToOffset(long rva, List<SectionInfo> sections) {
        for (SectionInfo sec : sections) {
            if (rva >= sec.virtualAddress() && rva < Math.addExact(sec.virtualAddress(), sec.virtualSize())) {
                long delta = rva - sec.virtualAddress();
                if (delta >= sec.sizeOfRawData()) {
                    throw new ConfigurationUsageException("Failed to convert RVA to file offset.");
                }
                long offset = Math.addExact(sec.pointerToRawData(), delta);
                validateRange(offset, 1);
                return offset;
            }
        }
        throw new ConfigurationUsageException("Failed to convert RVA to file offset.");
    }
}
