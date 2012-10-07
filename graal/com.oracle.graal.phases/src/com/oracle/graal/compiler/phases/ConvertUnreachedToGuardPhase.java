/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.phases;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.util.*;


public class ConvertUnreachedToGuardPhase extends Phase {
    private OptimisticOptimizations opt;

    public ConvertUnreachedToGuardPhase(OptimisticOptimizations opt) {
        this.opt = opt;
    }

    @Override
    protected void run(StructuredGraph graph) {
        if (!opt.removeNeverExecutedCode()) {
            return;
        }
        for (Node node : graph.getNodes()) {
            if (node instanceof IfNode) {
                IfNode ifNode = (IfNode) node;
                BeginNode insertGuard = null;
                BeginNode delete = null;
                boolean inverted = false;
                if (ifNode.probability(IfNode.TRUE_EDGE) == 0) {
                    insertGuard = ifNode.falseSuccessor();
                    delete = ifNode.trueSuccessor();
                    inverted = true;
                } else if (ifNode.probability(IfNode.FALSE_EDGE) == 0) {
                    insertGuard = ifNode.trueSuccessor();
                    delete = ifNode.falseSuccessor();
                }
                if (insertGuard != null) {
                    GuardNode guard = graph.unique(new GuardNode(ifNode.compare(), BeginNode.prevBegin(ifNode), DeoptimizationReason.UnreachedCode, DeoptimizationAction.InvalidateReprofile, inverted, ifNode.leafGraphId()));
                    graph.addBeforeFixed(ifNode, graph.add(new ValueAnchorNode(guard)));
                    GraphUtil.killCFG(delete);
                    graph.removeSplit(ifNode, inverted ? IfNode.FALSE_EDGE : IfNode.TRUE_EDGE);
                }
            }
        }

    }

}
