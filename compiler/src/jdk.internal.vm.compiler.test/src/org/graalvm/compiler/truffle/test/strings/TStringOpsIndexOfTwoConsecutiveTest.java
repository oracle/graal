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
package org.graalvm.compiler.truffle.test.strings;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.replacements.nodes.ArrayIndexOfNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TStringOpsIndexOfTwoConsecutiveTest extends TStringOpsTest<ArrayIndexOfNode> {

    @Parameters(name = "{index}: offset: {1}, length: {2}, stride: {3}, fromIndex: {4}, toIndex: {5}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offset = 20;
        int padding = 20;
        for (int strideA = 0; strideA < 3; strideA++) {
            int contentLength = 256;
            byte[] arrayA = createTestArray(offset, padding, strideA, contentLength);
            for (int strideB = 0; strideB < 3; strideB++) {
                for (int offsetA : new int[]{
                                offset << strideA,
                                (offset + (contentLength / 2)) << strideA}) {
                    for (int iFromIndex = 0; iFromIndex < 3; iFromIndex++) {
                        int fromIndexA = contentLength * iFromIndex;
                        for (int lengthA : new int[]{0, 1, 2, 3, 4, 5, 7, 8, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129}) {
                            if (fromIndexA >= lengthA) {
                                continue;
                            }
                            int off = (offsetA >> strideA) + fromIndexA;
                            int vBefore = readValue(arrayA, strideA, off - 1);
                            int v0 = readValue(arrayA, strideA, off);
                            int v1 = readValue(arrayA, strideA, off + 1);
                            int vBeforeLast = readValue(arrayA, strideA, off + lengthA - 2);
                            int vLast = readValue(arrayA, strideA, off + lengthA - 1);
                            int vAfter = readValue(arrayA, strideA, off + lengthA);
                            ret.add(new Object[]{arrayA, offsetA, lengthA, strideA, fromIndexA, v0, v1, 0x20, 0x20});
                            ret.add(new Object[]{arrayA, offsetA, lengthA, strideA, fromIndexA, vBefore, v0, 0x20, 0x20});
                            ret.add(new Object[]{arrayA, offsetA, lengthA, strideA, fromIndexA, vBeforeLast, vLast, 0x20, 0x20});
                            ret.add(new Object[]{arrayA, offsetA, lengthA, strideA, fromIndexA, vLast, vAfter, 0x20, 0x20});
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
                writeValue(array, stride, j + (contentLength * i), j + valueOffset[i]);
            }
        }
        return array;
    }

    final byte[] arrayA;
    final int offsetA;
    final int lengthA;
    final int strideA;
    final int fromIndexA;
    final int v0;
    final int v1;
    final int mask0;
    final int mask1;

    public TStringOpsIndexOfTwoConsecutiveTest(byte[] arrayA, int offsetA, int lengthA, int strideA, int fromIndexA, int v0, int v1, int mask0, int mask1) {
        super(ArrayIndexOfNode.class);
        this.arrayA = arrayA;
        this.offsetA = offsetA;
        this.lengthA = lengthA;
        this.strideA = strideA;
        this.fromIndexA = fromIndexA;
        this.v0 = v0;
        this.v1 = v1;
        this.mask0 = mask0;
        this.mask1 = mask1;
    }

    @Test
    public void testIndexOfTwoConsecutive() {
        testWithNative(getIndexOf2ConsecutiveWithStrideIntl(), null, DUMMY_LOCATION, arrayA, offsetA, lengthA, strideA, fromIndexA, v0, v1);
    }
}
