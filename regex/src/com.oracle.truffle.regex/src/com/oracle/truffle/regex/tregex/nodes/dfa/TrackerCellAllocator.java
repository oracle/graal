/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.tregex.nodes.dfa;

import org.graalvm.collections.EconomicMap;

/**
 * Helper to keep track of the minimum amount of space required to track one counter in DFA mode.
 */
public final class TrackerCellAllocator {
    private final int reservedRange;
    private int counter;
    private int totalSize;

    public TrackerCellAllocator(int reservedRange) {
        this.reservedRange = reservedRange;
        counter = reservedRange;
        totalSize = reservedRange;
    }

    public void resetTemp() {
        this.counter = reservedRange;
    }

    public int allocTemp() {
        int i = counter;
        counter++;
        if (counter > totalSize) {
            totalSize = counter;
        }
        return i;
    }

    public int getTotalSize() {
        return totalSize;
    }

    /**
     * Builder, tracks how many fixed cells are needed. Is also used to remap the nfa state ids, to
     * continuous identifiers starting from 0.
     */
    public static final class Builder {
        private final EconomicMap<Integer, Integer> fixedMapping = EconomicMap.create();

        public int registerAndGet(int id) {
            if (!fixedMapping.containsKey(id)) {
                fixedMapping.put(id, fixedMapping.size());
            }
            return fixedMapping.get(id);
        }

        public TrackerCellAllocator build() {
            return new TrackerCellAllocator(fixedMapping.size());
        }
    }
}
