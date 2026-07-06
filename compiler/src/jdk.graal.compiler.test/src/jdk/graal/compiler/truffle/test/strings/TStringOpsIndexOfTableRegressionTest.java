/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.strings;

import org.junit.Test;

import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;

public class TStringOpsIndexOfTableRegressionTest extends TStringOpsTest<ArrayIndexOfNode> {

    private static final int OFFSET = 20;
    private static final int LENGTH = 256;
    private static final int STRIDE = 2;
    private static final int WIDENED_VALUE = 0x01020374;

    public TStringOpsIndexOfTableRegressionTest() {
        super(ArrayIndexOfNode.class);
    }

    @Test
    public void testIndexOfTableS4WidenedValuesDoNotMatchZeroTable() {
        testWithNative(getIndexOfTableIntl(), null, DUMMY_LOCATION, createWidenedArray(), byteArrayBaseOffset() + (OFFSET << STRIDE), LENGTH, STRIDE, 0, createSingleByteTable(0x00));
    }

    @Test
    public void testIndexOfTableS4WidenedValuesDoNotMatchZeroTableForeignEndian() {
        testWithNative(getIndexOfTableForeignEndianIntl(), null, DUMMY_LOCATION, byteSwapArray(createWidenedArray(), STRIDE), byteArrayBaseOffset() + (OFFSET << STRIDE), LENGTH, STRIDE, 0,
                        createSingleByteTable(0x00));
    }

    private static byte[] createWidenedArray() {
        byte[] array = new byte[(OFFSET + LENGTH + OFFSET) << STRIDE];
        for (int i = 0; i < LENGTH; i++) {
            writeValue(array, STRIDE, OFFSET + i, WIDENED_VALUE);
        }
        return array;
    }

    private static byte[] createSingleByteTable(int value) {
        byte[] table = new byte[32];
        int bit = 1;
        table[(value >>> 4) & 0xf] = (byte) bit;
        table[16 + (value & 0xf)] = (byte) bit;
        return table;
    }
}
