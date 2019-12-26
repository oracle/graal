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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ElfDisassembler implements Disassembler {

    public String disassemble(MachineCodeAccessor machineCode) throws IOException {
        final String elfFile = machineCode.fileName(".elf");
        Files.write(Paths.get(elfFile), writeElf(machineCode), StandardOpenOption.CREATE_NEW);
        return String.format("written to %s", elfFile, machineCode.getAddress());
    }

    public static byte[] writeElf(MachineCodeAccessor machineCode) {
        final int codePadding = machineCode.getLength() % 8;
        final short fileHeaderLength = 64;
        final short programHeaderLength = 56;
        final short sectionHeaderLength = 64;
        final byte[] sectionNames = "\0.shstrtab\0.text\0\0\0\0\0\0\0\0".getBytes(StandardCharsets.US_ASCII);
        final ByteBuffer buffer = ByteBuffer.allocate(fileHeaderLength + programHeaderLength + sectionNames.length + machineCode.getLength() + codePadding + 3*sectionHeaderLength);
        buffer.order(ByteOrder.nativeOrder());
        // File header
        buffer.put(new byte[]{0x7f, 'E', 'L', 'F'});                                                    // magic
        buffer.put((byte) 2);                                                                           // 64-bit
        buffer.put((byte) (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? 1 : 2));                // byte order
        buffer.put((byte) 1);                                                                           // version
        buffer.put((byte) 0);                                                                           // ABI left blank
        buffer.put((byte) 0);                                                                           // ABI version
        buffer.put(new byte[]{0, 0, 0, 0, 0, 0, 0});                                                    // padding
        buffer.putShort((short) 2);                                                                     // executable
        buffer.putShort(getElfArch());                                                                  // architecture
        buffer.putInt(1);                                                                               // version
        buffer.putLong(machineCode.getAddress());                                                       // entry point
        buffer.putLong(fileHeaderLength);                                                               // location of program header
        buffer.putLong(fileHeaderLength + programHeaderLength + sectionNames.length + machineCode.getLength() + codePadding);     // location of section header table
        buffer.putInt(0);                                                                               // flags
        buffer.putShort(fileHeaderLength);                                                              // length of file header
        buffer.putShort(programHeaderLength);                                                           // length of program header
        buffer.putShort((short) 1);                                                                     // number of program headers
        buffer.putShort(sectionHeaderLength);                                                           // length of section header
        buffer.putShort((short) 3);                                                                     // number of section headers
        buffer.putShort((short) 1);                                                                     // section header index for names
        // Program header
        assert buffer.position() == fileHeaderLength;
        buffer.putInt(1);                                                                               // load
        buffer.putInt(0);                                                                               // flags
        buffer.putLong(fileHeaderLength + programHeaderLength + sectionNames.length);                   // location in image
        buffer.putLong(machineCode.getAddress());                                                       // virtual address
        buffer.putLong(machineCode.getAddress());                                                       // physical address
        buffer.putLong(machineCode.getLength());                                                        // image size
        buffer.putLong(machineCode.getLength());                                                        // memory size
        buffer.putLong(0);                                                                              // no alignment
        // Section names
        assert buffer.position() == fileHeaderLength + programHeaderLength;
        buffer.put(sectionNames);
        // Code
        assert buffer.position() == fileHeaderLength + programHeaderLength + sectionNames.length;
        buffer.put(machineCode.getBytes());
        buffer.put(new byte[codePadding]);
        // Null section header
        assert buffer.position() == fileHeaderLength + programHeaderLength + sectionNames.length + machineCode.getLength() + codePadding;
        buffer.put(new byte[sectionHeaderLength]);
        // Section names section header table
        assert buffer.position() == fileHeaderLength + programHeaderLength + sectionNames.length + machineCode.getLength() + codePadding + sectionHeaderLength;
        buffer.putInt(1);                                                                               // index 1 in section names
        buffer.putInt(3);                                                                               // string table
        buffer.putLong(0x20);                                                                           // null-terminated strings
        buffer.putLong(0);                                                                              // virtual address
        buffer.putLong(fileHeaderLength + programHeaderLength);                                         // image address
        buffer.putLong(sectionNames.length);                                                            // size
        buffer.putInt(0);                                                                               // link
        buffer.putInt(0);                                                                               // extra
        buffer.putLong(8);                                                                              // alignment
        buffer.putLong(0);                                                                              // entry size
        // Text section header table
        assert buffer.position() == fileHeaderLength + programHeaderLength + sectionNames.length + machineCode.getLength() + codePadding + 2*sectionHeaderLength;
        buffer.putInt(11);                                                                              // index 11 in section names
        buffer.putInt(1);                                                                               // program data
        buffer.putLong(2 | 4);                                                                          // allocated and executable
        buffer.putLong(machineCode.getAddress());                                                       // virtual address
        buffer.putLong(fileHeaderLength + programHeaderLength + sectionNames.length);                   // image address
        buffer.putLong(machineCode.getLength() + codePadding);                                          // size
        buffer.putInt(0);                                                                               // link
        buffer.putInt(0);                                                                               // extra
        buffer.putLong(8);                                                                              // alignment
        buffer.putLong(0);                                                                              // entry size
        assert buffer.position() == fileHeaderLength + programHeaderLength + sectionNames.length + machineCode.getLength() + codePadding + 3*sectionHeaderLength;
        final byte[] bytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(bytes);
        return bytes;
    }

    private static short getElfArch() {
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
