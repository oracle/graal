/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.macho;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.parser.binary.BinaryParser;
import com.oracle.truffle.llvm.parser.macho.MachOSegmentCommand.MachOSection;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import org.graalvm.polyglot.io.ByteSequence;

public final class MachOFile {

    private static final String INTERMEDIATE_SEGMENT = "";
    private static final String BITCODE_SECTION = "__bitcode";
    private static final String LLVM_SEGMENT = "__LLVM";
    private static final String BUNDLE_SECTION = "__bundle";

    private static final int MH_OBJECT = 0x1; /* relocatable object file */
    private static final int MH_EXECUTE = 0x2; /* demand paged executable file */
    private static final int MH_DYLIB = 0x6; /* dynamically bound shared library */

    // currently unused types:
    @SuppressWarnings("unused") private static final int MH_FVMLIB = 0x3;
    @SuppressWarnings("unused") private static final int MH_CORE = 0x4;
    @SuppressWarnings("unused") private static final int MH_PRELOAD = 0x5;
    @SuppressWarnings("unused") private static final int MH_DYLINKER = 0x7;
    @SuppressWarnings("unused") private static final int MH_BUNDLE = 0x8;
    @SuppressWarnings("unused") private static final int MH_DYLIB_STUB = 0x9;

    private final MachOHeader header;
    private final MachOLoadCommandTable loadCommandTable;
    private final ByteSequence buffer;

    MachOFile(MachOHeader header, MachOLoadCommandTable loadCommandTable, ByteSequence buffer) {
        this.header = header;
        this.loadCommandTable = loadCommandTable;
        this.buffer = buffer;
    }

    public MachOSegmentCommand getSegment(String name) {
        return loadCommandTable.getSegment(name);
    }

    public MachOHeader getHeader() {
        return header;
    }

    public List<String> getDyLibs() {
        List<String> dylibs = new ArrayList<>();
        for (MachOLoadCommand lc : loadCommandTable.getLoadCommands()) {
            if (lc instanceof MachODylibCommand) {
                dylibs.add(((MachODylibCommand) lc).getName());
            }
        }
        return dylibs;
    }

    public ByteSequence extractBitcode() {
        switch (header.getFileType()) {
            case MH_OBJECT:
                return getSectionData(INTERMEDIATE_SEGMENT, BITCODE_SECTION);
            case MH_DYLIB:
            case MH_EXECUTE:
                return getSectionData(LLVM_SEGMENT, BUNDLE_SECTION);
            default:
                throw new LLVMParserException("Mach-O file type not supported!");
        }
    }

    public ByteSequence getSectionData(String segment, String section) {
        MachOSegmentCommand seg = loadCommandTable.getSegment(segment);

        if (seg == null) {
            // Mach-O file does not contain the segment
            return null;
        }

        MachOSection sect = seg.getSection(section);

        if (sect == null) {
            // Mach-O file does not contain a section
            return null;
        }

        int offset = sect.getOffset();
        long size = sect.getSize();

        return buffer.subSequence(offset, offset + (int) size);
    }

    public static MachOFile create(ByteSequence data) {
        return MachOReader.create(data);
    }

    public static boolean isMachOMagicNumber(long magic) {
        return isMachO32MagicNumber(magic) || isMachO64MagicNumber(magic);
    }

    public static boolean isMachO32MagicNumber(long magic) {
        return magic == BinaryParser.Magic.MH_MAGIC.magic || magic == BinaryParser.Magic.MH_CIGAM.magic;
    }

    public static boolean isMachO64MagicNumber(long magic) {
        return magic == BinaryParser.Magic.MH_MAGIC_64.magic || magic == BinaryParser.Magic.MH_CIGAM_64.magic;
    }

}
