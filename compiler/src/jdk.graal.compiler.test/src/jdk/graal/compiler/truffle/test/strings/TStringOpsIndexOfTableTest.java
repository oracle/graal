/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TStringOpsIndexOfTableTest extends TStringOpsTest<ArrayIndexOfNode> {

    @Parameters(name = "{index}: offset: {1}, length: {2}, stride: {3}, fromIndex: {4}, values: {5}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offset = 20;
        int padding = 20;
        for (int strideA = 0; strideA < 3; strideA++) {
            int contentLength = 256;
            byte[] arrayA = createTestArray(offset, padding, strideA, contentLength);
            int[] offsets = {offset << strideA,
                            (offset + (contentLength / 2)) << strideA};
            for (int offsetA : offsets) {
                for (int iFromIndex = 0; iFromIndex < 3; iFromIndex++) {
                    int fromIndexA = contentLength * iFromIndex;
                    for (int lengthA : new int[]{0, 1, 2, 3, 4, 5, 7, 8, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129, 255, 256}) {
                        if (fromIndexA >= lengthA) {
                            continue;
                        }
                        for (byte[] table : new byte[][]{
                                        {0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
                                        {0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                                                        0x01, 0x01, 0x01, 0x01, 0x01, 0x01},
                                        {0x01, 0x02, 0x04, 0x08, 0x00, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1b, 0x18, 0x19, 0x1c, 0x1b, 0x1c, 0x18, 0x18, 0x18, 0x18,
                                                        0x18, 0x18, 0x18, 0x18, 0x18, 0x08},
                                        {0x01, 0x02, 0x04, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0b, 0x0d, 0x0d, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
                                                        0x05, 0x05, 0x05, 0x05, 0x05, 0x05},
                                        {(byte) 0xff, 0x01, (byte) 0x88, 0x02, 0x04, 0x08, (byte) 0xff, 0x10, 0x20, 0x40, (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x77, (byte) 0xf6, 0x7e,
                                                        (byte) 0xfc, 0x6e, (byte) 0xfe, 0x7e, (byte) 0xfe, 0x3e, 0x5e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1a},
                                        {0x03, 0x01, 0x02, 0x04, 0x0c, 0x08, 0x30, 0x10, 0x20, 0x40, (byte) 0xc0, (byte) 0x80, (byte) 0xc7, 0x00, 0x00, 0x00, 0x02, 0x01, 0x00, 0x00, 0x04, 0x08, 0x20,
                                                        0x10, 0x00, 0x00, 0x40, (byte) 0x80, 0x00, 0x00, 0x00, 0x00},
                                        {0x00, 0x00, 0x00, 0x01, 0x02, 0x04, 0x02, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0d, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f,
                                                        0x0e, 0x02, 0x02, 0x02, 0x02, 0x06},
                                        {0x01, 0x01, 0x01, 0x02, 0x04, 0x08, 0x04, 0x10, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                                                        0x03, 0x1b, 0x1b, 0x1b, 0x1b, 0x13},
                                        {0x00, 0x00, 0x00, 0x01, 0x02, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x01, 0x01, 0x01,
                                                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00}
                        }) {
                            ret.add(new Object[]{arrayA, offsetA, lengthA, strideA, fromIndexA, table});
                        }
                    }
                }
            }
        }
        return ret;
    }

    private static byte[] createTestArray(int offset, int padding, int stride, int contentLength) {
        byte[] array = new byte[(offset + (contentLength * 3) + padding) << stride];
        int[] valueOffset = {0, 0x1000, 0x10_0000};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < contentLength; j++) {
                writeValue(array, stride, offset + j + (contentLength * i), j + valueOffset[i]);
            }
        }
        return array;
    }

    final byte[] arrayA;
    final int offsetA;
    final int lengthA;
    final int strideA;
    final int fromIndexA;
    final byte[] table;

    public TStringOpsIndexOfTableTest(byte[] arrayA, int offsetA, int lengthA, int strideA, int fromIndexA, byte[] table) {
        super(ArrayIndexOfNode.class);
        this.arrayA = arrayA;
        this.offsetA = offsetA;
        this.lengthA = lengthA;
        this.strideA = strideA;
        this.fromIndexA = fromIndexA;
        this.table = table;
    }

    @Test
    public void testIndexOfAny() {
        test(getIndexOfTableIntl(), null, DUMMY_LOCATION, arrayA, offsetA, lengthA, strideA, fromIndexA, table);
    }
}
