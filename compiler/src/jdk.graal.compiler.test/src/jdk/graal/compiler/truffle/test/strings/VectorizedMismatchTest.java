/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.replacements.nodes.VectorizedMismatchNode;

public class VectorizedMismatchTest extends TStringOpsTest<VectorizedMismatchNode> {

    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offset = 20;
        int padding = 20;
        for (int stride = 0; stride < 4; stride++) {
            int contentLength = 129;
            byte[] arrayA = createTestArray(offset, padding, stride, contentLength);
            for (int iFromIndex = 0; iFromIndex < 3; iFromIndex++) {
                int fromIndexA = contentLength * iFromIndex;
                for (int length : new int[]{1, 0, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129}) {
                    for (int fromIndexOffset : new int[]{-1, 0, 1}) {
                        if (fromIndexOffset == -1 && fromIndexA == 0 || fromIndexOffset == 1 && fromIndexA == contentLength * 2 && length == 129) {
                            continue;
                        }
                        int fromIndexB = (contentLength * iFromIndex) + fromIndexOffset;
                        for (int diffPos : new int[]{-1, 0, 1, 7, 8, 15, 16, 31, 32, length - 1}) {
                            byte[] arrayB = new byte[(offset + (contentLength * 4) + padding) << stride];
                            for (int i = 0; i < length; i++) {
                                writeValue(arrayB, stride, offset + fromIndexB + i, readValue(arrayA, stride, offset + fromIndexA + i));
                            }
                            if (diffPos >= 0 && diffPos < length) {
                                writeValue(arrayB, stride, offset + fromIndexB + diffPos, readValue(arrayA, stride, offset + fromIndexA + diffPos) - 1);
                            }
                            ret.add(new Object[]{
                                            arrayA, offset + fromIndexA,
                                            arrayB, offset + fromIndexB, length, stride});
                        }
                    }
                }
            }
        }
        return ret;
    }

    private static byte[] createTestArray(int offset, int padding, int stride, int contentLength) {
        byte[] array = new byte[(offset + (contentLength * 4) + padding) << stride];
        int[] valueOffset = {0, 0x1000, 0x10_0000, 0x1000_0000};
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < contentLength; j++) {
                writeValue(array, stride, j + (contentLength * i), j + valueOffset[i]);
            }
        }
        return array;
    }

    public VectorizedMismatchTest() {
        super(VectorizedMismatchNode.class);
    }

    @Test
    public void testVectorizedMismatch() {
        testParameterized(data(), this::testVectorizedMismatchCase);
    }

    private void testVectorizedMismatchCase(Object[] args) {
        byte[] arrayA = (byte[]) args[0];
        int offsetA = (int) args[1];
        byte[] arrayB = (byte[]) args[2];
        int offsetB = (int) args[3];
        int length = (int) args[4];
        int stride = (int) args[5];

        switch (stride) {
            case 0:
                test("vectorizedMismatch0",
                                arrayA, offsetA,
                                arrayB, offsetB, length);
                break;
            case 1:
                test("vectorizedMismatch1",
                                toCharArray(arrayA), offsetA,
                                toCharArray(arrayB), offsetB, length);
                break;
            case 2:
                test("vectorizedMismatch2",
                                toIntArray(arrayA), offsetA,
                                toIntArray(arrayB), offsetB, length);
                break;
            case 3:
                test("vectorizedMismatch3",
                                toLongArray(arrayA), offsetA,
                                toLongArray(arrayB), offsetB, length);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static int vectorizedMismatch0(
                    byte[] arrayA, int offsetA,
                    byte[] arrayB, int offsetB, int length) {
        return Arrays.mismatch(arrayA, offsetA, offsetA + length, arrayB, offsetB, offsetB + length);
    }

    public static int vectorizedMismatch1(
                    char[] arrayA, int offsetA,
                    char[] arrayB, int offsetB, int length) {
        return Arrays.mismatch(arrayA, offsetA, offsetA + length, arrayB, offsetB, offsetB + length);
    }

    public static int vectorizedMismatch2(
                    int[] arrayA, int offsetA,
                    int[] arrayB, int offsetB, int length) {
        return Arrays.mismatch(arrayA, offsetA, offsetA + length, arrayB, offsetB, offsetB + length);
    }

    public static int vectorizedMismatch3(
                    long[] arrayA, int offsetA,
                    long[] arrayB, int offsetB, int length) {
        return Arrays.mismatch(arrayA, offsetA, offsetA + length, arrayB, offsetB, offsetB + length);
    }
}
