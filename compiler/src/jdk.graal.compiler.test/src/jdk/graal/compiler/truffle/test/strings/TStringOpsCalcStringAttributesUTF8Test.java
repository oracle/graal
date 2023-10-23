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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import jdk.graal.compiler.replacements.nodes.CalcStringAttributesNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.profiles.InlinedConditionProfile;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class TStringOpsCalcStringAttributesUTF8Test extends TStringOpsTest<CalcStringAttributesNode> {

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

                    // invalid
                    byteArray(0x80),
                    byteArray(0b11000000),
                    byteArray(0b11000000, 0x80, 0x80),
                    byteArray(0b11100000),
                    byteArray(0b11100000, 0x80),
                    byteArray(0b11100000, 0x80, 0x80, 0x80),
                    byteArray(0b11110000),
                    byteArray(0b11110000, 0x80, 0x80),
                    byteArray(0b11110000, 0x80, 0x80, 0x80, 0x80),
                    byteArray(0b11111000),
                    byteArray(0b11111000, 0x80, 0x80, 0x80, 0x80),
                    byteArray(0b11111100),
                    byteArray(0b11111100, 0x80, 0x80, 0x80, 0x80, 0x80),
                    byteArray(0b11111110),
                    byteArray(0b11111111),
                    byteArray(0xed, 0xb0, 0x80),
                    byteArray(0xed, 0xbf, 0xbf),
                    byteArray(0xed, 0xa0, 0x80),
                    byteArray(0xed, 0xaf, 0xbf),
    };

    private static byte[] utf8Encode(int codepoint) {
        return new StringBuilder().appendCodePoint(codepoint).toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] byteArray(int... values) {
        byte[] ret = new byte[values.length];
        for (int i = 0; i < ret.length; i++) {
            assert 0 <= values[i] && values[i] <= 0xff;
            ret[i] = (byte) values[i];
        }
        return ret;
    }

    @Parameters(name = "{index}: args: {1}, {2}")
    public static Iterable<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offset = 20;
        int padding = 20;
        int large = 254 * 32;
        for (int length : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129, large - 1, large, large + 1}) {
            byte[] arr = new byte[offset + length + padding];
            for (int i = 0; i < offset; i++) {
                arr[i] = (byte) 0x7f;
            }
            for (int i = offset; i < offset + length; i++) {
                arr[i] = (byte) (i % 0x7f);
            }
            for (int i = arr.length - padding; i < arr.length; i++) {
                arr[i] = (byte) 0x7f;
            }
            ret.add(new Object[]{arr, offset, length});
            for (byte[] pattern : PATTERNS) {
                if (pattern.length > length) {
                    continue;
                }
                int endPos = offset + length - pattern.length;
                for (int[] positions : new int[][]{new int[]{offset}, new int[]{endPos}, new int[]{offset, endPos}}) {
                    byte[] sut = Arrays.copyOf(arr, arr.length);
                    for (int pos : positions) {
                        System.arraycopy(pattern, 0, sut, pos, pattern.length);
                    }
                    ret.add(new Object[]{sut, offset, length});
                }
            }
        }
        return ret;
    }

    private final byte[] array;
    private final int offset;
    private final int length;

    public TStringOpsCalcStringAttributesUTF8Test(byte[] array, int offset, int length) {
        super(CalcStringAttributesNode.class);
        this.array = array;
        this.offset = offset;
        this.length = length;
    }

    @Test
    public void testUtf8() {
        ResolvedJavaMethod method = getTStringOpsMethod("calcStringAttributesUTF8", Object.class, int.class, int.class, boolean.class, boolean.class, InlinedConditionProfile.class);
        testWithNative(method, null, DUMMY_LOCATION, array, offset, length, true, false, InlinedConditionProfile.getUncached());
        testWithNative(method, null, DUMMY_LOCATION, array, offset, length, false, false, InlinedConditionProfile.getUncached());
    }
}
