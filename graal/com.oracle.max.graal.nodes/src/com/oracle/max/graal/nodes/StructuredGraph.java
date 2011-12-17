/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.java.*;


/**
 * A graph that contains at least one distinguished node : the {@link #start() start} node.
 * This node is the start of the control flow of the graph.
 */
public class StructuredGraph extends Graph {
    private final BeginNode start;

    /**
     * Creates a new Graph containing a single {@link BeginNode} as the {@link #start() start} node.
     */
    public StructuredGraph(String name) {
        super(name);
        this.start = add(new BeginNode());
    }

    /**
     * Creates a new Graph containing a single {@link BeginNode} as the {@link #start() start} node.
     */
    public StructuredGraph() {
        this(null);
    }

    public BeginNode start() {
        return start;
    }

    @Override
    public StructuredGraph copy() {
        return copy(name);
    }

    @Override
    public StructuredGraph copy(String name) {
        StructuredGraph copy = new StructuredGraph(name);
        HashMap<Node, Node> replacements = new HashMap<Node, Node>();
        replacements.put(start, copy.start);
        copy.addDuplicates(getNodes(), replacements);
        return copy;
    }

    public LocalNode getLocal(int index) {
        for (LocalNode local : getNodes(LocalNode.class)) {
            if (local.index() == index) {
                return local;
            }
        }
        return null;
    }

    public Iterable<Invoke> getInvokes() {
        final Iterator<MethodCallTargetNode> callTargets = getNodes(MethodCallTargetNode.class).iterator();
        return new Iterable<Invoke>() {
            private Invoke next;

            @Override
            public Iterator<Invoke> iterator() {
                return new Iterator<Invoke>() {

                    @Override
                    public boolean hasNext() {
                        if (next == null) {
                            while (callTargets.hasNext()) {
                                Invoke i = callTargets.next().invoke();
                                if (i != null) {
                                    next = i;
                                    return true;
                                }
                            }
                            return false;
                        } else {
                            return true;
                        }
                    }

                    @Override
                    public Invoke next() {
                        try {
                            return next;
                        } finally {
                            next = null;
                        }
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public boolean hasLoops() {
        return getNodes(LoopBeginNode.class).iterator().hasNext();
    }
}
