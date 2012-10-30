/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.virtual.phases.ea;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.virtual.phases.ea.EffectList.Effect;

public class PartialEscapeAnalysisPhase extends Phase {

    public static final void trace(String format, Object... obj) {
        if (GraalOptions.TraceEscapeAnalysis) {
            Debug.log(format, obj);
        }
    }

    public static final void error(String format, Object... obj) {
        System.out.print(String.format(format, obj));
    }

    private final GraalCodeCacheProvider runtime;

    public PartialEscapeAnalysisPhase(GraalCodeCacheProvider runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void run(StructuredGraph graph) {
        SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph, false);
        PartialEscapeClosure closure = new PartialEscapeClosure(graph.createNodeBitMap(), schedule, runtime);
        ReentrantBlockIterator.apply(closure, schedule.getCFG().getStartBlock(), new BlockState(), null);

        // apply the effects collected during the escape analysis iteration
        ArrayList<Node> obsoleteNodes = new ArrayList<>();
        for (int i = 0; i < closure.effects.size(); i++) {
            Effect effect = closure.effects.get(i);
            effect.apply(graph, obsoleteNodes);
            if (GraalOptions.TraceEscapeAnalysis) {
                if (effect.isVisible()) {
                    int level = closure.effects.levelAt(i);
                    StringBuilder str = new StringBuilder();
                    for (int i2 = 0; i2 < level; i2++) {
                        str.append("    ");
                    }
                    trace(str.append(effect).toString());
                }
            }
        }
        Debug.dump(graph, "after PartialEscapeAnalysis");
        assert noObsoleteNodes(graph, obsoleteNodes);

        new DeadCodeEliminationPhase().apply(graph);
    }

    private static boolean noObsoleteNodes(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
        // helper code that determines the paths that keep obsolete nodes alive:

        NodeFlood flood = graph.createNodeFlood();
        IdentityHashMap<Node, Node> path = new IdentityHashMap<>();
        flood.add(graph.start());
        for (Node current : flood) {
            if (current instanceof EndNode) {
                EndNode end = (EndNode) current;
                flood.add(end.merge());
                if (!path.containsKey(end.merge())) {
                    path.put(end.merge(), end);
                }
            } else {
                for (Node successor : current.successors()) {
                    flood.add(successor);
                    if (!path.containsKey(successor)) {
                        path.put(successor, current);
                    }
                }
            }
        }

        for (Node node : obsoleteNodes) {
            if (node instanceof FixedNode) {
                assert !flood.isMarked(node);
            }
        }

        for (Node node : graph.getNodes()) {
            if (node instanceof LocalNode) {
                flood.add(node);
            }
            if (flood.isMarked(node)) {
                for (Node input : node.inputs()) {
                    flood.add(input);
                    if (!path.containsKey(input)) {
                        path.put(input, node);
                    }
                }
            }
        }
        for (Node current : flood) {
            for (Node input : current.inputs()) {
                flood.add(input);
                if (!path.containsKey(input)) {
                    path.put(input, current);
                }
            }
        }

        boolean success = true;
        for (Node node : obsoleteNodes) {
            if (flood.isMarked(node)) {
                System.out.println("offending node path:");
                Node current = node;
                while (current != null) {
                    System.out.println(current);
                    current = path.get(current);
                    if (current != null && current instanceof FixedNode && !obsoleteNodes.contains(current)) {
                        break;
                    }
                }
                success = false;
            }
        }
        return success;
    }
}
