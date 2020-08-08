/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.binary;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.elf.ElfDynamicSection;
import com.oracle.truffle.llvm.parser.elf.ElfFile;
import com.oracle.truffle.llvm.parser.elf.ElfLibraryLocator;
import com.oracle.truffle.llvm.parser.elf.ElfSectionHeaderTable;
import com.oracle.truffle.llvm.parser.macho.MachOFile;
import com.oracle.truffle.llvm.parser.macho.MachOLibraryLocator;
import com.oracle.truffle.llvm.parser.macho.Xar;
import com.oracle.truffle.llvm.parser.scanner.BitStream;
import com.oracle.truffle.llvm.runtime.DefaultLibraryLocator;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.Magic;
import org.graalvm.polyglot.io.ByteSequence;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a binary {@linkplain ByteSequence file} and returns the embedded {@linkplain ByteSequence
 * bitcode data}. Supported file types are plain bitcode, ELF files, and Mach-O files.
 */
public final class BinaryParser {

    private ArrayList<String> libraries = new ArrayList<>();
    private ArrayList<String> paths = new ArrayList<>();
    private LibraryLocator locator = DefaultLibraryLocator.INSTANCE;

    public static Magic getMagic(BitStream b) {
        try {
            return Magic.get(Integer.toUnsignedLong((int) b.read(0, Integer.SIZE)));
        } catch (Exception e) {
            /*
             * An exception here means we can't read at least 4 bytes from the file. That means it
             * is definitely not a bitcode or ELF file.
             */
            return Magic.UNKNOWN;
        }
    }

    public static BinaryParserResult parse(ByteSequence bytes, Source bcSource, LLVMContext context) {
        return new BinaryParser().parseInternal(bytes, bcSource, context);
    }

    private BinaryParserResult parseInternal(ByteSequence bytes, Source bcSource, LLVMContext context) {
        assert bytes != null;

        ByteSequence bitcode = parseBitcode(bytes, bcSource);
        if (bitcode == null) {
            // unsupported file
            return null;
        }
        if (bcSource != null) {
            LibraryLocator.traceParseBitcode(context, bcSource.getPath());
        }
        return new BinaryParserResult(libraries, paths, bitcode, locator, bcSource);
    }

    public static String getOrigin(Source source) {
        if (source == null) {
            return null;
        }
        String sourcePath = source.getPath();
        if (sourcePath == null) {
            return null;
        }
        Path parent = Paths.get(sourcePath).getParent();
        if (parent == null) {
            return null;
        }
        return parent.toString();
    }

    private ByteSequence parseBitcode(ByteSequence bytes, Source source) {
        BitStream b = BitStream.create(bytes);
        Magic magicWord = getMagic(b);
        switch (magicWord) {
            case BC_MAGIC_WORD:
                return bytes;
            case WRAPPER_MAGIC_WORD:
                // 0: magic word
                // 32: version
                // 64: offset32
                long offset = b.read(64, Integer.SIZE);
                // 96: size32
                long size = b.read(96, Integer.SIZE);
                return bytes.subSequence((int) offset, (int) (offset + size));
            case ELF_MAGIC_WORD:
                ElfFile elfFile = ElfFile.create(bytes);
                ElfSectionHeaderTable.Entry llvmbc = elfFile.getSectionHeaderTable().getEntry(".llvmbc");
                if (llvmbc == null) {
                    // ELF File does not contain an .llvmbc section
                    return null;
                }
                ElfDynamicSection dynamicSection = elfFile.getDynamicSection();
                if (dynamicSection != null) {
                    List<String> elfLibraries = dynamicSection.getDTNeeded();
                    libraries.addAll(elfLibraries);
                    locator = new ElfLibraryLocator(elfFile, source);
                }
                long elfOffset = llvmbc.getOffset();
                long elfSize = llvmbc.getSize();
                return bytes.subSequence((int) elfOffset, (int) (elfOffset + elfSize));
            case MH_MAGIC:
            case MH_CIGAM:
            case MH_MAGIC_64:
            case MH_CIGAM_64:
                MachOFile machOFile = MachOFile.create(bytes);

                String origin = getOrigin(source);
                List<String> machoLibraries = machOFile.getDyLibs(origin);
                locator = new MachOLibraryLocator(machOFile, source);
                libraries.addAll(machoLibraries);

                ByteSequence machoBitcode = machOFile.extractBitcode();
                if (machoBitcode == null) {
                    return null;
                }
                return parseBitcode(machoBitcode, source);
            case XAR_MAGIC:
                Xar xarFile = Xar.create(bytes);
                ByteSequence xarBitcode = xarFile.extractBitcode();
                if (xarBitcode == null) {
                    return null;
                }
                return parseBitcode(xarBitcode, source);
            default:
                return null;
        }
    }

}
