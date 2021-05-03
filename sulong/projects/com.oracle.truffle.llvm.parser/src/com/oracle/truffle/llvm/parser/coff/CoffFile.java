/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;

import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.truffle.llvm.parser.filereader.ObjectFileReader;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;

/**
 * @see <a href=
 *      "https://download.microsoft.com/download/9/C/5/9C5B2167-8017-4BAE-9FDE-D599BAC8184A/pecoff.docx">Microsoft
 *      Portable Executable and Common Object File Format Specification Revision 11 - June 20, 2017
 *      </a>
 * @see <a href=
 *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?redirectedfrom=MSDN#overview">PE
 *      Format 11/08/2020 (web version of the above, poorly formatted)</a>
 * @see <a href=
 *      "https://github.com/MicrosoftDocs/win32/blob/docs/desktop-src/Debug/pe-format.md">Github
 *      source of the above</a>
 * @see <a href="https://docs.microsoft.com/en-us/previous-versions/ms809762(v=msdn.10)">Peering
 *      Inside the PE: A Tour of the Win32 Portable Executable File Format</a>
 * @see <a href=
 *      "https://msdn.microsoft.com/en-us/library/windows/desktop/ms680313(v=vs.85).aspx">IMAGE_FILE_HEADER
 *      structure (winnt.h)</a>
 */
public final class CoffFile {
    private final ByteSequence bytes;
    private final ImageFileHeader header;
    private final ImageSectionHeader[] sections;

    private CoffFile(ByteSequence bytes, ImageFileHeader header) {
        this.bytes = bytes;
        this.header = header;
        this.sections = new ImageSectionHeader[header.numberOfSections];
    }

    public ImageSectionHeader getSection(String sectionName) {
        for (ImageSectionHeader section : sections) {
            if (section.getName().equals(sectionName)) {
                return section;
            }
        }
        return null;
    }

    private void initializeSections(ObjectFileReader reader) {
        reader.setPosition(header.firstSection);
        for (int i = 0; i < header.numberOfSections; i++) {
            sections[i] = createImageSectionHeader(reader);
        }
    }

    public static CoffFile create(ByteSequence bytes) {
        return create(bytes, new ObjectFileReader(bytes, true));
    }

    static CoffFile create(ByteSequence bytes, ObjectFileReader reader) {
        ImageFileHeader header = ImageFileHeader.createImageFileHeader(reader);
        CoffFile coffFile = new CoffFile(bytes, header);
        coffFile.initializeSections(reader);
        return coffFile;
    }

    /**
     * Image File Header.
     *
     * <pre>
     * typedef struct _IMAGE_FILE_HEADER {
     *     WORD    Machine;
     *     WORD    NumberOfSections;
     *     DWORD   TimeDateStamp;
     *     DWORD   PointerToSymbolTable;
     *     DWORD   NumberOfSymbols;
     *     WORD    SizeOfOptionalHeader;
     *     WORD    Characteristics;
     * } IMAGE_FILE_HEADER, *PIMAGE_FILE_HEADER;
     * </pre>
     * 
     * @see <a href=
     *      "https://msdn.microsoft.com/en-us/library/windows/desktop/ms680313(v=vs.85).aspx">IMAGE_FILE_HEADER
     *      structure (winnt.h)</a>
     */
    public static final class ImageFileHeader {
        static final short IMAGE_FILE_MACHINE_AMD64 = (short) 0x8664;
        static final int NUMBER_OF_SYMBOLS = 8;
        static final int SIZE_OF_OPTIONAL_HEADER_OFFSET = 16;
        static final int IMAGE_SIZEOF_FILE_HEADER = 20;
        /**
         * @see <a href=
         *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?redirectedfrom=MSDN#coff-symbol-table">COFF
         *      Symbol Table</a>
         */
        static final int SIZE_OF_SYMBOL_TABLE_RECORD = 18;

        private static ImageFileHeader createImageFileHeader(ObjectFileReader reader) {
            int headerStartOffset = reader.getPosition();
            short machine = reader.getShort();
            checkIdent(machine);
            short numberOfSections = reader.getShort();
            /* int dateTimeStamp = */ reader.getInt();
            int pointerToSymbolTable = reader.getInt();
            int numberOfSymbols = reader.getInt();
            short sizeOfOptionalHeader = reader.getShort();
            int firstSection = headerStartOffset + ImageFileHeader.IMAGE_SIZEOF_FILE_HEADER + Short.toUnsignedInt(sizeOfOptionalHeader);
            int stringTablePosition = getStringTablePosition(pointerToSymbolTable, numberOfSymbols);
            return new ImageFileHeader(numberOfSections, firstSection, stringTablePosition);
        }

        /**
         * <quote>Immediately following the COFF symbol table is the COFF string table. The position
         * of this table is found by taking the symbol table address in the COFF header and adding
         * the number of symbols multiplied by the size of a symbol.</quote>
         * 
         * @see <a href=
         *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?#coff-string-table">COFF
         *      String Table</a>
         */
        private static int getStringTablePosition(int pointerToSymbolTable, int numberOfSymbols) {
            return pointerToSymbolTable + numberOfSymbols * SIZE_OF_SYMBOL_TABLE_RECORD;
        }

        private static void checkIdent(short magic) {
            if (magic != ImageFileHeader.IMAGE_FILE_MACHINE_AMD64) {
                throw new LLVMParserException("Invalid COFF file!");
            }
        }

        private final short numberOfSections;
        private final int firstSection;
        private final int stringTablePosition;

        ImageFileHeader(short numberOfSections, int firstSection, int stringTablePosition) {
            this.numberOfSections = numberOfSections;
            this.firstSection = firstSection;
            this.stringTablePosition = stringTablePosition;
        }

        @Override
        public String toString() {
            return "ImageFileHeader[" +
                            "numberOfSections=" + numberOfSections +
                            ']';
        }
    }

    /**
     * Image Section Header.
     *
     * <pre>
     * typedef struct _IMAGE_SECTION_HEADER {
     *     BYTE    Name[IMAGE_SIZEOF_SHORT_NAME];
     *     union {
     *             DWORD   PhysicalAddress;
     *             DWORD   VirtualSize;
     *     } Misc;
     *     DWORD   VirtualAddress;
     *     DWORD   SizeOfRawData;
     *     DWORD   PointerToRawData;
     *     DWORD   PointerToRelocations;
     *     DWORD   PointerToLinenumbers;
     *     WORD    NumberOfRelocations;
     *     WORD    NumberOfLinenumbers;
     *     DWORD   Characteristics;
     * } IMAGE_SECTION_HEADER, *PIMAGE_SECTION_HEADER;
     * </pre>
     * 
     * @see <a href=
     *      "https://docs.microsoft.com/en-gb/windows/win32/api/winnt/ns-winnt-image_section_header">IMAGE_SECTION_HEADER
     *      structure (winnt.h)</a>
     */
    public final class ImageSectionHeader {
        private static final int IMAGE_SIZEOF_SHORT_NAME = 8;
        @SuppressWarnings("unused") private static final int VIRTUAL_ADDRESS_OFFSET = 12;
        private static final int SIZE_OF_RAW_DATA_OFFSET = 16;
        @SuppressWarnings("unused") private static final int POINTER_TO_RAW_DATA_OFFSET = 20;
        private static final int IMAGE_SIZEOF_SECTION_HEADER = 40;

        private final int startOffset;
        private final String name;
        private final int sizeOfRawData;
        private final int pointerToRawData;

        private ImageSectionHeader(int startOffset, String name, int sizeOfRawData, int pointerToRawData) {
            this.startOffset = startOffset;
            this.name = name;
            this.sizeOfRawData = sizeOfRawData;
            this.pointerToRawData = pointerToRawData;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "ImageSectionHeader[" +
                            name +
                            ", startOffset=" + startOffset +
                            ", sizeOfRawData=" + sizeOfRawData +
                            ", pointerToRawData=" + pointerToRawData +
                            ']';
        }

        public ByteSequence getData() {
            return bytes.subSequence(pointerToRawData, pointerToRawData + sizeOfRawData);
        }

    }

    private ImageSectionHeader createImageSectionHeader(ObjectFileReader reader) {
        int startOffset = reader.getPosition();
        byte[] nameBytes = new byte[ImageSectionHeader.IMAGE_SIZEOF_SHORT_NAME];
        reader.get(nameBytes);
        String name = parseName(nameBytes, reader);
        reader.setPosition(startOffset + ImageSectionHeader.SIZE_OF_RAW_DATA_OFFSET);
        int sizeOfRawData = reader.getInt();
        int pointerToRawData = reader.getInt();
        reader.setPosition(startOffset + ImageSectionHeader.IMAGE_SIZEOF_SECTION_HEADER);
        return new ImageSectionHeader(startOffset, name, sizeOfRawData, pointerToRawData);
    }

    /**
     * Parse section name.
     * 
     * An 8-byte, null-padded UTF-8 string. There is no terminating null character if the string is
     * exactly eight characters long. For longer names, this member contains a forward slash (/)
     * followed by an ASCII representation of a decimal number that is an offset into the string
     * table. Executable images do not use a string table and do not support section names longer
     * than eight characters.
     * 
     * @see ImageSectionHeader
     */
    private String parseName(byte[] nameBytes, ObjectFileReader reader) {
        String s = nameBytesToString(nameBytes);
        if (s.startsWith("/")) {
            int stringTableOffset = Integer.parseInt(s.substring(1));
            return readStringTableOffset(stringTableOffset, reader);
        }
        return s;
    }

    /**
     * @see <a href=
     *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?#coff-string-table">COFF
     *      String Table</a>
     */
    private String readStringTableOffset(int offset, ObjectFileReader reader) {
        int start = header.stringTablePosition + offset;
        reader.setPosition(start);
        for (byte b = reader.getByte(); b != 0; b = reader.getByte()) {
            // increment position
        }
        int end = reader.getPosition();
        return reader.getString(start, end, StandardCharsets.UTF_8);
    }

    private static String nameBytesToString(byte[] nameBytes) {
        // strip trailing '\0's
        int length = 0;
        while (length < nameBytes.length && nameBytes[length] != 0x0) {
            length++;
        }
        return new String(nameBytes, 0, length, StandardCharsets.UTF_8);
    }

}
