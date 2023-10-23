/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import java.util.Arrays;

import jdk.graal.compiler.core.common.util.TypeConversion;
import jdk.graal.compiler.core.common.util.UnsafeArrayTypeReader;
import jdk.graal.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.ByteArrayReader;

import jdk.vm.ci.meta.LineNumberTable;

public class EncodedLineNumberTable {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static byte[] encode(LineNumberTable table) {
        if (table == null) {
            return null;
        }

        int[] lineNumbers = table.getLineNumbers();
        int[] bcis = table.getBcis();
        assert lineNumbers.length == bcis.length;

        UnsafeArrayTypeWriter encodingBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());

        int lastLineNumber = 0;
        int lastBci = 0;
        encodingBuffer.putUV(lineNumbers.length);
        for (int i = 0; i < lineNumbers.length; i++) {
            encodingBuffer.putSV(lineNumbers[i] - lastLineNumber);
            encodingBuffer.putSV(bcis[i] - lastBci);
            lastLineNumber = lineNumbers[i];
            lastBci = bcis[i];
        }

        byte[] encodedTable = encodingBuffer.toArray(new byte[TypeConversion.asS4(encodingBuffer.getBytesWritten())]);
        assert verifyTable(table, decode(encodedTable));

        return encodedTable;
    }

    public static LineNumberTable decode(byte[] encodedTable) {
        if (encodedTable == null) {
            return null;
        }

        UnsafeArrayTypeReader readBuffer = UnsafeArrayTypeReader.create(encodedTable, 0, ByteArrayReader.supportsUnalignedMemoryAccess());
        int length = readBuffer.getUVInt();
        int[] lineNumbers = new int[length];
        int[] bcis = new int[length];

        int lastLineNumber = 0;
        int lastBci = 0;
        for (int i = 0; i < length; i++) {
            int curLineNumber = lastLineNumber + readBuffer.getSVInt();
            int curBci = lastBci + readBuffer.getSVInt();
            lineNumbers[i] = curLineNumber;
            bcis[i] = curBci;

            lastLineNumber = curLineNumber;
            lastBci = curBci;
        }

        return new LineNumberTable(lineNumbers, bcis);
    }

    public static int getLineNumber(int atBci, byte[] encodedTable) {
        if (encodedTable == null) {
            return -1;
        }

        UnsafeArrayTypeReader readBuffer = UnsafeArrayTypeReader.create(encodedTable, 0, ByteArrayReader.supportsUnalignedMemoryAccess());
        int length = readBuffer.getUVInt();

        int lastLineNumber = 0;
        int lastBci = 0;
        for (int i = 0; i < length; i++) {
            int curLineNumber = lastLineNumber + readBuffer.getSVInt();
            int curBci = lastBci + readBuffer.getSVInt();
            if (lastBci <= atBci && atBci < curBci) {
                assert lastLineNumber == decode(encodedTable).getLineNumber(atBci);
                return lastLineNumber;
            }

            lastLineNumber = curLineNumber;
            lastBci = curBci;
        }

        assert lastLineNumber == decode(encodedTable).getLineNumber(atBci);
        return lastLineNumber;
    }

    private static boolean verifyTable(LineNumberTable expected, LineNumberTable encoded) {
        assert Arrays.equals(expected.getLineNumbers(), encoded.getLineNumbers());
        assert Arrays.equals(expected.getBcis(), encoded.getBcis());
        return true;
    }
}
