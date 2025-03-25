/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.replacements.nodes.ArrayRegionCompareToNode;

@RunWith(Parameterized.class)
public class TStringOpsCompareTest extends TStringOpsTest<ArrayRegionCompareToNode> {

    @Parameters(name = "{index}: offset: {1}, {4}, stride: {2}, {5}, length: {6}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offset = 20;
        int padding = 20;
        for (int strideA = 0; strideA < 3; strideA++) {
            int contentLength = 129;
            byte[] arrayA = createTestArray(offset, padding, strideA, contentLength);
            for (int strideB = 0; strideB < 3; strideB++) {
                for (int iFromIndex = 0; iFromIndex < 3; iFromIndex++) {
                    int offsetA = offset << strideA;
                    int offsetB = offset << strideB;
                    int fromIndexA = contentLength * iFromIndex;
                    for (int lengthCMP : new int[]{1, 0, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129}) {
                        for (int fromIndexOffset : new int[]{-1, 0, 1}) {
                            if (fromIndexOffset == -1 && fromIndexA == 0 || fromIndexOffset == 1 && fromIndexA == contentLength * 2 && lengthCMP == 129) {
                                continue;
                            }
                            int fromIndexB = (contentLength * iFromIndex) + fromIndexOffset;
                            for (int diffPos : new int[]{-1, 0, 1, 8, 16, 32, lengthCMP - 1}) {
                                byte[] arrayB = new byte[(offset + (contentLength * 3) + padding) << strideB];
                                for (int i = 0; i < lengthCMP; i++) {
                                    writeValue(arrayB, strideB, offset + fromIndexB + i, readValue(arrayA, strideA, offset + fromIndexA + i));
                                }
                                if (diffPos >= 0 && diffPos < lengthCMP) {
                                    writeValue(arrayB, strideB, offset + fromIndexB + diffPos, readValue(arrayA, strideA, offset + fromIndexA + diffPos) - 1);
                                }
                                ret.add(new Object[]{
                                                arrayA, offsetA + (fromIndexA << strideA), strideA,
                                                arrayB, offsetB + (fromIndexB << strideB), strideB, lengthCMP});
                            }
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
    final long offsetA;
    final int strideA;
    final byte[] arrayB;
    final long offsetB;
    final int strideB;
    final int lengthCMP;

    public TStringOpsCompareTest(
                    byte[] arrayA, int offsetA, int strideA,
                    byte[] arrayB, int offsetB, int strideB, int lengthCMP) {
        super(ArrayRegionCompareToNode.class);
        this.arrayA = arrayA;
        this.offsetA = offsetA + byteArrayBaseOffset();
        this.strideA = strideA;
        this.arrayB = arrayB;
        this.offsetB = offsetB + byteArrayBaseOffset();
        this.strideB = strideB;
        this.lengthCMP = lengthCMP;
    }

    @Test
    public void testMemCmp() {
        testWithNative(getMemcmpWithStrideIntl(), null, DUMMY_LOCATION,
                        arrayA, offsetA, strideA,
                        arrayB, offsetB, strideB, lengthCMP);
    }

    @Override
    protected void checkIntrinsicNode(ArrayRegionCompareToNode node) {
        Assert.assertTrue(node.getDirectStubCallIndex() < 0);
    }
}
