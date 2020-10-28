/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.analysis.graph;

import java.util.Arrays;

import com.oracle.truffle.espresso.analysis.Util;

public class EspressoBlock implements LinkedBlock {
    public static final int[] EMPTY_ID_ARRAY = new int[0];

    private final EspressoExecutionGraph graph;
    private final int id;
    private final int start;
    private final int end;
    private final int last;
    private final int[] successors;
    private final int[] predecessors;

    public EspressoExecutionGraph graph() {
        return graph;
    }

    public EspressoBlock(EspressoExecutionGraph graph, int id, int start, int end, int last, int[] successors, int[] predecessors) {
        this.graph = graph;
        this.id = id;
        this.start = start;
        this.end = end;
        this.last = last;
        this.successors = successors;
        this.predecessors = predecessors;
        assert Util.assertNoDupe(successors);
        assert Util.assertNoDupe(predecessors);
    }

    @Override
    public final int start() {
        return start;
    }

    @Override
    public final int end() {
        return end;
    }

    @Override
    public int lastBCI() {
        return last;
    }

    @Override
    public final boolean isLeaf() {
        return successorsID().length == 0;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public int[] successorsID() {
        return successors;
    }

    @Override
    public int[] predecessorsID() {
        return predecessors;
    }

    @Override
    public String toString() {
        return "B" + id + "[" + start + ", " + end + "] to: " + Arrays.toString(successorsID());
    }

}
