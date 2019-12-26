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
package org.graalvm.compiler.truffle.runtime.debug;

import org.graalvm.compiler.truffle.options.DisassemblyFormatType;
import sun.misc.Unsafe;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ByteChannel;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Disassembler {

    public static String disassemble(DisassemblyFormatType disassemblyFormat, long address, long size) throws IOException {
        switch (disassemblyFormat) {
            case HEX:
                return disassembleHex(address, size);
            case RAW:
                return disassembleRaw(address, size);
            case ELF:
                return disassembleElf(address, size);
            case OBJDUMP:
                return disassembleObjdump(address, size);
            default:
                throw new UnsupportedOperationException();
        }
    }

    private static String disassembleHex(long address, long size) {
        final byte[] code = readMemory(address, size);

        final StringBuilder builder = new StringBuilder();

        final int bytesPerLine = 32;

        int p = 0;

        while (p < code.length) {
            builder.append(String.format("0x%016x ", address + p));

            for (int n = 0; n < bytesPerLine && p + n < code.length; n++) {
                if (n % 8 == 0) {
                    builder.append(' ');
                }

                builder.append(String.format("%02x", Byte.toUnsignedInt(code[p + n])));
            }

            p += bytesPerLine;

            if (p < code.length) {
                builder.append(System.lineSeparator());
            }
        }

        return builder.toString();
    }

    private static String disassembleRaw(long address, long size) throws IOException {
        final byte[] code = readMemory(address, size);
        final String rawFile = String.format("truffle_compiled_code_%d_0x%x_%d.raw", getPid(), address, System.nanoTime());
        Files.write(Paths.get(rawFile), code, StandardOpenOption.CREATE_NEW);
        return String.format("written to %s - load or disassemble at 0x%x", rawFile, address);
    }

    private static String disassembleElf(long address, long size) throws IOException {
        final String rawFile = String.format("truffle_compiled_code_%d_0x%x_%d.elf", getPid(), address, System.nanoTime());
        Files.write(Paths.get(rawFile), writeElf(address, size), StandardOpenOption.CREATE_NEW);
        return String.format("written to %s", rawFile, address);
    }

    private static String disassembleObjdump(long address, long size) throws IOException {
        final Process process = new ProcessBuilder()
                .command("objdump", "--no-show-raw-insn", "-d", "/dev/stdin")
                .start();
        final OutputStream objdumpInputStream = process.getOutputStream();
        final InputStream objdumpErrorStream = process.getErrorStream();
        final InputStream objdumpOutputStream = process.getInputStream();
        objdumpInputStream.write(writeElf(address, size));
        objdumpInputStream.close();
        final ByteArrayOutputStream objdumpError = new ByteArrayOutputStream();
        final ByteArrayOutputStream objdumpOutput = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        while (process.isAlive() || objdumpErrorStream.available() > 0 || objdumpOutputStream.available() > 0) {
            if (objdumpErrorStream.available() > 0) {
                final int read = objdumpErrorStream.read(buffer);
                objdumpError.write(buffer, 0, read);
            }
            if (objdumpOutputStream.available() > 0) {
                final int read = objdumpOutputStream.read(buffer);
                objdumpOutput.write(buffer, 0, read);
            }
        }
        int objdumpExitCode;
        while (true) {
            try {
                objdumpExitCode = process.waitFor();
                break;
            } catch (InterruptedException e) {
                continue;
            }
        }
        if (objdumpExitCode == 0) {
            final StringBuilder builder = new StringBuilder();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(objdumpOutput.toByteArray())));
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.isEmpty() || line.startsWith("/dev/stdin:") || line.startsWith("Disassembly of section .text:") || line.startsWith(".text:")) {
                    continue;
                }
                builder.append(line);
                builder.append(System.lineSeparator());
            }
            return builder.toString().trim();
        } else {
            return objdumpError.toString(Charset.defaultCharset().name());
        }
    }

    private static byte[] writeElf(long address, long size) {
        final byte[] code = readMemory(address, size);
        final int codePadding = code.length % 8;
        final short fileHeaderLength = 64;
        final short programHeaderLength = 56;
        final short sectionHeaderLength = 64;
        final byte[] sectionNames = "\0.shstrtab\0.text\0\0\0\0\0\0\0\0".getBytes(StandardCharsets.US_ASCII);
        final ByteBuffer buffer = ByteBuffer.allocate(fileHeaderLength + programHeaderLength + sectionNames.length + code.length + codePadding + 3*sectionHeaderLength);
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
        buffer.putLong(address);                                                                        // entry point
        buffer.putLong(fileHeaderLength);                                                               // location of program header
        buffer.putLong(fileHeaderLength + programHeaderLength + sectionNames.length + code.length + codePadding);     // location of section header table
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
        buffer.putLong(address);                                                                        // virtual address
        buffer.putLong(address);                                                                        // physical address
        buffer.putLong(size);                                                                           // image size
        buffer.putLong(size);                                                                           // memory size
        buffer.putLong(0);                                                                              // no alignment
        // Section names
        assert buffer.position() == fileHeaderLength + programHeaderLength;
        buffer.put(sectionNames);
        // Code
        assert buffer.position() == fileHeaderLength + programHeaderLength + sectionNames.length;
        buffer.put(code);
        buffer.put(new byte[codePadding]);
        // Null section header
        assert buffer.position() == fileHeaderLength + programHeaderLength + sectionNames.length + code.length + codePadding;
        buffer.put(new byte[sectionHeaderLength]);
        // Section names section header table
        assert buffer.position() == fileHeaderLength + programHeaderLength + sectionNames.length + code.length + codePadding + sectionHeaderLength;
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
        assert buffer.position() == fileHeaderLength + programHeaderLength + sectionNames.length + code.length + codePadding + 2*sectionHeaderLength;
        buffer.putInt(11);                                                                              // index 11 in section names
        buffer.putInt(1);                                                                               // program data
        buffer.putLong(2 | 4);                                                                          // allocated and executable
        buffer.putLong(address);                                                                        // virtual address
        buffer.putLong(fileHeaderLength + programHeaderLength + sectionNames.length);                   // image address
        buffer.putLong(code.length + codePadding);                                                      // size
        buffer.putInt(0);                                                                               // link
        buffer.putInt(0);                                                                               // extra
        buffer.putLong(8);                                                                              // alignment
        buffer.putLong(0);                                                                              // entry size
        assert buffer.position() == fileHeaderLength + programHeaderLength + sectionNames.length + code.length + codePadding + 3*sectionHeaderLength;
        final byte[] bytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(bytes);
        return bytes;
    }

    private static byte[] readMemory(long address, long size) {
        if (size > Integer.MAX_VALUE) {
            throw new UnsupportedOperationException();
        }

        final byte[] bytes = new byte[(int) size];

        for (int n = 0; n < bytes.length; n++) {
            bytes[n] = UNSAFE.getByte(address + n);
        }

        return bytes;
    }

    private static long getPid() {
        final String info = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        return Long.parseLong(info.split("@")[0]);
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

    private static final Unsafe UNSAFE = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
