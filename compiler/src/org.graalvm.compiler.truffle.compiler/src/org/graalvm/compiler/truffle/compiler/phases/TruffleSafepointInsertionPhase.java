/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases;

import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleSafepointNode;

/**
 * Adds safepoints to loops and call targets.
 */
public final class TruffleSafepointInsertionPhase extends Phase {

    @Override
    public boolean checkContract() {
        // the size / cost after is highly dynamic and dependent on the graph, thus we do not verify
        // costs for this phase
        return false;
    }

    @SuppressWarnings("try")
    @Override
    protected void run(StructuredGraph graph) {
        /*
         * Truffle does not have any safepoint mechanism at calls, therefore we need to notify the
         * safepoint at the end of methods.
         */
        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            insertSafepoint(graph, returnNode);
        }

        for (LoopBeginNode loopBeginNode : graph.getNodes(LoopBeginNode.TYPE)) {
            for (LoopEndNode loopEndNode : loopBeginNode.loopEnds()) {
                if (loopEndNode.canSafepoint()) {
                    try (DebugCloseable s = loopEndNode.withNodeSourcePosition()) {
                        insertSafepoint(graph, loopEndNode);
                    }
                }
            }
        }
    }

    private static void insertSafepoint(StructuredGraph graph, FixedNode returnNode) {
        graph.addBeforeFixed(returnNode, graph.add(new TruffleSafepointNode()));
    }
}
