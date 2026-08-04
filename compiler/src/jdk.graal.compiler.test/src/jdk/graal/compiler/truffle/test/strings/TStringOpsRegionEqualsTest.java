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

import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsNode;

public class TStringOpsRegionEqualsTest extends TStringOpsTest<ArrayRegionEqualsNode> {

    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offset = 20;
        int padding = 20;
        for (int strideA = 0; strideA < 3; strideA++) {
            int contentLength = 129;
            byte[] arrayA = createTestArray(offset, padding, strideA, contentLength);
            for (int strideB = 0; strideB < 3; strideB++) {
                byte[] arrayB = createTestArray(offset, padding, strideB, contentLength);
                for (int iFromIndex = 0; iFromIndex < 3; iFromIndex++) {
                    int offsetA = offset << strideA;
                    int offsetB = offset << strideB;
                    int lengthA = contentLength * 3;
                    int lengthB = contentLength * 3;
                    int fromIndexA = contentLength * iFromIndex;
                    for (int lengthCMP : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129}) {
                        for (int fromIndexOffset : new int[]{-1, 0, 1}) {
                            if (fromIndexOffset == -1 && fromIndexA == 0 || fromIndexOffset == 1 && fromIndexA == contentLength * 2 && lengthCMP == 129) {
                                continue;
                            }
                            int fromIndexB = (contentLength * iFromIndex) + fromIndexOffset;
                            ret.add(new Object[]{DUMMY_LOCATION,
                                            arrayA, offsetA + byteArrayBaseOffset(), lengthA, strideA, fromIndexA,
                                            arrayB, offsetB + byteArrayBaseOffset(), lengthB, strideB, fromIndexB, null, lengthCMP});
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

    int strideA;
    int strideB;
    int lengthCMP;

    public TStringOpsRegionEqualsTest() {
        super(ArrayRegionEqualsNode.class);
    }

    protected void setTestCase(Object[] args) {
        strideA = (int) args[4];
        strideB = (int) args[9];
        lengthCMP = (int) args[12];
    }

    @Test
    public void testRegionEquals() {
        testParameterized(data(), this::testRegionEqualsCase);
    }

    private void testRegionEqualsCase(Object[] args) {
        testWithNativeExcept(getRegionEqualsWithOrMaskWithStride(), null, 1L << 11, args);
    }

    @Override
    protected void checkIntrinsicNode(ArrayRegionEqualsNode node) {
        Assert.assertTrue(node.getDirectStubCallIndex() < 0);
    }
}
