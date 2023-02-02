/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.coff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.llvm.parser.coff.PEFile.ImageDataDirectorySection;
import com.oracle.truffle.llvm.parser.filereader.ObjectFileReader;

import org.graalvm.collections.Pair;

/**
 * A class for parsing the export table section.
 *
 * @see <a href=
 *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?#the-edata-section-image-only">The
 *      .edata Section</a>
 */
public final class ExportTable {
    private final List<Pair<String, Export>> exports;
    public static final ExportTable EMPTY = new ExportTable(Collections.emptyList());

    private ExportTable(List<Pair<String, Export>> exports) {
        this.exports = exports;
    }

    public Iterable<Pair<String, Export>> getExports() {
        return exports;
    }

    static ExportTable create(CoffFile file, ImageDataDirectorySection section) {
        ObjectFileReader reader = section.getReader(file);

        ExportDirectoryTable exportTable = ExportDirectoryTable.read(file, reader);

        List<Pair<String, Export>> exports = new ArrayList<>(exportTable.numberOfNamePointers);

        int[] exportAddressEntries = readIntArray(exportTable.exportAddressTableRVA, exportTable.addressTableEntries, file);
        int[] nameRVAs = readIntArray(exportTable.namePointerRVA, exportTable.numberOfNamePointers, file);
        short[] ordinalNumbers = readShortArray(exportTable.ordinalTableRVA, exportTable.numberOfNamePointers, file);

        for (int i = 0; i < exportTable.numberOfNamePointers; i++) {
            String name = file.readStringAtVirtualOffset(nameRVAs[i]);
            int exportAddress = exportAddressEntries[ordinalNumbers[i]];

            // if the export address is within the export data directory section bounds, it is a
            // name pointer
            if (section.isRVAInBounds(exportAddress)) {
                String exportName = file.readStringAtVirtualOffset(exportAddress);
                exports.add(Pair.create(name, new ExportName(exportName)));
            } else {
                // otherwise it is an RVA to the export function
                exports.add(Pair.create(name, new ExportRVA(exportAddress)));
            }
        }

        return new ExportTable(exports);
    }

    private static int[] readIntArray(int position, int count, CoffFile file) {
        ObjectFileReader reader = file.getReaderAtVirtualAddress(position);
        int[] res = new int[count];
        for (int i = 0; i < count; i++) {
            res[i] = reader.getInt();
        }
        return res;
    }

    private static short[] readShortArray(int position, int count, CoffFile file) {
        ObjectFileReader reader = file.getReaderAtVirtualAddress(position);
        short[] res = new short[count];
        for (int i = 0; i < count; i++) {
            res[i] = reader.getShort();
        }
        return res;
    }

    public abstract static class Export {
    }

    public static final class ExportName extends Export {
        private final String name;

        private ExportName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final class ExportRVA extends Export {
        private final int rva;

        private ExportRVA(int rva) {
            this.rva = rva;
        }

        public int getRVA() {
            return rva;
        }
    }

    /**
     * Export Directory Table.
     *
     * <pre>
     * typedef struct _IMAGE_EXPORT_DIRECTORY {
     *     DWORD Characteristics;
     *     DWORD TimeDateStamp;
     *     WORD MajorVersion;
     *     WORD MinorVersion;
     *     DWORD Name;
     *     DWORD Base;
     *     DWORD NumberOfFunctions;
     *     DWORD NumberOfNames;
     *     DWORD AddressOfFunctions;
     *     DWORD AddressOfNames;
     *     DWORD AddressOfNameOrdinals;
     * } IMAGE_EXPORT_DIRECTORY;
     * </pre>
     *
     * @see <a href=
     *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?redirectedfrom=MSDN#export-directory-table">Export
     *      Directory Table</a>
     */
    public static final class ExportDirectoryTable {
        int exportFlags;
        int timeDateStamp;
        short majorVersion;
        short minorVersion;
        String name;
        int ordinalBase;
        int addressTableEntries;
        int numberOfNamePointers;
        int exportAddressTableRVA;
        int namePointerRVA;
        int ordinalTableRVA;

        private static ExportDirectoryTable read(CoffFile file, ObjectFileReader reader) {
            int start = reader.getPosition();

            ExportDirectoryTable table = new ExportDirectoryTable();
            table.exportFlags = reader.getInt();
            table.timeDateStamp = reader.getInt();
            table.majorVersion = reader.getShort();
            table.minorVersion = reader.getShort();
            int nameOffset = reader.getInt();
            table.ordinalBase = reader.getInt();
            table.addressTableEntries = reader.getInt();
            table.numberOfNamePointers = reader.getInt();
            table.exportAddressTableRVA = reader.getInt();
            table.namePointerRVA = reader.getInt();
            table.ordinalTableRVA = reader.getInt();

            assert reader.getPosition() - start == 40;

            table.name = file.readStringAtVirtualOffset(nameOffset);

            return table;
        }
    }
}
