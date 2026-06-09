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
 * Resource locator for Mach-O 64-bit images.
 */
final class MachOResourceLocator extends ResourceLocator {
    private static final int MAGIC = 0xFEEDFACF;
    private static final int MAGIC_SIZE = 4;

    private record HeaderInfo(long numberOfCommands, long sizeOfCommands) {
        static final int SIZE = 32;

        static final int NCMDS_OFFSET = 0x10;
        static final int SIZEOFCMDS_OFFSET = 0x14;
    }

    private record LoadCommand(long kind, long size) {
        static final int SIZE = 8;
        static final long SYMTAB_KIND = 0x2;
        static final long SEGMENT_64_KIND = 0x19;

        static final int KIND_OFFSET = 0x0;
        static final int SIZE_OFFSET = 0x4;
    }

    private record Segment(long virtualMemoryAddress, long fileOffset) {
        static final int SIZE = 72;
        static final String DATA_SEGMENT = "__DATA";

        static final int SEGNAME_OFFSET = 0x8;
        static final int VMADDR_OFFSET = 0x18;
        static final int FILEOFF_OFFSET = 0x28;
        static final int SEGNAME_SIZE = VMADDR_OFFSET - SEGNAME_OFFSET;
    }

    private record Symtab(long symbolTableOffset, long numberOfSymbols, long stringTableOffset, long stringTableSize) {
        private static final int NLIST_64_SIZE = 16;

        static final int SYMOFF_OFFSET = 0x8;
        static final int NSYMS_OFFSET = 0xC;
        static final int STROFF_OFFSET = 0x10;
        static final int STRSIZE_OFFSET = 0x14;

        static final int SYMBOL_STRX_OFFSET = 0x0;
        static final int SYMBOL_VALUE_OFFSET = 0x8;
    }

    private record LoadCommandsInfo(Segment segment, Symtab symtab) {
    }

    MachOResourceLocator(MemorySegment segment) {
        super(segment);
    }

    @Override
    String getSupportedFileFormat() {
        return "Mach-O 64-bit";
    }

    @Override
    ByteOrder getByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    boolean matchesMagic() {
        validateRange(0, MAGIC_SIZE);
        return getUInt32(0) == Integer.toUnsignedLong(MAGIC);
    }

    /**
     * Parses the Mach-O file and returns the resource offset and length for the given symbol.
     *
     * The symbol is automatically prefixed with "_" when searching. For example, given "sbom" we
     * search for "_sbom" since Mach-O files export symbols with an underscore as prefix.
     *
     * @param symbol the name of the symbol to locate (without underscore).
     * @return a ResourceLocation with offset and length.
     * @throws ConfigurationUsageException on parsing errors with messages matching the C spec.
     */
    @Override
    protected ResourceLocation locateResourceImpl(String symbol) {
        HeaderInfo header = parseHeader();
        LoadCommandsInfo loadCommandsInfo = parseLoadCommands(header);
        SymbolAddresses symbolAddresses = findSymbolAddresses(symbol, loadCommandsInfo);
        return computeResourceLocation(loadCommandsInfo, symbolAddresses);
    }

    private HeaderInfo parseHeader() {
        validateRange(0, HeaderInfo.SIZE);
        long numberOfCommands = getUInt32(HeaderInfo.NCMDS_OFFSET);
        long sizeOfCommands = getUInt32(HeaderInfo.SIZEOFCMDS_OFFSET);
        validateRange(HeaderInfo.SIZE, sizeOfCommands);
        return new HeaderInfo(numberOfCommands, sizeOfCommands);
    }

    private LoadCommandsInfo parseLoadCommands(HeaderInfo headerInfo) {
        Segment dataSegment = null;
        Symtab symtab = null;

        long currentOffset = HeaderInfo.SIZE;
        for (long commandIndex = 0; commandIndex < headerInfo.numberOfCommands(); commandIndex++) {
            LoadCommand loadCommand = parseLoadCommand(currentOffset);
            if (loadCommand.kind() == LoadCommand.SEGMENT_64_KIND && dataSegment == null) {
                Segment seg = parseSegmentCommand(currentOffset);
                String segmentName = getNullTerminatedString(currentOffset + Segment.SEGNAME_OFFSET, Segment.SEGNAME_SIZE).trim();
                if (segmentName.equals(Segment.DATA_SEGMENT)) {
                    dataSegment = seg;
                }
            } else if (loadCommand.kind() == LoadCommand.SYMTAB_KIND && symtab == null) {
                symtab = parseSymtabCommand(currentOffset);
            }
            currentOffset = Math.addExact(currentOffset, loadCommand.size());
        }

        if (dataSegment == null) {
            throw new ConfigurationUsageException("Unable to detect %s segment.".formatted(Segment.DATA_SEGMENT));
        }
        if (symtab == null) {
            throw new ConfigurationUsageException("Could not find LC_SYMTAB.");
        }

        return new LoadCommandsInfo(dataSegment, symtab);
    }

    private Segment parseSegmentCommand(long offset) {
        validateRange(offset, Segment.SIZE);
        long virtualMemoryAddress = getUInt64(offset + Segment.VMADDR_OFFSET);
        long fileOffset = getUInt64(offset + Segment.FILEOFF_OFFSET);
        return new Segment(virtualMemoryAddress, fileOffset);
    }

    private Symtab parseSymtabCommand(long offset) {
        long symbolTableOffset = getUInt32(offset + Symtab.SYMOFF_OFFSET);
        long numberOfSymbols = getUInt32(offset + Symtab.NSYMS_OFFSET);
        long stringTableOffset = getUInt32(offset + Symtab.STROFF_OFFSET);
        long stringTableSize = getUInt32(offset + Symtab.STRSIZE_OFFSET);

        long symtabEnd = Math.addExact(symbolTableOffset, Math.multiplyExact(numberOfSymbols, Symtab.NLIST_64_SIZE));
        validateRange(symbolTableOffset, symtabEnd - symbolTableOffset);
        validateRange(stringTableOffset, stringTableSize);

        return new Symtab(symbolTableOffset, numberOfSymbols, stringTableOffset, stringTableSize);
    }

    private LoadCommand parseLoadCommand(long offset) {
        validateRange(offset, LoadCommand.SIZE);
        long kind = getUInt32(offset + LoadCommand.KIND_OFFSET);
        long size = getUInt32(offset + LoadCommand.SIZE_OFFSET);
        validateRange(offset, size);
        return new LoadCommand(kind, size);
    }

    private SymbolAddresses findSymbolAddresses(String symbol, LoadCommandsInfo loadCommandsInfo) {
        Symtab symtab = loadCommandsInfo.symtab();
        Segment seg = loadCommandsInfo.segment();

        String dataSymbol = "_" + symbol;
        String lengthSymbol = lengthSymbolName(dataSymbol);
        long resourceVirtualMemoryAddress = -1;
        long lengthVirtualMemoryAddress = -1;

        for (long i = 0; i < symtab.numberOfSymbols(); i++) {
            long entryOffset = Math.addExact(symtab.symbolTableOffset(), Math.multiplyExact(i, Symtab.NLIST_64_SIZE));
            validateRange(entryOffset, Symtab.NLIST_64_SIZE);

            long nStrx = getUInt32(entryOffset + Symtab.SYMBOL_STRX_OFFSET);
            if (nStrx >= symtab.stringTableSize()) {
                throw new ConfigurationUsageException("Invalid offset or address.");
            }

            long nameOffset = Math.addExact(symtab.stringTableOffset(), nStrx);
            String name = getNullTerminatedString(nameOffset, symtab.stringTableSize() - nStrx);
            long nValue = getUInt64(entryOffset + Symtab.SYMBOL_VALUE_OFFSET);

            if (dataSymbol.equals(name)) {
                resourceVirtualMemoryAddress = nValue;
            } else if (lengthSymbol.equals(name)) {
                lengthVirtualMemoryAddress = nValue;
            }
        }

        if (resourceVirtualMemoryAddress == -1 || lengthVirtualMemoryAddress == -1) {
            throw symbolsNotFoundException(dataSymbol, lengthSymbol);
        }

        if (resourceVirtualMemoryAddress < seg.virtualMemoryAddress() || lengthVirtualMemoryAddress < seg.virtualMemoryAddress()) {
            throw new ConfigurationUsageException("Invalid offset or address.");
        }

        return new SymbolAddresses(resourceVirtualMemoryAddress, lengthVirtualMemoryAddress);
    }

    private ResourceLocation computeResourceLocation(LoadCommandsInfo loadCommandsInfo, SymbolAddresses symbolAddresses) {
        Segment segmentInfo = loadCommandsInfo.segment();
        long resourceOffset = Math.addExact(segmentInfo.fileOffset(), symbolAddresses.resourceVirtualAddress() - segmentInfo.virtualMemoryAddress());
        long lengthOffset = Math.addExact(segmentInfo.fileOffset(), symbolAddresses.lengthVirtualAddress() - segmentInfo.virtualMemoryAddress());
        long length = getUInt64(lengthOffset);
        return new ResourceLocation(resourceOffset, length);
    }
}
