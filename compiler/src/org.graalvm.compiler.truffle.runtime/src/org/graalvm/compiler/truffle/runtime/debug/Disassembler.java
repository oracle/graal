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

import java.io.IOException;
import java.lang.reflect.Field;
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
        final String rawFile = String.format("truffle_compiled_code_%d_0x%x.raw", getPid(), address);
        final byte[] code = readMemory(address, size);
        Files.write(Paths.get(rawFile), code, StandardOpenOption.CREATE_NEW);
        return String.format("written to %s - load or disassemble at 0x%x", rawFile, address);
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
