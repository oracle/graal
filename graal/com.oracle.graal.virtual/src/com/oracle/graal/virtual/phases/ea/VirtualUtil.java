/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

public final class VirtualUtil {

    private VirtualUtil() {
        GraalInternalError.shouldNotReachHere();
    }

    public static boolean assertNonReachable(StructuredGraph graph, List<Node> obsoleteNodes) {
        // helper code that determines the paths that keep obsolete nodes alive:

        NodeFlood flood = graph.createNodeFlood();
        IdentityHashMap<Node, Node> path = new IdentityHashMap<>();
        flood.add(graph.start());
        for (Node current : flood) {
            if (current instanceof AbstractEndNode) {
                AbstractEndNode end = (AbstractEndNode) current;
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
                assert !flood.isMarked(node) : node;
            }
        }

        for (Node node : graph.getNodes()) {
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
                TTY.println("offending node path:");
                Node current = node;
                TTY.print(current.toString());
                while (true) {
                    current = path.get(current);
                    if (current != null) {
                        TTY.print(" -> " + current.toString());
                        if (current instanceof FixedNode && !obsoleteNodes.contains(current)) {
                            break;
                        }
                    }
                }
                success = false;
            }
        }
        if (!success) {
            TTY.println();
        }
        return success;
    }

    public static void trace(String format, Object... obj) {
        if (TraceEscapeAnalysis.getValue() && Debug.isLogEnabled()) {
            Debug.logv(format, obj);
        }
    }

    public static boolean matches(StructuredGraph graph, String filter) {
        if (filter != null) {
            if (filter.startsWith("~")) {
                ResolvedJavaMethod method = graph.method();
                return method == null || !MetaUtil.format("%H.%n", method).contains(filter.substring(1));
            } else {
                ResolvedJavaMethod method = graph.method();
                return method != null && MetaUtil.format("%H.%n", method).contains(filter);
            }
        }
        return true;
    }

}
