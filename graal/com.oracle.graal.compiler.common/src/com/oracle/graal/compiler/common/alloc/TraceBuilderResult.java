/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.alloc;

import java.util.List;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;

public final class TraceBuilderResult<T extends AbstractBlockBase<T>> {
    private final List<List<T>> traces;
    private final int[] blockToTrace;

    TraceBuilderResult(List<List<T>> traces, int[] blockToTrace) {
        this.traces = traces;
        this.blockToTrace = blockToTrace;
    }

    public int getTraceForBlock(AbstractBlockBase<?> block) {
        return blockToTrace[block.getId()];
    }

    public List<List<T>> getTraces() {
        return traces;
    }

    public boolean incomingEdges(int traceNr) {
        List<T> trace = getTraces().get(traceNr);
        return incomingEdges(traceNr, trace);
    }

    public boolean incomingSideEdges(int traceNr) {
        List<T> trace = getTraces().get(traceNr);
        if (trace.size() <= 1) {
            return false;
        }
        return incomingEdges(traceNr, trace.subList(1, trace.size()));
    }

    private boolean incomingEdges(int traceNr, List<T> trace) {
        /* TODO (je): not efficient. find better solution. */
        for (T block : trace) {
            for (T pred : block.getPredecessors()) {
                if (getTraceForBlock(pred) != traceNr) {
                    return true;
                }
            }
        }
        return false;
    }

}
