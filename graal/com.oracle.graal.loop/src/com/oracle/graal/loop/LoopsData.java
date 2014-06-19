/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.loop;

import static com.oracle.graal.graph.util.CollectionsAccess.*;

import java.util.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;

public class LoopsData {

    private Map<Loop<Block>, LoopEx> loopToEx = newIdentityMap();
    private Map<LoopBeginNode, LoopEx> loopBeginToEx = newNodeIdentityMap();
    private ControlFlowGraph cfg;

    public LoopsData(final StructuredGraph graph) {
        try (Scope s = Debug.scope("ControlFlowGraph")) {
            cfg = ControlFlowGraph.compute(graph, true, true, true, true);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        for (Loop<Block> loop : cfg.getLoops()) {
            LoopEx ex = new LoopEx(loop, this);
            loopToEx.put(loop, ex);
            loopBeginToEx.put(ex.loopBegin(), ex);
        }
    }

    public LoopEx loop(Loop<?> loop) {
        return loopToEx.get(loop);
    }

    public LoopEx loop(LoopBeginNode loopBegin) {
        return loopBeginToEx.get(loopBegin);
    }

    public Collection<LoopEx> loops() {
        return loopToEx.values();
    }

    public List<LoopEx> outerFirst() {
        ArrayList<LoopEx> loops = new ArrayList<>(loops());
        Collections.sort(loops, new Comparator<LoopEx>() {

            @Override
            public int compare(LoopEx o1, LoopEx o2) {
                return o1.loop().getDepth() - o2.loop().getDepth();
            }
        });
        return loops;
    }

    public List<LoopEx> innerFirst() {
        ArrayList<LoopEx> loops = new ArrayList<>(loops());
        Collections.sort(loops, new Comparator<LoopEx>() {

            @Override
            public int compare(LoopEx o1, LoopEx o2) {
                return o2.loop().getDepth() - o1.loop().getDepth();
            }
        });
        return loops;
    }

    public Collection<LoopEx> countedLoops() {
        List<LoopEx> counted = new LinkedList<>();
        for (LoopEx loop : loops()) {
            if (loop.isCounted()) {
                counted.add(loop);
            }
        }
        return counted;
    }

    public void detectedCountedLoops() {
        for (LoopEx loop : loops()) {
            loop.detectCounted();
        }
    }

    public ControlFlowGraph controlFlowGraph() {
        return cfg;
    }

    public InductionVariable getInductionVariable(ValueNode value) {
        InductionVariable match = null;
        for (LoopEx loop : loops()) {
            InductionVariable iv = loop.getInductionVariables().get(value);
            if (iv != null) {
                if (match != null) {
                    return null;
                }
                match = iv;
            }
        }
        return match;
    }

    /**
     * Deletes any nodes created within the scope of this object that have no usages.
     */
    public void deleteUnusedNodes() {
        for (LoopEx loop : loops()) {
            loop.deleteUnusedNodes();
        }
    }
}
