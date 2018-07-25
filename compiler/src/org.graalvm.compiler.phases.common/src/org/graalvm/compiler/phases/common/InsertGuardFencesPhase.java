/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

import java.util.ArrayList;
import java.util.List;

public class InsertGuardFencesPhase extends Phase {
    @Override
    protected void run(StructuredGraph graph) {
        for (AbstractBeginNode beginNode : graph.getNodes(AbstractBeginNode.TYPE)) {
            if (hasGuardUsages(beginNode)) {
                if (graph.getDebug().isLogEnabled(DebugContext.DETAILED_LEVEL)) {
                    graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Adding speculation fence for %s at %s", guardUsages(beginNode), beginNode);
                }
                beginNode.setWithSpeculationFence();
            } else {
                graph.getDebug().log(DebugContext.DETAILED_LEVEL, "No guards on %s", beginNode);
            }
        }
    }

    private static boolean hasGuardUsages(Node n) {
        for (Node usage : n.usages()) {
            for (Position pos : usage.inputPositions()) {
                if (pos.getInputType() == InputType.Guard && pos.get(usage) == n) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Node> guardUsages(Node n) {
        List<Node> ret = new ArrayList<>();
        for (Node usage : n.usages()) {
            for (Position pos : usage.inputPositions()) {
                if (pos.getInputType() == InputType.Guard && pos.get(usage) == n) {
                    ret.add(usage);
                }
            }
        }
        return ret;
    }
}
