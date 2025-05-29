/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.codegen.phase;

import com.oracle.svm.hosted.webimage.codegen.node.CompoundConditionNode;
import com.oracle.svm.hosted.webimage.codegen.node.CompoundConditionNode.CompoundOp;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.metrickeys.MethodMetricKeys;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Scope;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.Phase;

@SuppressWarnings("try")
public class IncrementalCompoundConditionalPhase extends Phase {

    public IncrementalCompoundConditionalPhase() {

    }

    @Override
    protected void run(StructuredGraph graph) {
        int change = 1;

        outer: while (change > 0) {
            change = 0;
            for (IfNode ifnode : graph.getNodes(IfNode.TYPE).snapshot()) {
                if (isCompoundStatement(ifnode)) {
                    /*
                     * one compound might yield others, and we change the graph
                     */
                    change++;
                    continue outer;
                }
            }
        }
    }

    private static boolean isCompoundStatement(IfNode ifnode) {
        try (Scope s = ifnode.getDebug().scope("Incremental Compound Conditional Removal Phase")) {
            if (xAAy(ifnode) != null) {
                LoggerContext.counter(MethodMetricKeys.NUM_COMPOUND_COND_XAAY).increment();
                return true;
            } else if (xOOy(ifnode) != null) {
                LoggerContext.counter(MethodMetricKeys.NUM_COMPOUND_COND_XOOY).increment();
                return true;
            }
            return false;
        }
    }

    /**
     *
     * This method explicitly looks for the pattern of A&&B in two branches where both false
     * successors of two - true succ connected if's merge.
     *
     * @param ifnode the if node for looking for a compound conditional and
     * @return the if node that was originally supplied for checking for compound conditional and
     */
    private static IfNode xAAy(IfNode ifnode) {
        AbstractBeginNode trueSucc = ifnode.trueSuccessor();
        AbstractBeginNode falseSucc = ifnode.falseSuccessor();

        if (trueSucc instanceof LoopExitNode || falseSucc instanceof LoopExitNode) {
            return null;
        }

        if (falseSucc.next() instanceof EndNode) {
            AbstractMergeNode originalMergeFalse = ((EndNode) falseSucc.next()).merge();
            LogicNode originalCondition = ifnode.condition();

            if (trueSucc.next() instanceof IfNode) {
                IfNode potentialCompoundIf = (IfNode) trueSucc.next();
                LogicNode potentialComoundCondition = potentialCompoundIf.condition();

                if (potentialCompoundIf.trueSuccessor() instanceof LoopExitNode || potentialCompoundIf.falseSuccessor() instanceof LoopExitNode) {
                    return null;
                }

                if (potentialCompoundIf.falseSuccessor().next() instanceof EndNode) {
                    AbstractMergeNode compoundMergeFalse = ((EndNode) potentialCompoundIf.falseSuccessor().next()).merge();

                    if (originalMergeFalse.equals(compoundMergeFalse) && originalMergeFalse.forwardEndCount() == 2) {
                        if (!(originalMergeFalse.forwardEnds().contains(falseSucc.next()) && originalMergeFalse.forwardEnds().contains(potentialCompoundIf.falseSuccessor().next()))) {
                            return null;
                        }

                        if (compoundMergeFalse.phis().count() > 0) {
                            // different values floating on the branches into the phi
                            return null;
                        }

                        AbstractBeginNode deadAbstractBeginNode = potentialCompoundIf.trueSuccessor();
                        FixedNode newCompoundTrueSucc = deadAbstractBeginNode.next();
                        FixedNode newCompoundFalseSucc = compoundMergeFalse.next();

                        if (newCompoundTrueSucc instanceof LoopEndNode || newCompoundFalseSucc instanceof LoopEndNode) {
                            return null;
                        }

                        ifnode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, ifnode.graph(), "Before XaaY rewrite");

                        // remove connection to them
                        deadAbstractBeginNode.setNext(null);
                        compoundMergeFalse.setNext(null);

                        trueSucc.setNext(newCompoundTrueSucc);
                        /*
                         * All usages of deadAbstractBeginNode need to be replaced. Otherwise, its
                         * usages will also get deleted which could delete more nodes than we want.
                         * The node deadAbstractBeginNode will be deleted below when the
                         * potentialCompoundIf node gets deleted by GraphUtil.killCFG.
                         */
                        deadAbstractBeginNode.replaceAtUsages(trueSucc);
                        falseSucc.setNext(newCompoundFalseSucc);
                        ifnode.setCondition(ifnode.graph().addWithoutUnique(new CompoundConditionNode(originalCondition, potentialComoundCondition, CompoundOp.XaaY)));

                        GraphUtil.killCFG(potentialCompoundIf);

                        ifnode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, ifnode.graph(), "After XaaY rewrite");

                        return ifnode;
                    }
                }
            }
        }

        return null;

    }

    /**
     * Same as the XaaY method just for compound conditional ORs.
     */
    private static IfNode xOOy(IfNode ifnode) {
        AbstractBeginNode trueSucc = ifnode.trueSuccessor();
        AbstractBeginNode falseSucc = ifnode.falseSuccessor();

        if (trueSucc instanceof LoopExitNode || falseSucc instanceof LoopExitNode) {
            return null;
        }

        if (trueSucc.next() instanceof EndNode) {
            AbstractMergeNode originalMergeTrue = ((EndNode) trueSucc.next()).merge();
            LogicNode originalCondition = ifnode.condition();

            if (falseSucc.next() instanceof IfNode) {
                IfNode potentialCompoundIf = (IfNode) falseSucc.next();
                LogicNode potentialComoundCondition = potentialCompoundIf.condition();

                if (potentialCompoundIf.trueSuccessor() instanceof LoopExitNode || potentialCompoundIf.falseSuccessor() instanceof LoopExitNode) {
                    return null;
                }

                if (potentialCompoundIf.trueSuccessor().next() instanceof EndNode) {
                    AbstractMergeNode compoundMergerTrue = ((EndNode) potentialCompoundIf.trueSuccessor().next()).merge();

                    if (originalMergeTrue.equals(compoundMergerTrue) && originalMergeTrue.forwardEndCount() == 2) {
                        if (!(originalMergeTrue.forwardEnds().contains(trueSucc.next()) && originalMergeTrue.forwardEnds().contains(potentialCompoundIf.trueSuccessor().next()))) {
                            return null;
                        }

                        if (compoundMergerTrue.phis().count() > 0) {
                            // different values floating on the branches into the phi, we cannot
                            // remove it
                            return null;
                        }

                        FixedNode newCompoundTrueSucc = compoundMergerTrue.next();
                        AbstractBeginNode deadAbstractBeginNode = potentialCompoundIf.falseSuccessor();
                        FixedNode newCompoundFalseSucc = deadAbstractBeginNode.next();

                        if (newCompoundTrueSucc instanceof LoopEndNode || newCompoundFalseSucc instanceof LoopEndNode) {
                            return null;
                        }

                        ifnode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, ifnode.graph(), "After XooY rewrite");

                        // remove connection to them
                        deadAbstractBeginNode.setNext(null); // false succ
                        compoundMergerTrue.setNext(null); // true succ

                        trueSucc.setNext(newCompoundTrueSucc);
                        falseSucc.setNext(newCompoundFalseSucc);
                        deadAbstractBeginNode.replaceAtUsages(falseSucc);
                        ifnode.setCondition(ifnode.graph().addWithoutUnique(new CompoundConditionNode(originalCondition, potentialComoundCondition, CompoundOp.XooY)));

                        GraphUtil.killCFG(potentialCompoundIf);

                        ifnode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, ifnode.graph(), "After XooY rewrite");

                        return ifnode;
                    }
                }

            }
        }
        return null;
    }
}
