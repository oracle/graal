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
package org.graalvm.compiler.truffle.test.strings;

import java.util.ArrayList;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.replacements.nodes.ArrayCopyWithConversionsNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class TStringOpsCopyTest extends TStringOpsTest<ArrayCopyWithConversionsNode> {

    @Parameters(name = "{index}: args: {1}, {2}, {3}, {4}, {5}")
    public static Iterable<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offsetBytes = 20;
        int contentLength = 129;
        int paddingBytes = 20;
        for (int strideA = 0; strideA <= 2; strideA++) {
            byte[] src = new byte[offsetBytes + (contentLength << strideA) + paddingBytes];
            for (int i = 0; i < offsetBytes; i++) {
                src[i] = (byte) 0xff;
            }
            int i = (offsetBytes >> strideA);
            for (int n = 0; n < 2; n++) {
                for (int c = '0'; c <= '9'; c++) {
                    writeValue(src, strideA, i++, c);
                }
                for (int c = 'A'; c <= 'Z'; c++) {
                    writeValue(src, strideA, i++, c);
                }
                for (int c = 'a'; c <= 'z'; c++) {
                    writeValue(src, strideA, i++, c);
                }
            }
            writeValue(src, strideA, i++, ';');
            writeValue(src, strideA, i++, ':');
            writeValue(src, strideA, i++, '_');
            writeValue(src, strideA, i++, '?');
            writeValue(src, strideA, i++, '!');
            assert i == contentLength + (offsetBytes >> strideA);
            for (int j = src.length - paddingBytes; j < src.length; j++) {
                src[j] = (byte) 0xff;
            }
            for (int strideB = 0; strideB <= 2; strideB++) {
                for (int fromIndexA : new int[]{0, 1, 7, 15}) {
                    for (int fromIndexB : new int[]{0, 1, 7, 15}) {
                        for (int length : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129}) {
                            if (fromIndexA + length < contentLength && fromIndexB + length < contentLength) {
                                int offsetA = offsetBytes + (fromIndexA << strideA);
                                int offsetB = offsetBytes + (fromIndexB << strideB);
                                ret.add(new Object[]{src, offsetA, strideA, offsetB, strideB, length});
                            }
                        }
                    }
                }
            }
        }
        return ret;
    }

    private final Object arrayA;
    private final int offsetA;
    private final int strideA;
    private final int offsetB;
    private final int strideB;
    private final int lengthCPY;

    public TStringOpsCopyTest(
                    Object arrayA,
                    int offsetA, int strideA,
                    int offsetB, int strideB, int lengthCPY) {
        super(ArrayCopyWithConversionsNode.class);
        this.arrayA = arrayA;
        this.offsetA = offsetA;
        this.strideA = strideA;
        this.offsetB = offsetB;
        this.strideB = strideB;
        this.lengthCPY = lengthCPY;
    }

    @Test
    public void testCopy() throws ClassNotFoundException {
        ArgSupplier arrayB = () -> new byte[128 + offsetB + (lengthCPY << strideB) + 128];
        test(getArrayCopyWithStride(), null, DUMMY_LOCATION, arrayA, offsetA, strideA, 0, arrayB, offsetB, strideB, 0, lengthCPY);
        if (strideA == 1) {
            test(getArrayCopyWithStrideCB(), null, DUMMY_LOCATION, toCharArray((byte[]) arrayA), offsetA, arrayB, offsetB, strideB, lengthCPY);
        } else if (strideA == 2) {
            test(getArrayCopyWithStrideIB(), null, DUMMY_LOCATION, toIntArray((byte[]) arrayA), offsetA, arrayB, offsetB, strideB, lengthCPY);
        }
    }

    private static char[] toCharArray(byte[] array) {
        char[] ret = new char[array.length >> 1];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = (char) readValue(array, 1, i);
        }
        return ret;
    }

    private static int[] toIntArray(byte[] array) {
        int[] ret = new int[array.length >> 2];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = (char) readValue(array, 2, i);
        }
        return ret;
    }

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (method.getName().equals("arraycopyWithStrideIntl")) {
            return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(method);
        }
        return super.bytecodeParserShouldInlineInvoke(b, method, args);
    }
}
