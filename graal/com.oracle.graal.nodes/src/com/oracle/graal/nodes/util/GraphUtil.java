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
package com.oracle.graal.nodes.util;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;

public class GraphUtil {

    private static final NodePredicate FLOATING = new NodePredicate() {
        @Override
        public final boolean  apply(Node n) {
            //isA(FloatingNode.class).or(VirtualState.class).or(CallTargetNode.class)
            return n instanceof FloatingNode || n instanceof VirtualState || n instanceof CallTargetNode;
        }
    };

    public static void killCFG(FixedNode node) {
        assert node.isAlive();
        if (node instanceof EndNode) {
            // We reached a control flow end.
            EndNode end = (EndNode) node;
            killEnd(end);
        } else {
            // Normal control flow node.
            /* We do not take a successor snapshot because this iterator supports concurrent modifications
             * as long as they do not change the size of the successor list. Not taking a snapshot allows
             * us to see modifications to other branches that may happen while processing one branch.
             */
            for (Node successor : node.successors()) {
                killCFG((FixedNode) successor);
            }
        }
        propagateKill(node);
    }

    private static void killEnd(EndNode end) {
        MergeNode merge = end.merge();
        if (merge != null) {
            merge.removeEnd(end);
            StructuredGraph graph = (StructuredGraph) end.graph();
            if (merge instanceof LoopBeginNode && merge.forwardEndCount() == 0) { //dead loop
                for (PhiNode phi : merge.phis().snapshot()) {
                    propagateKill(phi);
                }
                LoopBeginNode begin = (LoopBeginNode) merge;
                // disconnect and delete loop ends & loop exits
                for (LoopEndNode loopend : begin.loopEnds().snapshot()) {
                    loopend.predecessor().replaceFirstSuccessor(loopend, null);
                    loopend.safeDelete();
                }
                begin.removeExits();
                killCFG(begin.next());
                begin.safeDelete();
            } else if (merge instanceof LoopBeginNode && ((LoopBeginNode) merge).loopEnds().isEmpty()) { // not a loop anymore
                graph.reduceDegenerateLoopBegin((LoopBeginNode) merge);
            } else if (merge.phiPredecessorCount() == 1) { // not a merge anymore
                graph.reduceTrivialMerge(merge);
            }
        }
    }

    public static NodePredicate isFloatingNode() {
        return FLOATING;
    }

    public static void propagateKill(Node node) {
        if (node != null && node.isAlive()) {
            List<Node> usagesSnapshot = node.usages().filter(isFloatingNode()).snapshot();

            // null out remaining usages
            node.replaceAtUsages(null);
            node.replaceAtPredecessor(null);
            killWithUnusedFloatingInputs(node);

            for (Node usage : usagesSnapshot) {
                if (!usage.isDeleted()) {
                    if (usage instanceof PhiNode) {
                        usage.replaceFirstInput(node, null);
                    } else {
                        propagateKill(usage);
                    }
                }
            }
        }
    }

    public static void killWithUnusedFloatingInputs(Node node) {
        List<Node> floatingInputs = node.inputs().filter(isFloatingNode()).snapshot();
        node.safeDelete();

        for (Node in : floatingInputs) {
            if (in.isAlive() && in.usages().isEmpty()) {
                killWithUnusedFloatingInputs(in);
            }
        }
    }

    public static void checkRedundantPhi(PhiNode phiNode) {
        if (phiNode.isDeleted() || phiNode.valueCount() == 1) {
            return;
        }

        ValueNode singleValue = phiNode.singleValue();
        if (singleValue != null) {
            Collection<PhiNode> phiUsages = phiNode.usages().filter(PhiNode.class).snapshot();
            Collection<ValueProxyNode> proxyUsages = phiNode.usages().filter(ValueProxyNode.class).snapshot();
            ((StructuredGraph) phiNode.graph()).replaceFloating(phiNode, singleValue);
            for (PhiNode phi : phiUsages) {
                checkRedundantPhi(phi);
            }
            for (ValueProxyNode proxy : proxyUsages) {
                checkRedundantProxy(proxy);
            }
        }
    }

    public static void checkRedundantProxy(ValueProxyNode vpn) {
        BeginNode proxyPoint = vpn.proxyPoint();
        if (proxyPoint instanceof LoopExitNode) {
            LoopExitNode exit = (LoopExitNode) proxyPoint;
            LoopBeginNode loopBegin = exit.loopBegin();
            ValueNode vpnValue = vpn.value();
            for (ValueNode v : loopBegin.stateAfter().values()) {
                ValueNode v2 = v;
                if (loopBegin.isPhiAtMerge(v2)) {
                    v2 = ((PhiNode) v2).valueAt(loopBegin.forwardEnd());
                }
                if (vpnValue == v2) {
                    Collection<PhiNode> phiUsages = vpn.usages().filter(PhiNode.class).snapshot();
                    Collection<ValueProxyNode> proxyUsages = vpn.usages().filter(ValueProxyNode.class).snapshot();
                    ((StructuredGraph) vpn.graph()).replaceFloating(vpn, vpnValue);
                    for (PhiNode phi : phiUsages) {
                        checkRedundantPhi(phi);
                    }
                    for (ValueProxyNode proxy : proxyUsages) {
                        checkRedundantProxy(proxy);
                    }
                    return;
                }
            }
        }
    }

    public static void normalizeLoopBegin(LoopBeginNode begin) {
        // Delete unnecessary loop phi functions, i.e., phi functions where all inputs are either the same or the phi itself.
        for (PhiNode phi : begin.phis().snapshot()) {
            GraphUtil.checkRedundantPhi(phi);
        }
        for (LoopExitNode exit : begin.loopExits()) {
            for (ValueProxyNode vpn : exit.proxies().snapshot()) {
                GraphUtil.checkRedundantProxy(vpn);
            }
        }
    }

    /**
     * Gets an approximate source code location for a node if possible.
     *
     * @return a file name and source line number in stack trace format (e.g. "String.java:32")
     *          if an approximate source location is found, null otherwise
     */
    public static String approxSourceLocation(Node node) {
        Node n = node;
        while (n != null) {
            if (n instanceof MethodCallTargetNode) {
                n = ((MethodCallTargetNode) n).invoke().node();
            }

            if (n instanceof StateSplit) {
                FrameState stateAfter = ((StateSplit) n).stateAfter();
                if (stateAfter != null) {
                    ResolvedJavaMethod method = stateAfter.method();
                    if (method != null) {
                        StackTraceElement stackTraceElement = method.toStackTraceElement(stateAfter.bci);
                        if (stackTraceElement.getFileName() != null && stackTraceElement.getLineNumber() >= 0) {
                            return stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber();
                        }
                    }
                }
            }
            n = n.predecessor();
        }
        return null;
    }

    public static ValueNode unProxify(ValueNode proxy) {
        ValueNode v = proxy;
        while (v instanceof ValueProxyNode) {
            v = ((ValueProxyNode) v).value();
        }
        return v;
    }

    /**
     * Returns a string representation of the given collection of objects.
     *
     * @param objects The {@link Iterable} that will be used to iterate over the objects.
     * @return A string of the format "[a, b, ...]".
     */
    public static String toString(Iterable< ? > objects) {
        StringBuilder str = new StringBuilder();
        str.append("[");
        for (Object o : objects) {
            str.append(o).append(", ");
        }
        if (str.length() > 1) {
            str.setLength(str.length() - 2);
        }
        str.append("]");
        return str.toString();
    }
}
