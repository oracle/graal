/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;

/**
 * Graph decoder that simplifies nodes during decoding. The standard
 * {@link Canonicalizable#canonical node canonicalization} interface is used to canonicalize nodes
 * during decoding. Additionally, {@link IfNode branches} and {@link IntegerSwitchNode switches}
 * with constant conditions are simplified.
 */
public class SimplifyingGraphDecoder extends GraphDecoder {

    protected final MetaAccessProvider metaAccess;
    protected final ConstantReflectionProvider constantReflection;
    protected final StampProvider stampProvider;
    protected final boolean canonicalizeReads;

    protected class PECanonicalizerTool implements CanonicalizerTool {
        @Override
        public MetaAccessProvider getMetaAccess() {
            return metaAccess;
        }

        @Override
        public ConstantReflectionProvider getConstantReflection() {
            return constantReflection;
        }

        @Override
        public boolean canonicalizeReads() {
            return canonicalizeReads;
        }

        @Override
        public boolean allUsagesAvailable() {
            return false;
        }
    }

    public SimplifyingGraphDecoder(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, StampProvider stampProvider, boolean canonicalizeReads, Architecture architecture) {
        super(architecture);
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
        this.stampProvider = stampProvider;
        this.canonicalizeReads = canonicalizeReads;
    }

    @Override
    protected void cleanupGraph(MethodScope methodScope, Graph.Mark start) {
        GraphUtil.normalizeLoops(methodScope.graph);
        super.cleanupGraph(methodScope, start);
    }

    @Override
    protected boolean allowLazyPhis() {
        /*
         * We do not need to exactly reproduce the encoded graph, so we want to avoid unnecessary
         * phi functions.
         */
        return true;
    }

    @Override
    protected void simplifyFixedNode(MethodScope methodScope, LoopScope loopScope, int nodeOrderId, FixedNode node) {
        if (node instanceof IfNode) {
            IfNode ifNode = (IfNode) node;
            if (ifNode.condition() instanceof LogicNegationNode) {
                ifNode.eliminateNegation();
            }
            if (ifNode.condition() instanceof LogicConstantNode) {
                boolean condition = ((LogicConstantNode) ifNode.condition()).getValue();
                AbstractBeginNode survivingSuccessor = ifNode.getSuccessor(condition);
                AbstractBeginNode deadSuccessor = ifNode.getSuccessor(!condition);

                methodScope.graph.removeSplit(ifNode, survivingSuccessor);
                assert deadSuccessor.next() == null : "must not be parsed yet";
                deadSuccessor.safeDelete();
            }

        } else if (node instanceof IntegerSwitchNode && ((IntegerSwitchNode) node).value().isConstant()) {
            IntegerSwitchNode switchNode = (IntegerSwitchNode) node;
            int value = switchNode.value().asJavaConstant().asInt();
            AbstractBeginNode survivingSuccessor = switchNode.successorAtKey(value);
            List<Node> allSuccessors = switchNode.successors().snapshot();

            methodScope.graph.removeSplit(switchNode, survivingSuccessor);
            for (Node successor : allSuccessors) {
                if (successor != survivingSuccessor) {
                    assert ((AbstractBeginNode) successor).next() == null : "must not be parsed yet";
                    successor.safeDelete();
                }
            }

        } else if (node instanceof Canonicalizable) {
            Node canonical = ((Canonicalizable) node).canonical(new PECanonicalizerTool());
            if (canonical == null) {
                /*
                 * This is a possible return value of canonicalization. However, we might need to
                 * add additional usages later on for which we need a node. Therefore, we just do
                 * nothing and leave the node in place.
                 */
            } else if (canonical != node) {
                if (!canonical.isAlive()) {
                    assert !canonical.isDeleted();
                    canonical = methodScope.graph.addOrUniqueWithInputs(canonical);
                    if (canonical instanceof FixedWithNextNode) {
                        methodScope.graph.addBeforeFixed(node, (FixedWithNextNode) canonical);
                    } else if (canonical instanceof ControlSinkNode) {
                        FixedWithNextNode predecessor = (FixedWithNextNode) node.predecessor();
                        predecessor.setNext((ControlSinkNode) canonical);
                        node.safeDelete();
                        for (Node successor : node.successors()) {
                            successor.safeDelete();
                        }

                    } else {
                        assert !(canonical instanceof FixedNode);
                    }
                }
                if (!node.isDeleted()) {
                    GraphUtil.unlinkFixedNode((FixedWithNextNode) node);
                    node.replaceAtUsages(canonical);
                    node.safeDelete();
                }
                assert lookupNode(loopScope, nodeOrderId) == node;
                registerNode(loopScope, nodeOrderId, canonical, true, false);
            }
        }
    }

    @Override
    protected Node handleFloatingNodeBeforeAdd(MethodScope methodScope, LoopScope loopScope, Node node) {
        if (node instanceof Canonicalizable) {
            Node canonical = ((Canonicalizable) node).canonical(new PECanonicalizerTool());
            if (canonical == null) {
                /*
                 * This is a possible return value of canonicalization. However, we might need to
                 * add additional usages later on for which we need a node. Therefore, we just do
                 * nothing and leave the node in place.
                 */
            } else if (canonical != node) {
                if (!canonical.isAlive()) {
                    assert !canonical.isDeleted();
                    canonical = methodScope.graph.addOrUniqueWithInputs(canonical);
                }
                assert node.hasNoUsages();
                // methodScope.graph.replaceFloating((FloatingNode) node, canonical);
                return canonical;
            }
        }
        return node;
    }

    @Override
    protected Node addFloatingNode(MethodScope methodScope, Node node) {
        /*
         * In contrast to the base class implementation, we do not need to exactly reproduce the
         * encoded graph. Since we do canonicalization, we also want nodes to be unique.
         */
        return methodScope.graph.addOrUnique(node);
    }
}
