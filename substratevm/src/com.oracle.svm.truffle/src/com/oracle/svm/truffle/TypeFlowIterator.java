/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

public abstract class TypeFlowIterator {

    public static class WorkListEntry {
        protected final TypeFlow<?> flow;
        protected final WorkListEntry reason;

        protected WorkListEntry(TypeFlow<?> flow, WorkListEntry reason) {
            this.flow = flow;
            this.reason = reason;
        }
    }

    private final BigBang bb;
    private final Deque<WorkListEntry> worklist;
    private final Set<TypeFlow<?>> processed;

    protected TypeFlowIterator(BigBang bb) {
        this.bb = bb;
        this.worklist = new ArrayDeque<>();
        this.processed = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    public final void addRoot(TypeFlow<?> rootFlow) {
        add(rootFlow, null);
    }

    protected abstract boolean process(TypeFlow<?> flow);

    public final void finish() {
        while (!worklist.isEmpty()) {
            WorkListEntry entry = worklist.removeFirst();

            if (process(entry.flow)) {
                for (TypeFlow<?> use : entry.flow.getUses()) {
                    add(use, entry);
                }
            }
        }
    }

    private void add(TypeFlow<?> flow, WorkListEntry reason) {
        if (processed.contains(flow)) {
            return;
        }

        processed.add(flow);
        WorkListEntry entry = new WorkListEntry(flow, reason);
        worklist.add(entry);

        if (!flow.isClone() && flow.getSource() instanceof ValueNode) {
            AnalysisMethod method = (AnalysisMethod) ((ValueNode) flow.getSource()).graph().method();
            for (MethodFlowsGraph methodFlow : method.getTypeFlow().getMethodContextFlows().values()) {
                TypeFlow<?> curClone = methodFlow.lookupCloneOf(bb, flow);
                add(curClone, entry);
            }
        }
    }
}
