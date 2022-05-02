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
import java.util.List;

import com.oracle.truffle.llvm.parser.filereader.ObjectFileReader;

/**
 * A class for parsing the import table section.
 *
 * @see <a href=
 *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?#the-idata-section">The
 *      .idata Section</a>
 */
public final class ImageImportData {
    final List<ImageImportDescriptor> directoryTable;

    private ImageImportData(List<ImageImportDescriptor> directoryTable) {
        this.directoryTable = directoryTable;
    }

    static ImageImportData create(CoffFile file, ObjectFileReader reader) {
        List<ImageImportDescriptor> directoryTable = new ArrayList<>();
        while (true) {
            ImageImportDescriptor importDescriptor = ImageImportDescriptor.create(file, reader);
            if (importDescriptor == null) {
                break;
            }
            directoryTable.add(importDescriptor);
        }
        return new ImageImportData(directoryTable);
    }

    /**
     * Import Image Descriptor.
     *
     * <pre>
     * typedef struct _IMAGE_IMPORT_DESCRIPTOR {
     *     union {
     *         DWORD Characteristics;
     *         DWORD OriginalFirstThunk;
     *     } DUMMYUNIONNAME;
     *     DWORD TimeDateStamp;
     *     DWORD ForwarderChain;
     *     DWORD Name;
     *     DWORD FirstThunk;
     * } IMAGE_IMPORT_DESCRIPTOR;
     * </pre>
     *
     * @see <a href=
     *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?#import-directory-table">Import
     *      Directory Table</a>
     */
    public static final class ImageImportDescriptor {
        int importLookupTableRVA;
        int timeDateStamp;
        int forwarderChain;
        String name;
        int firstThunk;

        private static ImageImportDescriptor create(CoffFile file, ObjectFileReader reader) {
            ImageImportDescriptor table = new ImageImportDescriptor();
            table.importLookupTableRVA = reader.getInt();
            table.timeDateStamp = reader.getInt();
            table.forwarderChain = reader.getInt();
            int nameOffset = reader.getInt();
            table.firstThunk = reader.getInt();

            // the last entry is a null entry
            if (table.importLookupTableRVA == 0 && table.timeDateStamp == 0 && table.forwarderChain == 0 && nameOffset == 0 && table.firstThunk == 0) {
                return null;
            }

            // read the name from the virtual address
            table.name = file.readStringAtVirtualOffset(nameOffset);

            return table;
        }
    }
}
