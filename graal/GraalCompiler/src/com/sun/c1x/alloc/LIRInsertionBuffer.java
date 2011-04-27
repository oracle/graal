/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.alloc;

import java.util.*;

import com.sun.c1x.lir.*;
import com.sun.c1x.util.*;
import com.sun.cri.ci.*;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class LIRInsertionBuffer {

    private LIRList lir; // the lir list where ops of this buffer should be inserted later (null when uninitialized)

    // list of insertion points. index and count are stored alternately:
    // indexAndCount[i * 2]: the index into lir list where "count" ops should be inserted
    // indexAndCount[i * 2 + 1]: the number of ops to be inserted at index
    private final IntList indexAndCount;

    // the LIROps to be inserted
    private final List<LIRInstruction> ops;

    private void appendNew(int index, int count) {
        indexAndCount.add(index);
        indexAndCount.add(count);
    }

    private void setCountAt(int i, int value) {
        indexAndCount.set((i << 1) + 1, value);
    }

    LIRInsertionBuffer() {
        ops = new ArrayList<LIRInstruction>(8);
        indexAndCount = new IntList(8);
    }

    // must be called before using the insertion buffer
    void init(LIRList lir) {
        assert !initialized() : "already initialized";
        this.lir = lir;
        indexAndCount.clear();
        ops.clear();
    }

    boolean initialized() {
        return lir != null;
    }

    // called automatically when the buffer is appended to the LIRList
    public void finish() {
        lir = null;
    }

    // accessors
    public LIRList lirList() {
        return lir;
    }

    public int numberOfInsertionPoints() {
        return indexAndCount.size() >> 1;
    }

    public int indexAt(int i) {
        return indexAndCount.get((i << 1));
    }

    public int countAt(int i) {
        return indexAndCount.get((i << 1) + 1);
    }

    public int numberOfOps() {
        return ops.size();
    }

    public LIRInstruction opAt(int i) {
        return ops.get(i);
    }

    void move(int index, CiValue src, CiValue dst, LIRDebugInfo info) {
        append(index, new LIROp1(LIROpcode.Move, src, dst, dst.kind, info));
    }

    // Implementation of LIRInsertionBuffer

    private void append(int index, LIRInstruction op) {
        assert indexAndCount.size() % 2 == 0 : "must have a count for each index";

        int i = numberOfInsertionPoints() - 1;
        if (i < 0 || indexAt(i) < index) {
            appendNew(index, 1);
        } else {
            assert indexAt(i) == index : "can append LIROps in ascending order only";
            assert countAt(i) > 0 : "check";
            setCountAt(i, countAt(i) + 1);
        }
        ops.add(op);

        assert verify();
    }

    private boolean verify() {
        int sum = 0;
        int prevIdx = -1;

        for (int i = 0; i < numberOfInsertionPoints(); i++) {
            assert prevIdx < indexAt(i) : "index must be ordered ascending";
            sum += countAt(i);
        }
        assert sum == numberOfOps() : "wrong total sum";
        return true;
    }
}
