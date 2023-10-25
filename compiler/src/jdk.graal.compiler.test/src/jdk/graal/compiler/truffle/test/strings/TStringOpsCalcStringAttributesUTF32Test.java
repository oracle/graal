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

import static java.lang.Character.MAX_CODE_POINT;
import static java.lang.Character.MAX_HIGH_SURROGATE;
import static java.lang.Character.MAX_LOW_SURROGATE;
import static java.lang.Character.MIN_HIGH_SURROGATE;
import static java.lang.Character.MIN_LOW_SURROGATE;

import java.util.ArrayList;
import java.util.Arrays;

import jdk.graal.compiler.replacements.nodes.CalcStringAttributesNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TStringOpsCalcStringAttributesUTF32Test extends TStringOpsTest<CalcStringAttributesNode> {

    @Parameters(name = "{index}: args: {1}, {2}")
    public static Iterable<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offset = 20;
        int padding = 20;
        int stride = 2;
        int byteOffset = offset << stride;
        for (int length : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129}) {
            int arrayLength = offset + length + padding;
            byte[] arr = new byte[arrayLength << stride];
            for (int i = 0; i < offset; i++) {
                writeValue(arr, stride, i, MAX_CODE_POINT);
            }
            for (int i = offset; i < offset + length; i++) {
                writeValue(arr, stride, i, i % 0x7f);
            }
            for (int i = arrayLength - padding; i < arrayLength; i++) {
                writeValue(arr, stride, i, MAX_CODE_POINT);
            }
            ret.add(new Object[]{arr, byteOffset, length});
            for (int cp : new int[]{0x80, 0x100, MIN_LOW_SURROGATE, MAX_LOW_SURROGATE, MIN_HIGH_SURROGATE, MAX_HIGH_SURROGATE, 0xffff, 0x10000, MAX_CODE_POINT, MAX_CODE_POINT + 1, Integer.MAX_VALUE,
                            Integer.MIN_VALUE}) {
                byte[] arr2 = Arrays.copyOf(arr, arr.length);
                writeValue(arr2, stride, offset, cp);
                byte[] arr3 = Arrays.copyOf(arr, arr.length);
                writeValue(arr3, stride, offset + length - 1, cp);
                ret.add(new Object[]{arr2, byteOffset, length});
                ret.add(new Object[]{arr3, byteOffset, length});
            }
            for (int[] cps : new int[][]{
                            {0x80, 0x100},
                            {0x80, 0xffff},
                            {0x80, MIN_LOW_SURROGATE},
                            {0x100, MAX_LOW_SURROGATE},
                            {0xffff, MIN_HIGH_SURROGATE},
                            {0x10000, MAX_HIGH_SURROGATE},
                            {0x80, 0x10000},
                            {0x100, 0x10000},
                            {0xffff, 0x10000},
                            {0x80, MAX_CODE_POINT}}) {
                byte[] arr2 = Arrays.copyOf(arr, arr.length);
                writeValue(arr2, stride, offset, cps[0]);
                writeValue(arr2, stride, offset + length - 1, cps[1]);
                ret.add(new Object[]{arr2, byteOffset, length});
            }
        }
        return ret;
    }

    private final byte[] array;
    private final int offset;
    private final int length;

    public TStringOpsCalcStringAttributesUTF32Test(byte[] array, int offset, int length) {
        super(CalcStringAttributesNode.class);
        this.array = array;
        this.offset = offset;
        this.length = length;
    }

    @Test
    public void testUTF32() {
        testWithNative(getTStringOpsMethod("calcStringAttributesUTF32", Object.class, int.class, int.class), null, DUMMY_LOCATION, array, offset, length);
    }

    @Test
    public void testUTF32I() {
        test(getTStringOpsMethod("calcStringAttributesUTF32I", int[].class, int.class, int.class), null, DUMMY_LOCATION, toIntArray(array), offset, length);
    }
}
