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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.replacements.nodes.StringCodepointIndexToByteIndexNode;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class TStringOpsCodepointIndexToByteIndexUTF8Test extends TStringOpsTest<StringCodepointIndexToByteIndexNode> {

    private static final byte[][] PATTERNS = {
                    // valid
                    utf8Encode(0x00),
                    utf8Encode(0x7f),
                    utf8Encode(0x80),
                    utf8Encode(0x7ff),
                    utf8Encode(0x800),
                    utf8Encode(Character.MIN_SURROGATE - 1),
                    utf8Encode(Character.MAX_SURROGATE + 1),
                    utf8Encode(0xffff),
                    utf8Encode(0x10000),
                    utf8Encode(Character.MAX_CODE_POINT),
    };

    private static byte[] utf8Encode(int codepoint) {
        return new StringBuilder().appendCodePoint(codepoint).toString().getBytes(StandardCharsets.UTF_8);
    }

    @Parameters(name = "{index}: args: {1}, {2}")
    public static Iterable<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offset = 20;
        int padding = 20;
        for (int length : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129}) {
            byte[] arr = new byte[offset + length + padding];
            for (int i = 0; i < offset; i++) {
                arr[i] = (byte) 0xff;
            }
            for (int i = offset; i < offset + length; i++) {
                arr[i] = (byte) (i % 0x7f);
            }
            for (int i = arr.length - padding; i < arr.length; i++) {
                arr[i] = (byte) 0xff;
            }
            for (byte[] pattern : PATTERNS) {
                if (pattern.length > length) {
                    continue;
                }
                int endPos = offset + length - pattern.length;
                int middlePos = offset + (length / 2) - pattern.length;
                for (int[] positions : new int[][]{new int[]{offset}, new int[]{endPos}, new int[]{middlePos}, new int[]{offset, endPos}, new int[]{offset, middlePos, endPos}, new int[]{-1}}) {
                    if (positions.length > 1 && positions[1] - positions[0] < pattern.length) {
                        continue;
                    }
                    byte[] sut = Arrays.copyOf(arr, arr.length);
                    int codepoints = length;
                    if (positions[0] == -1) {
                        for (int i = offset + pattern.length; i < offset + length; i += pattern.length) {
                            System.arraycopy(pattern, 0, sut, i - pattern.length, pattern.length);
                            codepoints -= pattern.length - 1;
                        }
                    } else {
                        for (int pos : positions) {
                            System.arraycopy(pattern, 0, sut, pos, pattern.length);
                            codepoints -= pattern.length - 1;
                        }
                    }
                    for (int index : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129}) {
                        ret.add(new Object[]{sut, offset, length, index});
                        if (index > codepoints) {
                            break;
                        }
                    }
                }
            }
        }
        // GR-62332
        ret.add(new Object[]{new byte[]{(byte) 0xef, (byte) 0xbf, (byte) 0xbf, 1, 2, 3, 4, 5, 6, 7, (byte) 0xf4, (byte) 0x8f, (byte) 0xbf, (byte) 0xbf, (byte) 0xbf}, 14, 0, 0});
        return ret;
    }

    private final Object array;
    private final long offset;
    private final int length;
    private final int index;

    public TStringOpsCodepointIndexToByteIndexUTF8Test(Object array, int offset, int length, int index) {
        super(StringCodepointIndexToByteIndexNode.class);
        this.array = array;
        this.offset = offset + byteArrayBaseOffset();
        this.length = length;
        this.index = index;
    }

    @Test
    public void testUtf8() {
        Assume.assumeTrue(getArchitecture() instanceof AMD64);
        ResolvedJavaMethod method = getTStringOpsMethod("codePointIndexToByteIndexUTF8Valid", byte[].class, long.class, int.class, int.class);
        testWithNative(method, null, DUMMY_LOCATION, array, offset, length, index);
    }
}
