/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsNode;

@RunWith(Parameterized.class)
public class TStringOpsByteSwapTest extends TStringOpsTest<ArrayCopyWithConversionsNode> {

    @Parameters(name = "{index}: args: {1}, {2}, {3}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offsetBytes = 20;
        int contentLength = 129;
        byte[] src = new byte[1024];
        for (int i = 0; i < src.length; i++) {
            src[i] = (byte) i;
        }
        for (int stride = 1; stride <= 2; stride++) {
            for (int fromIndexA : new int[]{0, 1, 7, 15}) {
                for (int fromIndexB : new int[]{0, 1, 7, 15}) {
                    for (int length : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129}) {
                        if (fromIndexA + length < contentLength && fromIndexB + length < contentLength) {
                            int offsetA = offsetBytes + (fromIndexA << stride);
                            int offsetB = offsetBytes + (fromIndexB << stride);
                            ret.add(new Object[]{src, offsetA, offsetB, length});
                        }
                    }
                }
            }
        }
        return ret;
    }

    final byte[] arrayA;
    final long offsetA;
    final long offsetB;
    final int lengthCPY;

    public TStringOpsByteSwapTest(byte[] arrayA, int offsetA, int offsetB, int lengthCPY) {
        super(ArrayCopyWithConversionsNode.class);
        this.arrayA = arrayA;
        this.offsetA = offsetA + byteArrayBaseOffset();
        this.offsetB = offsetB + byteArrayBaseOffset();
        this.lengthCPY = lengthCPY;
    }

    @Test
    public void testByteSwapS1() {
        ArgSupplier arrayB = () -> new byte[(int) (128 + offsetB + (lengthCPY << 1) + 128)];
        testWithNativeExcept(getByteSwapS1(), null, 1 << 3, DUMMY_LOCATION, arrayA, offsetA, arrayB, offsetB + 128, lengthCPY);
    }

    @Test
    public void testByteSwapS2() {
        ArgSupplier arrayB = () -> new byte[(int) (128 + offsetB + (lengthCPY << 2) + 128)];
        testWithNativeExcept(getByteSwapS2(), null, 1 << 3, DUMMY_LOCATION, arrayA, offsetA, arrayB, offsetB + 128, lengthCPY);
    }

    @Override
    protected void checkIntrinsicNode(ArrayCopyWithConversionsNode node) {
        Assert.assertTrue(node.isReverseBytes());
    }
}
