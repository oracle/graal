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

import static java.lang.Character.MAX_CODE_POINT;
import static java.lang.Character.MAX_HIGH_SURROGATE;
import static java.lang.Character.MAX_LOW_SURROGATE;
import static java.lang.Character.MIN_HIGH_SURROGATE;
import static java.lang.Character.MIN_LOW_SURROGATE;

import java.util.ArrayList;
import java.util.Arrays;

import org.graalvm.compiler.replacements.nodes.CalcStringAttributesNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TStringOpsCalcStringAttributesUTF16Test extends TStringOpsTest<CalcStringAttributesNode> {

    @Parameters(name = "{index}: args: {1}, {2}")
    public static Iterable<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offset = 20;
        int padding = 20;
        int stride = 1;
        int byteOffset = offset << stride;
        int large = 254 * 16;
        for (int length : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129, large - 1, large, large + 1}) {
            int arrayLength = offset + length + padding;
            byte[] arr = new byte[arrayLength << stride];
            for (int i = 0; i < offset; i++) {
                writeValue(arr, stride, i, 0xffff);
            }
            for (int i = offset; i < offset + length; i++) {
                writeValue(arr, stride, i, i % 0x7f);
            }
            for (int i = arrayLength - padding; i < arrayLength; i++) {
                writeValue(arr, stride, i, 0xffff);
            }
            ret.add(new Object[]{arr, byteOffset, length});
            for (int cp : new int[]{0x80, 0x100, 0xffff, 0x10000, MAX_CODE_POINT}) {
                byte[] arr2 = Arrays.copyOf(arr, arr.length);
                writeCodePoint(arr2, offset, cp, true);
                byte[] arr3 = Arrays.copyOf(arr, arr.length);
                writeCodePoint(arr3, offset + length - 1, cp, false);
                ret.add(new Object[]{arr2, byteOffset, length});
                ret.add(new Object[]{arr3, byteOffset, length});
            }
            for (int[] cps : new int[][]{
                            {0x80, 0x100},
                            {0x80, 0xffff},
                            {0x80, 0x10000},
                            {0x100, 0x10000},
                            {0xffff, 0x10000},
                            {0x80, MAX_CODE_POINT}}) {
                byte[] arr2 = Arrays.copyOf(arr, arr.length);
                writeCodePoint(arr2, offset, cps[0], true);
                writeCodePoint(arr2, offset + length - 1, cps[1], false);
                ret.add(new Object[]{arr2, byteOffset, length});
            }
            for (int[] cps : new int[][]{
                            {0x80, MIN_LOW_SURROGATE},
                            {0xffff, MAX_LOW_SURROGATE},
                            {0x100, MIN_HIGH_SURROGATE},
                            {0x7f, MAX_HIGH_SURROGATE},
                            {MIN_LOW_SURROGATE, MAX_CODE_POINT},
                            {MAX_LOW_SURROGATE, MAX_CODE_POINT},
                            {MIN_HIGH_SURROGATE, MAX_CODE_POINT},
                            {MAX_HIGH_SURROGATE, MAX_CODE_POINT}}) {
                byte[] arr2 = Arrays.copyOf(arr, arr.length);
                writeCodePoint(arr2, offset, cps[0], true);
                writeCodePoint(arr2, offset + length - 1, cps[1], false);
                ret.add(new Object[]{arr2, byteOffset, length});
                byte[] arr3 = Arrays.copyOf(arr, arr.length);
                writeCodePoint(arr3, offset, cps[1], true);
                writeCodePoint(arr3, offset + length - 1, cps[0], false);
                ret.add(new Object[]{arr3, byteOffset, length});
            }
            byte[] arr2 = Arrays.copyOf(arr, arr.length);
            int i = 0;
            while (i < length - 1) {
                i += writeCodePoint(arr2, offset + i, 0x10000 + i, true);
            }
            ret.add(new Object[]{arr2, byteOffset, length});
            byte[] arr3 = Arrays.copyOf(arr, arr.length);
            i = 1;
            while (i < length - 1) {
                i += writeCodePoint(arr3, offset + i, 0x10000 + i, true);
                if (i < length - 1) {
                    i += writeCodePoint(arr3, offset + i, MAX_LOW_SURROGATE, true);
                }
            }
            writeCodePoint(arr3, offset, MAX_LOW_SURROGATE, true);
            ret.add(new Object[]{arr3, byteOffset, length});
            byte[] arr4 = Arrays.copyOf(arr, arr.length);
            i = 1;
            while (i < length - 1) {
                i += writeCodePoint(arr4, offset + i, 0x10000 + i, true);
                if (i < length - 1) {
                    i += writeCodePoint(arr4, offset + i, MIN_HIGH_SURROGATE, true);
                }
            }
            writeCodePoint(arr4, offset, MIN_HIGH_SURROGATE, true);
            ret.add(new Object[]{arr4, byteOffset, length});
        }
        return ret;
    }

    private static int writeCodePoint(byte[] array, int index, int cp, boolean forward) {
        if (cp > 0xffff) {
            writeValue(array, 1, forward ? index : index - 1, Character.highSurrogate(cp));
            writeValue(array, 1, forward ? index + 1 : index, Character.lowSurrogate(cp));
            return 2;
        } else {
            writeValue(array, 1, index, cp);
            return 1;
        }
    }

    private final byte[] array;
    private final int offset;
    private final int length;

    public TStringOpsCalcStringAttributesUTF16Test(byte[] array, int offset, int length) {
        super(CalcStringAttributesNode.class);
        this.array = array;
        this.offset = offset;
        this.length = length;
    }

    @Test
    public void testValid() {
        testWithNative(getTStringOpsMethod("calcStringAttributesUTF16", Object.class, int.class, int.class, boolean.class), null, DUMMY_LOCATION, array, offset, length, true);
    }

    @Test
    public void testUnknown() {
        testWithNative(getTStringOpsMethod("calcStringAttributesUTF16", Object.class, int.class, int.class, boolean.class), null, DUMMY_LOCATION, array, offset, length, false);
    }

    @Test
    public void testUnknownC() {
        test(getTStringOpsMethod("calcStringAttributesUTF16C", char[].class, int.class, int.class), null, DUMMY_LOCATION, toCharArray(array), offset, length);
    }
}
