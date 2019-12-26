/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.debug.disassembler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Writes machine code into a simple ELF core format.
 */
public class ElfCoreWriter {

    private static final short FILE_HEADER_LENGTH = 64;
    private static final short PROGRAM_HEADER_LENGTH = 56;
    private static final short SECTION_HEADER_LENGTH = 64;

    private static final int START_OF_SECTIONS = FILE_HEADER_LENGTH + PROGRAM_HEADER_LENGTH;

    private static final byte[] MAGIC = new byte[]{0x7f, 'E', 'L', 'F'};
    private static final byte[] SECTION_NAMES = "\0.shstrtab\0.text\0\0\0\0\0\0\0\0".getBytes(StandardCharsets.US_ASCII);

    public static byte[] writeElf(MachineCodeAccessor machineCode) {
        final int codePadding = machineCode.getLength() % 8;
        final ByteBuffer buffer = ByteBuffer.allocate(START_OF_SECTIONS + SECTION_NAMES.length + machineCode.getLength() + codePadding + 3*SECTION_HEADER_LENGTH);
        buffer.order(ByteOrder.nativeOrder());
        writeFileHeader(buffer, machineCode, codePadding);
        writeProgramHeader(buffer, machineCode);
        buffer.put(SECTION_NAMES);
        buffer.put(machineCode.getBytes());
        buffer.put(new byte[codePadding]);
        buffer.put(new byte[SECTION_HEADER_LENGTH]);
        writeSectionHeader(buffer, 1, 3, 0x20, 0, START_OF_SECTIONS, SECTION_NAMES.length);
        writeSectionHeader(buffer, 11, 1, 2 | 4, machineCode.getAddress(), START_OF_SECTIONS + SECTION_NAMES.length, machineCode.getLength() + codePadding);
        final byte[] bytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(bytes);
        return bytes;
    }

    private static void writeFileHeader(ByteBuffer buffer, MachineCodeAccessor machineCode, int codePadding) {
        buffer.put(MAGIC);
        buffer.put((byte) 2);                           // 64-bit
        buffer.put(getByteOrderCode());
        buffer.put((byte) 1);                           // version
        buffer.put((byte) 0);                           // ABI left blank
        buffer.put((byte) 0);                           // ABI version
        buffer.put(new byte[]{0, 0, 0, 0, 0, 0, 0});    // padding
        buffer.putShort((short) 4);                     // core
        buffer.putShort(getArchCode());
        buffer.putInt(1);                               // version
        buffer.putLong(machineCode.getAddress());       // entry point
        buffer.putLong(FILE_HEADER_LENGTH);             // location of program header
        buffer.putLong(START_OF_SECTIONS + SECTION_NAMES.length + machineCode.getLength() + codePadding);
                                                        // location of section header table
        buffer.putInt(0);                               // flags
        buffer.putShort(FILE_HEADER_LENGTH);
        buffer.putShort(PROGRAM_HEADER_LENGTH);
        buffer.putShort((short) 1);                     // number of program headers
        buffer.putShort(SECTION_HEADER_LENGTH);
        buffer.putShort((short) 3);                     // number of section headers
        buffer.putShort((short) 1);                     // section header index for names
    }

    private static void writeProgramHeader(ByteBuffer buffer, MachineCodeAccessor machineCode) {
        buffer.putInt(1);                                           // load
        buffer.putInt(1);                                           // section offset
        buffer.putLong(START_OF_SECTIONS + SECTION_NAMES.length);   // entry point in image
        buffer.putLong(machineCode.getAddress());                   // entry point virtual address
        buffer.putLong(machineCode.getAddress());                   // entry point physical address
        buffer.putLong(machineCode.getLength());                    // image size
        buffer.putLong(machineCode.getLength());                    // memory size
        buffer.putLong(0);                                          // no alignment
    }

    private static void writeSectionHeader(ByteBuffer buffer, int index, int type, int flags, long virtualAddress, long imageAddress, long size) {
        buffer.putInt(index);
        buffer.putInt(type);
        buffer.putLong(flags);
        buffer.putLong(virtualAddress);
        buffer.putLong(imageAddress);
        buffer.putLong(size);
        buffer.putInt(0);       // link index
        buffer.putInt(0);       // info
        buffer.putLong(8);      // alignment
        buffer.putLong(0);      // size of entry
    }

    private static byte getByteOrderCode() {
        final ByteOrder byteOrder = ByteOrder.nativeOrder();
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            return 1;
        } else if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return 2;
        } else {
            throw new UnsupportedOperationException("unknown byte order " + byteOrder + " for ELF");
        }
    }

    private static short getArchCode() {
        final String osArch = System.getProperty("os.arch");
        switch (osArch) {
            case "sparc":
                return 0x02;
            case "x86_64":
                return 0x3e;
            case "amd64":
                return 0x3e;
            case "aarch64":
                return 0xb7;
            default:
                throw new UnsupportedOperationException("unknown architecture " + osArch + " for ELF");
        }
    }

}
