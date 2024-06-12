/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hightiercodegen.reconstruction.stackifier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.hightiercodegen.reconstruction.StackifierData;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.blocks.LabeledBlock;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.CatchScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.IfScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.LoopScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.Scope;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.ScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.SwitchScopeContainer;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;

/**
 * This class computes {@link Scope}s for then-else branches, catch-blocks and cases of a switch
 * that are necessary for an optimization of the stackifier algorithm. This optimization pulls basic
 * blocks inside a then/else-branch/catch-block/case-statement instead of placing them after the
 * if/catch/switch statement and doing jumps via {@link LabeledBlock}s. The explanation below
 * focuses on then/else-branches but also applies to catch-blocks and case statements.
 *
 * Here is an example to compare the basic stackifier algorithm with the optimization - Suppose we
 * have the Java method:
 *
 * <pre>
 * if (A()) {
 *     B();
 * } else {
 *     C();
 * }
 * D();
 * </pre>
 *
 * the stackifier algorithm without the optimization would produce:
 *
 * <pre>
 * block1: {
 *     block0: {
 *         if (A()) {
 *
 *         } else {
 *             break block0;
 *         }
 *         B();
 *         break block1;
 *     } // end of block0
 *     C();
 * } // end of block1
 * D();
 * </pre>
 *
 * This class looks for then/else-branches that only have the {@link IfNode} as their predecessor.
 * For these cases we can pull generated code into the then/else branch instead of producing a
 * {@link LabeledBlock} and outputting a labeled {@code break} statement. If we apply this
 * optimization to the above example, we would get:
 *
 * <pre>
 * block0: {
 *     if (A()) {
 *         B();
 *         break block0;
 *     } else {
 *         C();
 *     }
 * } // end of block0
 * D();
 * </pre>
 *
 * A much more readable and shorter output. Note that this still produces a superfluous
 * {@link LabeledBlock} and labeled {@code break} statement.
 *
 * Example where the optimization would not be possible: Assume we have the basic blocks A,B and C.
 * Assume that A and B end with an {@link IfNode} and both have C as a successor. In that case the
 * optimization is not possible since C has more than one predecessor, or put differently it is
 * neither dominated by A nor B. In this case we cannot put C into a then/else branch because two
 * different {@code If}s cannot share the same then/else branch.
 *
 * In more general cases we want to pull as much code into the then/else-branch as possible.
 * Therefore, the true/false-successor of the ifNode and all {@link HIRBlock} that are dominated by
 * the true/false-successor are pulled into the then/else branch. This class computes {@link Scope}
 * which contains {@link HIRBlock}s that can be pulled into a then/else branch.
 */
public class StackifierScopeComputation {

    private final ControlFlowGraph cfg;
    /**
     * Care needs to be taken with the {@link Equivalence} because some rely on
     * {@link HIRBlock#hashCode()}. In {@link CFStackifierSortPhase} the {@link HIRBlock} ids are
     * updated. Since {@link HIRBlock#hashCode()} relies on the id, we cannot use an
     * {@link Equivalence} that uses {@link HIRBlock#hashCode()}.
     */
    private final EconomicMap<Node, ScopeContainer> scopes = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);

    public StackifierScopeComputation(StructuredGraph g) {
        this.cfg = g.getLastSchedule().getCFG();
    }

    /**
     * Compute all {@link HIRBlock} that are dominated by the given {@link HIRBlock} and belong to
     * the {@code loopScope}.
     *
     * @param b given basic block
     * @param loopScope loop scope that must contain all block in the returned set
     * @return set of all dominated basic block (including the given basic block)
     */
    private static EconomicSet<HIRBlock> computeDominatedBlocks(HIRBlock b, Scope loopScope) {
        if (loopScope != null && !loopScope.getBlocks().contains(b)) {
            return null;
        }
        // all block that are dominated by b
        EconomicSet<HIRBlock> dominatedBlocksInsideLoop = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        // temporary set of blocks that are dominated by b
        EconomicSet<HIRBlock> dominatedBlocks = EconomicSet.create();
        dominatedBlocksInsideLoop.add(b);
        if (b.getFirstDominated() != null) {
            dominatedBlocks.add(b.getFirstDominated());
        }
        while (!dominatedBlocks.isEmpty()) {
            HIRBlock dominatedBlock = dominatedBlocks.iterator().next();
            dominatedBlocks.remove(dominatedBlock);
            if (loopScope == null || (loopScope.getBlocks().contains(dominatedBlock))) {
                dominatedBlocksInsideLoop.add(dominatedBlock);
            }
            if (dominatedBlock.getFirstDominated() != null) {
                dominatedBlocks.add(dominatedBlock.getFirstDominated());
            }
            if (dominatedBlock.getDominatedSibling() != null) {
                dominatedBlocks.add(dominatedBlock.getDominatedSibling());
            }
        }
        return dominatedBlocksInsideLoop;
    }

    /**
     * Compute all {@link Scope} for {@link IfNode} and {@link CFGLoop}.
     */
    @SuppressWarnings("try")
    public void computeScopes(StackifierData stackifierData) {
        try (DebugContext.Scope scope1 = cfg.graph.getDebug().scope("Stackifier scope computation")) {
            computeLoopScopes();
            computeThenElseScopes();
            computeCatchScopes();
            computeSwitchCases();
            stackifierData.setScopeEntries(scopes);
        }
    }

    private void computeSwitchCases() {
        for (IntegerSwitchNode switchNode : cfg.graph.getNodes().filter(IntegerSwitchNode.class)) {
            computeCaseScopes(switchNode);
        }
    }

    private void computeCaseScopes(IntegerSwitchNode switchNode) {
        HIRBlock switchBlock = cfg.blockFor(switchNode);
        SwitchScopeContainer switchScopes = new SwitchScopeContainer(new Scope[switchNode.blockSuccessorCount()]);
        scopes.put(switchNode, switchScopes);
        for (int i = 0; i < switchNode.blockSuccessorCount(); i++) {
            HIRBlock caseBlock = cfg.blockFor(switchNode.blockSuccessor(i));
            assert AbstractControlFlowGraph.dominates(switchBlock, caseBlock);
            CFGLoop<HIRBlock> loop = switchBlock.getLoop();
            EconomicSet<HIRBlock> blocks = computeScopeBlocks(switchBlock, caseBlock, loop);
            if (blocks != null) {
                Scope scope = new Scope(blocks, switchBlock);
                switchScopes.getCaseScopes()[i] = scope;
            }
        }
    }

    private void computeCatchScopes() {
        for (WithExceptionNode invokeNode : cfg.graph.getNodes().filter(WithExceptionNode.class)) {
            AbstractBeginNode begNode = invokeNode.exceptionEdge();
            HIRBlock invokeBlock = cfg.blockFor(invokeNode);
            EconomicSet<HIRBlock> blocks = computeScopeBlocks(invokeBlock, cfg.blockFor(begNode), invokeBlock.getLoop());
            var scope = blocks == null ? null : new Scope(blocks, invokeBlock);
            scopes.put(invokeNode, new CatchScopeContainer(scope));
        }
    }

    /**
     * Compute all loop scopes.
     */
    private void computeLoopScopes() {
        for (CFGLoop<HIRBlock> loop : cfg.getLoops()) {
            EconomicSet<HIRBlock> loopBlocks = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
            loopBlocks.addAll(loop.getBlocks());
            Scope loopScope = new Scope(loopBlocks, loop.getHeader());
            scopes.put(loop.getHeader().getBeginNode(), new LoopScopeContainer(loopScope));
            cfg.graph.getDebug().log("%s", loopScope);
        }
    }

    /**
     * Compute for all {@link IfNode} the scopes if possible.
     */
    private void computeThenElseScopes() {
        for (IfNode ifNode : cfg.graph.getNodes(IfNode.TYPE)) {
            HIRBlock ifBlock = cfg.blockFor(ifNode);
            Node trueSuccessor = ifNode.trueSuccessor();
            Node falseSuccessor = ifNode.falseSuccessor();
            CFGLoop<HIRBlock> loop = ifBlock.getLoop();
            EconomicSet<HIRBlock> blocks;
            blocks = computeScopeBlocks(ifBlock, cfg.blockFor(trueSuccessor), loop);
            Scope thenScope = null;
            if (blocks != null) {
                thenScope = new Scope(blocks, ifBlock);
            }
            blocks = computeScopeBlocks(cfg.blockFor(ifNode), cfg.blockFor(falseSuccessor), loop);
            Scope elseScope = null;
            if (blocks != null) {
                elseScope = new Scope(blocks, ifBlock);
            }
            scopes.put(ifNode, new IfScopeContainer(thenScope, elseScope));
        }
    }

    /**
     * Compute a set of basic blocks that can form a scope. If the optimization cannot be applied,
     * nothing is done.
     *
     * The scope is only computed if {@code successor} has only one predecessor, i.e. it is
     * dominated by its {@code startBlock}. Otherwise, we cannot pull the successor into a scope of
     * the {@code startBlock} because it is reachable from somewhere else in the IR.
     *
     * The scope is the union of the successor and all basic blocks that are dominated by the
     * successor. If the scope is inside a loop, only basic blocks that belong to the loop are in
     * the scope.
     *
     * @param startBlock basic block containing a node that starts a scope, e.g. {@link IfNode},
     *            {@link IntegerSwitchNode}
     *
     * @param successor of {@code startBlock}
     */
    private EconomicSet<HIRBlock> computeScopeBlocks(HIRBlock startBlock, HIRBlock successor, CFGLoop<HIRBlock> loop) {
        if (AbstractControlFlowGraph.dominates(startBlock, successor)) {
            Scope loopScope;
            if (loop == null) {
                loopScope = null;
            } else {
                loopScope = ((LoopScopeContainer) scopes.get(loop.getHeader().getBeginNode())).getLoopScope();
                assert loopScope != null;
            }
            EconomicSet<HIRBlock> dominatedBlocks = computeDominatedBlocks(successor, loopScope);
            assert dominatedBlocks == null || !dominatedBlocks.isEmpty();
            return dominatedBlocks;
        }
        return null;
    }
}
