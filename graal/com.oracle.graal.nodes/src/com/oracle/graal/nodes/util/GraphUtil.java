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

import static com.oracle.graal.graph.iterators.NodePredicates.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graph.iterators.NodePredicates.PositiveTypePredicate;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.virtual.*;

public class GraphUtil {

    private static final PositiveTypePredicate FLOATING = isA(FloatingNode.class).or(CallTargetNode.class).or(FrameState.class).or(VirtualObjectFieldNode.class).or(VirtualObjectNode.class);

    public static void killCFG(FixedNode node) {
        assert node.isAlive();
        if (node instanceof EndNode) {
            // We reached a control flow end.
            EndNode end = (EndNode) node;
            killEnd(end);
        } else {
            // Normal control flow node.
            /* We do not take a successor snapshot because this iterator supports concurrent modifications
             * as long as they do not change the size of the successor list. Not tasking a snapshot allows
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
            for (LoopExitNode loopexit : begin.loopExits().snapshot()) {
                for (ValueProxyNode vpn : loopexit.proxies().snapshot()) {
                    graph.replaceFloating(vpn, vpn.value());
                }
                graph.replaceFixedWithFixed(loopexit, graph.add(new BeginNode()));
            }
            killCFG(begin.next());
            begin.safeDelete();
        } else if (merge instanceof LoopBeginNode && ((LoopBeginNode) merge).loopEnds().isEmpty()) { // not a loop anymore
            graph.reduceDegenerateLoopBegin((LoopBeginNode) merge);
        } else if (merge.phiPredecessorCount() == 1) { // not a merge anymore
            graph.reduceTrivialMerge(merge);
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

    private static ValueProxyNode findProxy(ValueNode value, BeginNode proxyPoint) {
        for (ValueProxyNode vpn : proxyPoint.proxies()) {
            ValueNode v = vpn;
            while (v instanceof ValueProxyNode) {
                v = ((ValueProxyNode) v).value();
                if (v == value) {
                    return vpn;
                }
            }
        }
        return null;
    }

    public static ValueNode mergeVirtualChain(
                    StructuredGraph graph,
                    ValueNode vof,
                    ValueNode newVof,
                    PhiNode vPhi,
                    BeginNode earlyExit,
                    BeginNode newEarlyExit,
                    MergeNode merge) {
        VirtualObjectNode vObject = virtualObject(vof);
        assert virtualObject(newVof) == vObject;
        ValueNode[] virtualState = virtualState(vof);
        ValueNode[] newVirtualState = virtualState(newVof);
        ValueNode chain = vPhi;
        for (int i = 0; i < virtualState.length; i++) {
            ValueNode value = virtualState[i];
            ValueNode newValue = newVirtualState[i];
            assert value.kind() == newValue.kind();
            if (value != newValue) {
                PhiNode valuePhi = graph.add(new PhiNode(value.kind(), merge));
                ValueProxyNode inputProxy = findProxy(value, earlyExit);
                if (inputProxy != null) {
                    ValueProxyNode newInputProxy = findProxy(newValue, newEarlyExit);
                    assert newInputProxy != null : "no proxy for " + newValue + " at " + newEarlyExit;
                    valuePhi.addInput(inputProxy);
                    valuePhi.addInput(newInputProxy);
                } else {
                    valuePhi.addInput(graph.unique(new ValueProxyNode(value, earlyExit, PhiType.Value)));
                    valuePhi.addInput(newValue);
                }
                chain = graph.add(new VirtualObjectFieldNode(vObject, chain, valuePhi, i));
            }
        }
        return chain;
    }

    public static ValueNode mergeVirtualChain(
                    StructuredGraph graph,
                    PhiNode vPhi,
                    MergeNode merge) {
        NodeInputList<ValueNode> virtuals = vPhi.values();
        VirtualObjectNode vObject = virtualObject(unProxify(virtuals.first()));
        List<ValueNode[]> virtualStates = new ArrayList<>(virtuals.size());
        for (ValueNode virtual : virtuals) {
            virtualStates.add(virtualState(unProxify(virtual)));
        }
        ValueNode chain = vPhi;
        int stateLength = virtualStates.get(0).length;
        for (int i = 0; i < stateLength; i++) {
            ValueNode v = null;
            boolean reconcile = false;
            for (ValueNode[] state : virtualStates) {
                if (v == null) {
                    v = state[i];
                } else if (v != state[i]) {
                    reconcile = true;
                    break;
                }
                assert v.kind() == state[i].kind();
            }
            if (reconcile) {
                PhiNode valuePhi = graph.add(new PhiNode(v.kind(), merge));
                for (ValueNode[] state : virtualStates) {
                    valuePhi.addInput(state[i]);
                }
                chain = graph.add(new VirtualObjectFieldNode(vObject, chain, valuePhi, i));
            }
        }
        return chain;
    }

    /**
     * Returns the VirtualObjectNode associated with the virtual chain of the provided virtual node.
     * @param vof a virtual ValueNode (a VirtualObjectFieldNode or a Virtual Phi)
     * @return the VirtualObjectNode associated with the virtual chain of the provided virtual node.
     */
    public static VirtualObjectNode virtualObject(ValueNode vof) {
        assert vof instanceof VirtualObjectFieldNode || (vof instanceof PhiNode && ((PhiNode) vof).type() == PhiType.Virtual) : vof;
        ValueNode currentField = vof;
        do {
            if (currentField instanceof VirtualObjectFieldNode) {
               return ((VirtualObjectFieldNode) currentField).object();
            } else {
                assert currentField instanceof PhiNode && ((PhiNode) currentField).type() == PhiType.Virtual : currentField;
                currentField = ((PhiNode) currentField).valueAt(0);
            }
        } while (currentField != null);
        throw new GraalInternalError("Invalid virtual chain : cound not find virtual object from %s", vof);
    }

    /**
     * Builds the state of the virtual object at the provided point into a virtual chain.
     * @param vof a virtual ValueNode (a VirtualObjectFieldNode or a Virtual Phi)
     * @return the state of the virtual object at the provided point into a virtual chain.
     */
    public static ValueNode[] virtualState(ValueNode vof) {
        return virtualState(vof, virtualObject(vof));
    }

    /**
     * Builds the state of the virtual object at the provided point into a virtual chain.
     * @param vof a virtual ValueNode (a VirtualObjectFieldNode or a Virtual Phi)
     * @param vObj the virtual object
     * @return the state of the virtual object at the provided point into a virtual chain.
     */
    public static ValueNode[] virtualState(ValueNode vof, VirtualObjectNode vObj) {
        int fieldsCount = vObj.fieldsCount();
        int dicovered = 0;
        ValueNode[] state = new ValueNode[fieldsCount];
        ValueNode currentField = vof;
        do {
            if (currentField instanceof VirtualObjectFieldNode) {
                int index = ((VirtualObjectFieldNode) currentField).index();
                if (state[index] == null) {
                    dicovered++;
                    state[index] = ((VirtualObjectFieldNode) currentField).input();
                }
                currentField = ((VirtualObjectFieldNode) currentField).lastState();
            } else if (currentField instanceof ValueProxyNode) {
                currentField = ((ValueProxyNode) currentField).value();
            } else {
                assert currentField instanceof PhiNode && ((PhiNode) currentField).type() == PhiType.Virtual : currentField;
                currentField = ((PhiNode) currentField).valueAt(0);
            }
        } while (currentField != null && dicovered < fieldsCount);
        return state;
    }
}
