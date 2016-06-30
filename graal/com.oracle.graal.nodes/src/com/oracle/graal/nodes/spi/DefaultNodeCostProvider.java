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
package com.oracle.graal.nodes.spi;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodeinfo.NodeCycles;
import com.oracle.graal.nodeinfo.NodeSize;
import com.oracle.graal.nodes.CallTargetNode;
import com.oracle.graal.nodes.InvokeNode;
import com.oracle.graal.nodes.LoopEndNode;
import com.oracle.graal.nodes.extended.IntegerSwitchNode;
import com.oracle.graal.nodes.extended.SwitchNode;
import com.oracle.graal.nodes.java.AccessFieldNode;
import com.oracle.graal.nodes.virtual.CommitAllocationNode;

/*
 * (dl) Certain node costs can not, based on the meta information encoded in the node properties,
 * be computed before a real node is instantiated. E.g. the type of a call in Java heavily
 * influences the cost of an invocation and thus must be decided dynamically.
 */
public class DefaultNodeCostProvider implements NodeCostProvider {

    @Override
    public final int sizeNumeric(Node n) {
        return NodeSize.relativeSize(() -> size(n));
    }

    @Override
    public final int cyclesNumeric(Node n) {
        return NodeCycles.relativeCycles(() -> cycles(n));
    }

    @Override
    public NodeSize size(Node n) {
        if (n instanceof InvokeNode) {
            /*
             * Code size for the invoke itself is a very weak approximation.
             */
            InvokeNode ivk = (InvokeNode) n;
            CallTargetNode mct = ivk.callTarget();
            switch (mct.invokeKind()) {
                case Interface:
                    return NodeSize.SIZE_50;
                case Special:
                case Static:
                    return NodeSize.SIZE_2;
                case Virtual:
                    return NodeSize.SIZE_4;
                default:
                    break;
            }
        } else if (n instanceof CommitAllocationNode) {
            CommitAllocationNode commit = (CommitAllocationNode) n;
            /*
             * very weak approximation, current problem is node size is an enum and we cannot
             * dynamically instantiate a new case like nrOfAllocs*allocationCodeSize
             */
            int nrOfAllocs = commit.getVirtualObjects().size();
            if (nrOfAllocs < 5) {
                return NodeSize.SIZE_80;
            } else if (nrOfAllocs < 10) {
                return NodeSize.SIZE_100;
            } else {
                return NodeSize.SIZE_200;
            }
        } else if (n instanceof AccessFieldNode) {
            if (((AccessFieldNode) n).field().isVolatile()) {
                // membar size is added
                return NodeSize.SIZE_10;
            }
        } else if (n instanceof LoopEndNode) {
            if (((LoopEndNode) n).canSafepoint()) {
                return NodeSize.SIZE_6;
            }
        } else if (n instanceof SwitchNode) {
            SwitchNode x = (SwitchNode) n;
            int keyCount = x.keyCount();
            if (keyCount == 0) {
                return NodeSize.SIZE_1;
            } else {
                if (keyCount == 1) {
                    // if
                    return NodeSize.SIZE_2;
                } else if (x instanceof IntegerSwitchNode && x.isSorted()) {
                    // good heuristic
                    return NodeSize.SIZE_15;
                } else {
                    // not so good
                    return NodeSize.SIZE_30;
                }
            }
        }

        return n.getNodeClass().size();
    }

    @Override
    public NodeCycles cycles(Node n) {
        if (n instanceof InvokeNode) {
            InvokeNode ivk = (InvokeNode) n;
            CallTargetNode mct = ivk.callTarget();
            switch (mct.invokeKind()) {
                case Interface:
                    return NodeCycles.CYCLES_100;
                case Special:
                case Static:
                    return NodeCycles.CYCLES_2;
                case Virtual:
                    return NodeCycles.CYCLES_4;
                default:
                    break;
            }
        } else if (n instanceof CommitAllocationNode) {
            CommitAllocationNode commit = (CommitAllocationNode) n;
            /*
             * very weak approximation, current problem is node cycles is an enum and we cannot
             * dynamically instantiate a new case like nrOfAllocs*allocationCost
             */
            int nrOfAllocs = commit.getVirtualObjects().size();
            if (nrOfAllocs < 5) {
                return NodeCycles.CYCLES_20;
            } else if (nrOfAllocs < 10) {
                return NodeCycles.CYCLES_40;
            } else {
                return NodeCycles.CYCLES_80;
            }
        } else if (n instanceof AccessFieldNode) {
            if (((AccessFieldNode) n).field().isVolatile()) {
                // membar cycles is added
                return NodeCycles.CYCLES_30;
            }
        } else if (n instanceof LoopEndNode) {
            if (((LoopEndNode) n).canSafepoint()) {
                return NodeCycles.CYCLES_INFINITY;
            }
        } else if (n instanceof SwitchNode) {
            SwitchNode x = (SwitchNode) n;
            int keyCount = x.keyCount();
            if (keyCount == 0) {
                return NodeCycles.CYCLES_1;
            } else {
                if (keyCount == 1) {
                    // if
                    return NodeCycles.CYCLES_2;
                } else if (x instanceof IntegerSwitchNode && x.isSorted()) {
                    // good heuristic
                    return NodeCycles.CYCLES_15;
                } else {
                    // not so good
                    return NodeCycles.CYCLES_30;
                }
            }
        }

        return n.getNodeClass().cycles();
    }

}
