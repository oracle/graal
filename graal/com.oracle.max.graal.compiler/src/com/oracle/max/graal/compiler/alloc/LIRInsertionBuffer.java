/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.alloc;

import java.util.*;

import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;

/**
 */
public final class LIRInsertionBuffer {

    /**
     * The lir list where ops of this buffer should be inserted later (null when uninitialized).
     */
    private List<LIRInstruction> lir;

    /**
     * List of insertion points. index and count are stored alternately:
     * indexAndCount[i * 2]: the index into lir list where "count" ops should be inserted
     * indexAndCount[i * 2 + 1]: the number of ops to be inserted at index
     */
    private final IntList indexAndCount;

    /**
     * The LIROps to be inserted.
     */
    private final List<LIRInstruction> ops;


    public LIRInsertionBuffer() {
        indexAndCount = new IntList(8);
        ops = new ArrayList<LIRInstruction>(8);
    }

    // must be called before using the insertion buffer
    public void init(List<LIRInstruction> lir) {
        assert !initialized() : "already initialized";
        assert indexAndCount.size() == 0 && ops.size() == 0;
        this.lir = lir;
    }

    public boolean initialized() {
        return lir != null;
    }

    public void move(int index, CiValue src, CiValue dst) {
        append(index, StandardOpcode.MOVE.create(dst, src));
    }

    public void finish() {
        if (ops.size() > 0) {
            int n = lir.size();
            // increase size of instructions list
            for (int i = 0; i < ops.size(); i++) {
                lir.add(null);
            }
            // insert ops from buffer into instructions list
            int opIndex = ops.size() - 1;
            int ipIndex = numberOfInsertionPoints() - 1;
            int fromIndex = n - 1;
            int toIndex = lir.size() - 1;
            while (ipIndex >= 0) {
                int index = indexAt(ipIndex);
                // make room after insertion point
                while (index < fromIndex) {
                    lir.set(toIndex--, lir.get(fromIndex--));
                }
                // insert ops from buffer
                for (int i = countAt(ipIndex); i > 0; i--) {
                    lir.set(toIndex--, ops.get(opIndex--));
                }
                ipIndex--;
            }
        }

        lir = null;
        indexAndCount.clear();
        ops.clear();
    }

    public List<LIRInstruction> lirList() {
        return lir;
    }

    private void appendNew(int index, int count) {
        indexAndCount.add(index);
        indexAndCount.add(count);
    }

    private void setCountAt(int i, int value) {
        indexAndCount.set((i << 1) + 1, value);
    }

    private int numberOfInsertionPoints() {
        return indexAndCount.size() >> 1;
    }

    private int indexAt(int i) {
        return indexAndCount.get((i << 1));
    }

    private int countAt(int i) {
        return indexAndCount.get((i << 1) + 1);
    }

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
        assert sum == ops.size() : "wrong total sum";
        return true;
    }
}
