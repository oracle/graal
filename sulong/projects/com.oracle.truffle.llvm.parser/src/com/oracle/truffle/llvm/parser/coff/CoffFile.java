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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.filereader.ObjectFileReader;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;

import org.graalvm.polyglot.io.ByteSequence;

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
    private final Source source;
    private final ByteSequence bytes;
    private final ImageFileHeader header;
    private final ImageOptionHeader optionHeader;
    private final ImageSectionHeader[] sections;
    private final List<ImageSymbol> symbols;

    private CoffFile(Source source, ByteSequence bytes, ImageFileHeader header, ImageOptionHeader optionHeader) {
        this.source = source;
        this.bytes = bytes;
        this.header = header;
        this.optionHeader = optionHeader;
        this.sections = new ImageSectionHeader[header.numberOfSections];
        this.symbols = new ArrayList<>();
    }

    /**
     * Get the section using a one based index number.
     */
    public ImageSectionHeader getSectionByNumber(int number) {
        assert number > 0;
        return sections[number - 1];
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

    private void initializeSymbols(ObjectFileReader reader) {
        reader.setPosition(header.symbolTablePosition);
        int i = 0;
        while (i < header.numberOfSymbols) {
            ImageSymbol symbol = ImageSymbol.read(this, reader);
            symbols.add(symbol);

            // skip the symbol as well as any auxiliary symbols
            i += 1 + symbol.numberOfAuxSymbols;
        }
    }

    public Iterable<ImageSymbol> getSymbols() {
        return symbols;
    }

    ImageSectionHeader lookupOffset(int offset) {
        for (int i = 0; i < sections.length; i++) {
            int virtualOffset = offset - sections[i].virtualAddress;
            if (virtualOffset >= 0 && virtualOffset <= sections[i].sizeOfRawData) {
                return sections[i];
            }
        }
        throw new LLVMParserException(String.format("Invalid virtual address %d.", offset));
    }

    public ImageOptionHeader getOptionHeader() {
        return optionHeader;
    }

    public ObjectFileReader getSectionReader(ImageSectionHeader section) {
        ByteSequence sectionBytes = bytes.subSequence(section.pointerToRawData, section.pointerToRawData + section.sizeOfRawData);
        ObjectFileReader reader = new ObjectFileReader(sectionBytes, true);
        return reader;
    }

    public ObjectFileReader getReaderAtVirtualAddress(int virtualAddress) {
        ImageSectionHeader section = lookupOffset(virtualAddress);
        ObjectFileReader reader = getSectionReader(section);
        reader.setPosition(virtualAddress - section.virtualAddress);
        return reader;
    }

    public static CoffFile create(Source source, ByteSequence bytes) {
        return create(source, bytes, new ObjectFileReader(bytes, true));
    }

    static CoffFile create(Source source, ByteSequence bytes, ObjectFileReader reader) {
        ImageFileHeader header = ImageFileHeader.createImageFileHeader(reader);
        ImageOptionHeader optionHeader = header.sizeOfOptionalHeader > 0 ? ImageOptionHeader.create(reader) : null;
        CoffFile coffFile = new CoffFile(source, bytes, header, optionHeader);
        coffFile.initializeSections(reader);
        coffFile.initializeSymbols(reader);
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
            int symbolTablePosition = reader.getInt();
            int numberOfSymbols = reader.getInt();
            short sizeOfOptionalHeader = reader.getShort();
            /* short characteristics */ reader.getShort();
            int firstSection = headerStartOffset + ImageFileHeader.IMAGE_SIZEOF_FILE_HEADER + Short.toUnsignedInt(sizeOfOptionalHeader);
            int stringTablePosition = getStringTablePosition(symbolTablePosition, numberOfSymbols);
            return new ImageFileHeader(numberOfSections, firstSection, symbolTablePosition, numberOfSymbols, stringTablePosition, sizeOfOptionalHeader);
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
                // TODO: This should probably be more specific (e.g. with 32 bit files)
                throw new LLVMParserException("Invalid COFF file!");
            }
        }

        private final short numberOfSections;
        private final int firstSection;
        private final int symbolTablePosition;
        private final int numberOfSymbols;
        private final int stringTablePosition;
        private final short sizeOfOptionalHeader;

        ImageFileHeader(short numberOfSections, int firstSection, int symbolTablePosition, int numberOfSymbols, int stringTablePosition, short sizeOfOptionalHeader) {
            this.numberOfSections = numberOfSections;
            this.firstSection = firstSection;
            this.symbolTablePosition = symbolTablePosition;
            this.numberOfSymbols = numberOfSymbols;
            this.stringTablePosition = stringTablePosition;
            this.sizeOfOptionalHeader = sizeOfOptionalHeader;
        }

        @Override
        public String toString() {
            return "ImageFileHeader[" +
                            "numberOfSections=" + numberOfSections +
                            ']';
        }
    }

    /**
     * Image Data Directory.
     *
     * <pre>
     * typedef struct _IMAGE_DATA_DIRECTORY {
     *    DWORD   VirtualAddress;
     *    DWORD   Size;
     * } IMAGE_DATA_DIRECTORY, *PIMAGE_DATA_DIRECTORY;
     * </pre>
     */
    public static final class ImageDataDirectory {
        private static ImageDataDirectory createImageDataDirectory(ObjectFileReader reader) {
            int virtualAddress = reader.getInt();
            int size = reader.getInt();
            return new ImageDataDirectory(virtualAddress, size);
        }

        private final int virtualAddress;
        private final int size;

        public ImageDataDirectory(int virtualAddress, int size) {
            this.virtualAddress = virtualAddress;
            this.size = size;
        }

        public int getVirtualAddress() {
            return virtualAddress;
        }

        public int getSize() {
            return size;
        }
    }

    /**
     * Standard Image Optional Header.
     *
     * @see ImageOptionNT64Header
     */
    public abstract static class ImageOptionHeader {
        byte majorLinkerVersion;
        byte minorLinkerVersion;
        int sizeOfCode;
        int sizeOfInitializedData;
        int sizeOfUninitializedData;
        int addressOfEntryPoint;
        int baseOfCode;

        private static ImageOptionHeader create(ObjectFileReader reader) {
            short magic = reader.getShort();
            ImageOptionHeader header;
            switch (magic) {
                case ImageOptionNT64Header.IMAGE_NT_OPTIONAL_HDR64_MAGIC:
                    header = new ImageOptionNT64Header();
                    break;
                default:
                    throw new LLVMParserException(String.format("Unsupported coff optional header magic number 0x%x.", magic));
            }

            header.readStandard(reader);
            header.readExtended(reader);

            return header;
        }

        protected void readStandard(ObjectFileReader reader) {
            this.majorLinkerVersion = reader.getByte();
            this.minorLinkerVersion = reader.getByte();
            this.sizeOfCode = reader.getInt();
            this.sizeOfInitializedData = reader.getInt();
            this.sizeOfUninitializedData = reader.getInt();
            this.addressOfEntryPoint = reader.getInt();
            this.baseOfCode = reader.getInt();
        }

        protected abstract void readExtended(ObjectFileReader reader);

        private ImageOptionHeader() {
        }
    }

    /**
     * Image Optional Header.
     *
     * <pre>
     * typedef struct _IMAGE_OPTIONAL_HEADER64 {
     * // Standard fields
     *     WORD        Magic;
     *     BYTE        MajorLinkerVersion;
     *     BYTE        MinorLinkerVersion;
     *     DWORD       SizeOfCode;
     *     DWORD       SizeOfInitializedData;
     *     DWORD       SizeOfUninitializedData;
     *     DWORD       AddressOfEntryPoint;
     *     DWORD       BaseOfCode;
     * // Windows-specific fields
     *     ULONGLONG   ImageBase;
     *     DWORD       SectionAlignment;
     *     DWORD       FileAlignment;
     *     WORD        MajorOperatingSystemVersion;
     *     WORD        MinorOperatingSystemVersion;
     *     WORD        MajorImageVersion;
     *     WORD        MinorImageVersion;
     *     WORD        MajorSubsystemVersion;
     *     WORD        MinorSubsystemVersion;
     *     DWORD       Win32VersionValue;
     *     DWORD       SizeOfImage;
     *     DWORD       SizeOfHeaders;
     *     DWORD       CheckSum;
     *     WORD        Subsystem;
     *     WORD        DllCharacteristics;
     *     ULONGLONG   SizeOfStackReserve;
     *     ULONGLONG   SizeOfStackCommit;
     *     ULONGLONG   SizeOfHeapReserve;
     *     ULONGLONG   SizeOfHeapCommit;
     *     DWORD       LoaderFlags;
     *     DWORD       NumberOfRvaAndSizes;
     * // Data directories
     *     IMAGE_DATA_DIRECTORY DataDirectory[IMAGE_NUMBEROF_DIRECTORY_ENTRIES];
     * } IMAGE_OPTIONAL_HEADER64, *PIMAGE_OPTIONAL_HEADER64;
     * </pre>
     *
     * @see <a href=
     *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?redirectedfrom=MSDN#optional-header-windows-specific-fields-image-only">Option
     *      Header</a>
     */
    public static final class ImageOptionNT64Header extends ImageOptionHeader {
        protected static final short IMAGE_NT_OPTIONAL_HDR64_MAGIC = 0x20b;

        public enum ImageDataIndex {
            ExportTable(0),
            ImportTable(1),
            ImportAddressTable(12);

            private final int index;

            ImageDataIndex(int index) {
                this.index = index;
            }

            public int getIndex() {
                return index;
            }
        }

        long imageBase;
        int sectionAlignment;
        int fileAlignment;
        short majorOperatingSystemVersion;
        short minorOperatingSystemVersion;
        short majorImageVersion;
        short minorImageVersion;
        short majorSubsystemVersion;
        short minorSubsystemVersion;
        int win32VersionValue;
        int sizeOfImage;
        int sizeOfHeaders;
        int checksum;
        short subsystem;
        short dllCharacteristics;
        long sizeOfStackReserve;
        long sizeOfStackCommit;
        long sizeOfHeadReserve;
        long sizeOfHeadCommit;
        int loaderFlags;
        ImageDataDirectory[] directories;

        @Override
        protected void readExtended(ObjectFileReader reader) {
            imageBase = reader.getLong();
            sectionAlignment = reader.getInt();
            fileAlignment = reader.getInt();
            majorOperatingSystemVersion = reader.getShort();
            minorOperatingSystemVersion = reader.getShort();
            majorImageVersion = reader.getShort();
            minorImageVersion = reader.getShort();
            majorSubsystemVersion = reader.getShort();
            minorSubsystemVersion = reader.getShort();
            win32VersionValue = reader.getInt();
            sizeOfImage = reader.getInt();
            sizeOfHeaders = reader.getInt();
            checksum = reader.getInt();
            subsystem = reader.getShort();
            dllCharacteristics = reader.getShort();
            sizeOfStackReserve = reader.getLong();
            sizeOfStackCommit = reader.getLong();
            sizeOfHeadReserve = reader.getLong();
            sizeOfHeadCommit = reader.getLong();
            loaderFlags = reader.getInt();
            int numberOfRvaAndSizes = reader.getInt();

            directories = new ImageDataDirectory[numberOfRvaAndSizes];
            for (int i = 0; i < numberOfRvaAndSizes; i++) {
                directories[i] = ImageDataDirectory.createImageDataDirectory(reader);
            }
        }

        private ImageOptionNT64Header() {
        }

        public ImageDataDirectory getDirectory(ImageDataIndex index) {
            return directories.length > index.getIndex() ? directories[index.getIndex()] : null;
        }

        public ImageDataDirectory getImportAddressTableDirectory() {
            return getDirectory(ImageDataIndex.ImportAddressTable);
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

        final int startOffset;
        int virtualSize;
        int virtualAddress;
        String name;
        int sizeOfRawData;
        int pointerToRawData;
        int pointerToRelocations;
        int pointerToLinenumbers;
        short numberOfRelocations;
        short numberOfLinenumbers;
        int characteristics;

        private ImageSectionHeader(int startOffset) {
            this.startOffset = startOffset;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "ImageSectionHeader[" +
                            name +
                            ", virtualSize=" + virtualAddress +
                            ", virtualAddress=" + virtualAddress +
                            ", sizeOfRawData=" + sizeOfRawData +
                            ", pointerToRawData=" + pointerToRawData +
                            ']';
        }

        public ByteSequence getData() {
            return bytes.subSequence(pointerToRawData, pointerToRawData + sizeOfRawData);
        }
    }

    /**
     * Symbol Table.
     *
     * <pre>
     * typedef struct _IMAGE_SYMBOL {
     *     union {
     *       BYTE    ShortName[8];
     *       struct {
     *           DWORD   Short;     // if 0, use LongName
     *           DWORD   Long;      // offset into string table
     *       } Name;
     *       DWORD   LongName[2];    // PBYTE [2]
     *   } N;
     *   DWORD   Value;
     *   SHORT   SectionNumber;
     *   WORD    Type;
     *   BYTE    StorageClass;
     *   BYTE    NumberOfAuxSymbols;
     * } IMAGE_SYMBOL;
     * </pre>
     *
     * @see <a href=
     *      "https://docs.microsoft.com/en-gb/windows/win32/debug/pe-format?redirectedfrom=MSDN#export-directory-table">Export
     *      Directory Table</a>
     */
    public static final class ImageSymbol {
        private static final int IMAGE_SYMBOL_SIZE = 18;
        private static final int AUXILIARY_SYMBOL_SIZE = 18;

        private static final int TYPE_FUNCTION_SYMBOL = 0x20;

        private static final int IMAGE_SYM_CLASS_EXTERNAL = 2;
        private static final int IMAGE_SYM_CLASS_EXTERNAL_DEF = 5;

        String name;
        int value;
        short sectionNumber;
        short type;
        byte storageClass;
        byte numberOfAuxSymbols;

        private static ImageSymbol read(CoffFile file, ObjectFileReader reader) {
            int start = reader.getPosition();

            ImageSymbol symbol = new ImageSymbol();
            int shortName = reader.getInt();
            int nameOffset = reader.getInt();
            symbol.value = reader.getInt();
            symbol.sectionNumber = reader.getShort();
            symbol.type = reader.getShort();
            symbol.storageClass = reader.getByte();
            symbol.numberOfAuxSymbols = reader.getByte();

            assert reader.getPosition() - start == 18;

            if (shortName == 0) {
                symbol.name = file.readStringTableOffset(nameOffset, reader);
            } else {
                byte[] bytes = new byte[8];
                ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).putInt(shortName).putInt(nameOffset);
                symbol.name = CoffFile.nameBytesToString(bytes);
            }

            reader.setPosition(start + IMAGE_SYMBOL_SIZE + AUXILIARY_SYMBOL_SIZE * symbol.numberOfAuxSymbols);

            return symbol;
        }

        /**
         * Returns true if the symbol has a valid section number. Negative and zero section numbers
         * are special values and return false.
         */
        public boolean hasValidSectionNumber() {
            return sectionNumber > 0;
        }

        public boolean isFunction() {
            return type == TYPE_FUNCTION_SYMBOL;
        }

        public boolean isDefinedExternally() {
            return storageClass == IMAGE_SYM_CLASS_EXTERNAL_DEF;
        }

        public boolean isExternal() {
            return storageClass == IMAGE_SYM_CLASS_EXTERNAL;
        }
    }

    private ImageSectionHeader createImageSectionHeader(ObjectFileReader reader) {
        int startOffset = reader.getPosition();
        ImageSectionHeader section = new ImageSectionHeader(startOffset);
        byte[] nameBytes = new byte[ImageSectionHeader.IMAGE_SIZEOF_SHORT_NAME];
        reader.get(nameBytes);
        section.virtualSize = reader.getInt();
        section.virtualAddress = reader.getInt();
        section.sizeOfRawData = reader.getInt();
        section.pointerToRawData = reader.getInt();
        section.pointerToRelocations = reader.getInt();
        section.pointerToLinenumbers = reader.getInt();
        section.numberOfRelocations = reader.getShort();
        section.numberOfLinenumbers = reader.getShort();
        section.characteristics = reader.getInt();
        int endOffset = reader.getPosition();
        section.name = parseName(nameBytes, reader);
        reader.setPosition(endOffset);
        return section;
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
        return reader.getString(start, end - 1, StandardCharsets.UTF_8);
    }

    public String readStringAtVirtualOffset(int offset) {
        ImageSectionHeader section = lookupOffset(offset);
        int virtualOffset = offset - section.virtualAddress + section.pointerToRawData;
        int endOffset = virtualOffset;
        // find end of string
        while (bytes.byteAt(endOffset) != 0) {
            endOffset++;
        }
        return new String(bytes.subSequence(virtualOffset, endOffset).toByteArray(), StandardCharsets.UTF_8);
    }

    public static String nameBytesToString(byte[] nameBytes) {
        // strip trailing '\0's
        int length = 0;
        while (length < nameBytes.length && nameBytes[length] != 0x0) {
            length++;
        }
        return new String(nameBytes, 0, length, StandardCharsets.UTF_8);
    }

    public Source getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("<CoffFile: %s>", source.getPath());
    }
}
