/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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

import org.graalvm.polyglot.io.ByteSequence;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.coff.CoffFile.ImageDataDirectory;
import com.oracle.truffle.llvm.parser.coff.CoffFile.ImageOptionHeader;
import com.oracle.truffle.llvm.parser.coff.CoffFile.ImageOptionNT64Header;
import com.oracle.truffle.llvm.parser.coff.CoffFile.ImageSectionHeader;
import com.oracle.truffle.llvm.parser.coff.CoffFile.ImageOptionNT64Header.ImageDataIndex;
import com.oracle.truffle.llvm.parser.coff.ImageImportData.ImageImportDescriptor;
import com.oracle.truffle.llvm.parser.filereader.ObjectFileReader;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;

/**
 * Windows Portable Executable File.
 *
 * A PE file starts with a MS DOS header which includes a pointer the PE signature. The PE signature
 * word is followed by a {@link CoffFile COFF header}. Note that absolute offsets in the embedded
 * COFF file are relative to the whole file, not to the COFF header!
 *
 * @see <a href=
 *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?redirectedfrom=MSDN#overview">PE
 *      Format</a>
 */
public final class PEFile {
    private static final short IMAGE_DOS_SIGNATURE = (short) 0x5A4D; // MZ
    private static final int IMAGE_NT_SIGNATURE = 0x00004550;  // PE00
    private static final int OFFSET_TO_PE_SIGNATURE = 0x3c;

    private final CoffFile coffFile;
    private ImageImportData importData = null;
    private ExportTable exportTable = null;

    private PEFile(CoffFile coffFile) {
        this.coffFile = coffFile;
        loadImportData();
        loadExportData();
    }

    public CoffFile getCoffFile() {
        return coffFile;
    }

    public static PEFile create(Source source, ByteSequence bytes) {
        ObjectFileReader reader = new ObjectFileReader(bytes, true);
        short machine = reader.getShort();
        if (machine != IMAGE_DOS_SIGNATURE) {
            throw new LLVMParserException("Invalid MS DOS file!");
        }
        reader.setPosition(OFFSET_TO_PE_SIGNATURE);
        int peOffset = reader.getInt();
        reader.setPosition(peOffset);
        int reSignature = reader.getInt();
        if (reSignature != IMAGE_NT_SIGNATURE) {
            throw new LLVMParserException("No PE Signature found in MS DOS Executable!");
        }
        return new PEFile(CoffFile.create(source, bytes, reader));
    }

    public ImageOptionNT64Header getOptionHeader() {
        ImageOptionHeader header = coffFile.getOptionHeader();
        if (header == null) {
            return null;
        }
        if (header instanceof ImageOptionNT64Header) {
            return (ImageOptionNT64Header) header;
        }
        throw new LLVMParserException(String.format("Unsupported PE File Coff option header: %s.", header.getClass().toString()));
    }

    public static final class ImageDataDirectorySection {
        final ImageSectionHeader header;
        final int offset;
        final int length;

        private ImageDataDirectorySection(ImageSectionHeader header) {
            this(header, 0, header.virtualSize);
        }

        private ImageDataDirectorySection(ImageSectionHeader header, int offset, int length) {
            this.header = header;
            this.offset = offset;
            this.length = length;
        }

        /**
         * Returns true if the relative virtual address is within the bounds of this image data
         * directory.
         */
        public boolean isRVAInBounds(int rva) {
            return rva >= header.virtualAddress + offset && rva <= header.virtualAddress + offset + length;
        }

        public ObjectFileReader getReader(CoffFile file) {
            ObjectFileReader reader = file.getSectionReader(header);
            reader.setPosition(offset);
            return reader;
        }
    }

    private ImageDataDirectorySection getImageDataDirectory(ImageDataIndex optionHeaderIndex, String sectionName) {
        // First see if there is an import data directory
        ImageOptionNT64Header header = getOptionHeader();
        if (header != null) {
            ImageDataDirectory importDirectory = header.getDirectory(optionHeaderIndex);
            if (importDirectory.getSize() == 0) {
                // The import directory is empty
                return null;
            }

            if (importDirectory != null) {
                ImageSectionHeader sectionHeader = coffFile.lookupOffset(importDirectory.getVirtualAddress());
                return new ImageDataDirectorySection(sectionHeader, importDirectory.getVirtualAddress() - sectionHeader.virtualAddress, importDirectory.getSize());
            }
        }

        // Otherwise try to load it from the .idata section
        ImageSectionHeader imageSection = coffFile.getSection(sectionName);
        if (imageSection != null) {
            return new ImageDataDirectorySection(imageSection);
        }

        return null;
    }

    private ObjectFileReader getImageData(ImageDataIndex optionHeaderIndex, String sectionName) {
        ImageDataDirectorySection dataDirectorySection = getImageDataDirectory(optionHeaderIndex, sectionName);
        if (dataDirectorySection != null) {
            return dataDirectorySection.getReader(coffFile);
        }

        return null;
    }

    private ObjectFileReader getImportDataReader() {
        return getImageData(ImageDataIndex.ImportTable, ".idata");
    }

    private void loadImportData() {
        ObjectFileReader reader = getImportDataReader();
        importData = reader == null ? null : ImageImportData.create(coffFile, reader);
    }

    private void loadExportData() {
        ImageDataDirectorySection section = getImageDataDirectory(ImageDataIndex.ExportTable, ".edata");
        exportTable = section == null ? ExportTable.EMPTY : ExportTable.create(coffFile, section);
    }

    public List<String> getImportLibraries() {
        List<String> libraries = new ArrayList<>();
        if (importData == null) {
            return libraries;
        }

        for (ImageImportDescriptor importDescriptor : importData.directoryTable) {
            libraries.add(importDescriptor.name);
        }
        return libraries;
    }

    public ExportTable getExportTable() {
        return exportTable;
    }

}
