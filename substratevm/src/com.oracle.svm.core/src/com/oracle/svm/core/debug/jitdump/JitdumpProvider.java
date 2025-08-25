/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.debug.jitdump;

import java.nio.ByteOrder;

import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.MethodEntry;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.util.Digest;

public class JitdumpProvider {

    /**
     * Generates the byte array of a jitdump header.
     *
     * @return The content of the jitdump header
     */
    public static byte[] writeHeader() {
        int pos = 0;
        byte[] content = new byte[JitdumpHeader.SIZE];

        JitdumpHeader header = new JitdumpHeader();
        pos = writeInt(JitdumpHeader.MAGIC, content, pos);
        pos = writeInt(JitdumpHeader.VERSION, content, pos);
        pos = writeInt(JitdumpHeader.SIZE, content, pos);
        pos = writeInt(header.elfMach().toShort(), content, pos);
        pos = writeInt(0, content, pos);  // padding. Reserved for future use
        pos = writeInt(header.pid(), content, pos);
        pos = writeLong(header.timestamp(), content, pos);
        pos = writeLong(0, content, pos);  // no flags needed

        assert pos == JitdumpHeader.SIZE;
        return content;
    }

    /**
     * Generates the byte array for the jitdump records of a run-time compilation.
     *
     * @param compiledMethodEntry the {@code CompiledMethodEntry} of the run-time compiled method
     * @param compilation the {@code CompilationResult}
     * @param codeSize the code size
     * @param codeAddress the code address
     * @return The content of the jitdump records for the run-time compiled method
     */
    public static byte[] writeRecords(CompiledMethodEntry compiledMethodEntry, CompilationResult compilation, int codeSize, long codeAddress) {
        MethodEntry methodEntry = compiledMethodEntry.primary().getMethodEntry();
        JitdumpCodeLoadRecord cl = JitdumpCodeLoadRecord.create(methodEntry, codeSize, codeAddress);
        JitdumpDebugInfoRecord di = JitdumpDebugInfoRecord.create(compiledMethodEntry, codeAddress);

        int totalSize = cl.header().recordSize() + di.header().recordSize();
        byte[] content = new byte[totalSize];
        int pos = 0;

        /*
         * First write the debug info record for the run-time compiled method.
         */
        pos = writeRecordHeader(di.header(), content, pos);
        // Add content.
        pos = writeLong(di.address(), content, pos);
        pos = writeLong(di.entries().size(), content, pos);
        for (JitdumpDebugInfoRecord.JitdumpDebugEntry de : di.entries()) {
            pos = writeLong(de.address(), content, pos);
            pos = writeInt(de.line(), content, pos);
            pos = writeInt(de.discriminator(), content, pos);
            // Add file name with terminating null.
            for (byte b : de.filename().getBytes()) {
                pos = writeByte(b, content, pos);
            }
            pos = writeByte((byte) 0, content, pos);
        }
        assert pos == di.header().recordSize();

        /*
         * Add corresponding code load record after the debug info record.
         */
        pos = writeRecordHeader(cl.header(), content, pos);
        // Add content.
        pos = writeInt(cl.pid(), content, pos);
        pos = writeInt(cl.tid(), content, pos);
        // Virtual address = address.
        pos = writeLong(cl.address(), content, pos);
        pos = writeLong(cl.address(), content, pos);
        pos = writeLong(cl.size(), content, pos);
        // Code index -> unique identifier for run-time compiled code.
        pos = writeLong(Digest.digestAsUUID(compilation.getCompilationId().toString()).getLeastSignificantBits(), content, pos);
        // Add method name with terminating null.
        for (byte b : cl.name().getBytes()) {
            pos = writeByte(b, content, pos);
        }
        pos = writeByte((byte) 0, content, pos);
        // Add compiled code.
        byte[] targetCode = compilation.getTargetCode();
        for (int i = 0; i < codeSize; i++) {
            pos = writeByte(targetCode[i], content, pos);
        }

        assert pos == totalSize;
        return content;
    }

    private static int writeRecordHeader(JitdumpRecordHeader header, byte[] content, int p) {
        int pos = p;

        pos = writeInt(header.id().value(), content, pos);
        pos = writeInt(header.recordSize(), content, pos);
        pos = writeLong(System.currentTimeMillis(), content, pos);

        return pos;
    }

    private static boolean littleEndian() {
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    }

    private static int writeByte(byte b, byte[] content, int p) {
        int pos = p;
        content[pos++] = b;
        return pos;
    }

    private static int writeShort(short s, byte[] content, int p) {
        int pos = p;
        if (littleEndian()) {
            pos = writeByte((byte) s, content, pos);
            pos = writeByte((byte) (s >> Byte.SIZE), content, pos);
        } else {
            pos = writeByte((byte) (s >> Byte.SIZE), content, pos);
            pos = writeByte((byte) s, content, pos);
        }
        return pos;
    }

    private static int writeInt(int i, byte[] content, int p) {
        int pos = p;
        if (littleEndian()) {
            pos = writeShort((short) i, content, pos);
            pos = writeShort((short) (i >> Short.SIZE), content, pos);
        } else {
            pos = writeShort((short) (i >> Short.SIZE), content, pos);
            pos = writeShort((short) i, content, pos);
        }
        return pos;
    }

    private static int writeLong(long l, byte[] content, int p) {
        int pos = p;
        if (littleEndian()) {
            pos = writeInt((int) l, content, pos);
            pos = writeInt((int) (l >> Integer.SIZE), content, pos);
        } else {
            pos = writeInt((int) (l >> Integer.SIZE), content, pos);
            pos = writeInt((int) l, content, pos);
        }
        return pos;
    }
}
