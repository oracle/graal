/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.bytecode;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoNode;

/**
 * lightweight map from BCI to array index. The contract is easy, upon lookup, returns the index of
 * the key queried if it is there, or the index of the last element element smaller than the key
 * otherwise.
 * <p>
 * If walking through BCI without jumping (ie: using {@link BytecodeStream#nextBCI(int)}, calling
 * {@link #checkNext(int, int)} does a O(1) lookup, given the previous index.
 * <p>
 * Lookups done after a jump instruction is done through binary search, yielding a O(log) query.
 */
public class MapperBCI extends EspressoNode {
    @CompilationFinal(dimensions = 1) //
    private final int[] bcis;

    private final int length;

    private final boolean wasSorted;

    public MapperBCI(LineNumberTableAttribute lineNumberTable) {
        this.length = lineNumberTable.getEntries().size();
        this.bcis = new int[length];
        int i = 0;
        int prev = 0;
        boolean sort = false;
        for (LineNumberTableAttribute.Entry entry : lineNumberTable.getEntries()) {
            int bci = entry.getBCI();
            if (i > 0 && bci < prev) {
                sort = true;
            }
            prev = bci;
            bcis[i++] = bci;
        }
        if (sort) {
            Arrays.sort(bcis);
        }
        this.wasSorted = !sort;
    }

    public int[] getBcis() {
        return bcis;
    }

    public int getLength() {
        return length;
    }

    /**
     * Use this when initializing the array supported by this Mapper.
     * 
     * @param curIndex loop index if initialization.
     * @param bci Bci corresponding to current loop.
     * @return The index at which to put the object, in the array supported by this mapper.
     */
    public int initIndex(int curIndex, int bci) {
        if (wasSorted) {
            return curIndex;
        } else {
            return lookup(bci);
        }
    }

    private int lookup(int targetBCI) {
        int res = slowLookup(targetBCI, 0, length);
        if (res >= 0) {
            return res;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere();
    }

    public int lookup(int curIndex, int curBCI, int targetBCI) {
        int res;
        int start;
        int end;
        if (curBCI < targetBCI) {
            start = curIndex;
            end = length;
        } else {
            start = 0;
            end = curIndex + 1;
        }
        res = slowLookup(targetBCI, start, end);
        if (res >= 0) {
            return res;
        }
        return -res - 2;
    }

    public int checkNext(int curIndex, int targetBCI) {
        if (curIndex < length - 1 && targetBCI >= bcis[curIndex + 1]) {
            return curIndex + 1;
        }
        return curIndex;
    }

    /**
     * inlined binary search. No bounds checks.
     * 
     * @see Arrays#binarySearch(int[], int, int, int)
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE)
    private int slowLookup(int targetBCI, int start, int end) {
        // Our usage should not see an out of bounds
        int low = start;
        int high = end - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = bcis[mid];

            if (midVal < targetBCI) {
                low = mid + 1;
            } else if (midVal > targetBCI) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);  // key not found.
    }

}
