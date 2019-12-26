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

import org.graalvm.compiler.truffle.options.DisassemblyFormatType;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

public class MachineCode {

    private final long address;
    private final byte[] code;

    public static MachineCode read(long address, int size) {
        return new MachineCode(address, readCode(address, size));
    }

    private MachineCode(long address, byte[] code) {
        this.address = address;
        this.code = code;
    }

    public long getAddress() {
        return address;
    }

    public int getLength() {
        return code.length;
    }

    public String disassemble(DisassemblyFormatType disassemblyFormat) throws IOException {
        return createDisassembler(disassemblyFormat).disassemble(new MachineCodeAccessor() {

            @Override
            public long getAddress() {
                return address;
            }

            @Override
            public int getLength() {
                return code.length;
            }

            @Override
            public byte getByte(int n) {
                return code[n];
            }

            @Override
            public byte[] getBytes() {
                return Arrays.copyOf(code, code.length);
            }

            @Override
            public String fileName(String extension) {
                return String.format("truffle_compiled_code_%d_0x%x_%d%s", getPid(), address, System.nanoTime(), extension);
            }

        });
    }

    private Disassembler createDisassembler(DisassemblyFormatType disassemblyFormat) {
        switch (disassemblyFormat) {
            case HEX:
                return new HexDisassembler();
            case RAW:
                return new RawDisassembler();
            case ELF:
                return new ElfDisassembler();
            case OBJDUMP:
                return new ObjdumpDisassembler();
            default:
                throw new UnsupportedOperationException();
        }
    }

    private static long getPid() {
        try {
            final String info = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            return Long.parseLong(info.split("@")[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    private static byte[] readCode(long address, int size) {
        final byte[] bytes = new byte[size];

        for (int n = 0; n < bytes.length; n++) {
            bytes[n] = UNSAFE.getByte(address + n);
        }

        return bytes;
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
