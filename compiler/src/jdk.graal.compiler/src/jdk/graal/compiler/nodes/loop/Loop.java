/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.loop;

import static jdk.graal.compiler.phases.common.util.LoopUtility.isNumericInteger;

import jdk.vm.ci.code.CodeUtil;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.nodes.util.UnsignedIntegerHelper;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.extended.GuardedUnsafeLoadNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.core.common.NumUtil;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractDeoptimizeNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.debug.NeverStripMineNode;
import jdk.graal.compiler.nodes.debug.NeverWriteSinkNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

/**
 * Extra loop data for a loop in the IR. This includes data on which nodes belong to a loop, counted
 * loop information if the compiler detects it as a counted loop. Data about induction variables,
 * parent loops and much more.
 *
 * A note on the relation of {@link Loop}, {@link LoopBeginNode} and {@link CFGLoop} in the Graal
 * IR.
 *
 * A {@link Loop} is a data structure used by the optimizer to reason about a loop. It encapsulates
 * machinery to compute which (floating) nodes belong to a loop as well as API to duplicate, copy,
 * etc a loop.
 *
 * In contrast a {@link LoopBeginNode} is a marker node in the IR to signal the start of a loop
 * structure. A {@link Loop} is a temporary data structure while the {@link LoopBeginNode} is
 * permanent. One can compute multiple {@link Loop} for a given {@link LoopBeginNode} loop in the
 * IR.
 *
 * A {@link CFGLoop} is an encapsulation of concepts related to a loop in the context of a
 * {@link ControlFlowGraph}. It pulls together the {@link HIRBlock} of a loop and computes extra
 * data like depth and child loops etc. necessary to even compute a {@link Loop} for the optimizer.
 *
 * IN the bigger picture a {@link LoopBeginNode} in the IR signals that a loop data structure
 * begins. While computing a {@link ControlFlowGraph} we build the context data structure
 * {@link CFGLoop} which is the necessary CFG abstraction to compute a {@link Loop} for the
 * optimizer.
 *
 * Context data for a loop that needs to be preserved over the entire course of compilation is
 * attached to a {@link LoopBeginNode} because that is the only permanent data storage associated
 * with a loop.
 */
public class Loop {
    public static class Options {
        //@formatter:off
        @Option(help = "Applies counted loop optimization to tail-counted loops.", type = OptionType.User)
        public static final OptionKey<Boolean> DetectInvertedLoopsAsCounted = new OptionKey<>(true);
        //@formatter:on
    }

    /**
     * The corresponding {@link ControlFlowGraph} loop data structure.
     */
    protected final CFGLoop<HIRBlock> cfgLoop;
    /**
     * The corresponding fragment that describes the body nodes of this loop.
     */
    protected LoopFragmentInside inside;
    /**
     * The corresponding fragment that describes the entire nodes in this loop.
     */
    protected LoopFragmentWhole whole;
    /**
     * If this loop is counted, a link to the counted loop info.
     */
    protected CountedLoopInfo counted;
    /**
     * A link to the enclosing loops data structure that contains information about all loops in the
     * given graph.
     */
    protected LoopsData data;
    /**
     * All {@code InductionVariable} of this loop, indexed by their operation node.
     */
    protected EconomicMap<Node, InductionVariable> ivs;
    /**
     * Indicates if we already ran counted loop detection on this loop.
     */
    protected boolean countedLoopChecked;
    protected int size = -1;

    protected Loop(CFGLoop<HIRBlock> loop, LoopsData data) {
        this.cfgLoop = loop;
        this.data = data;
    }

    public double localLoopFrequency() {
        return data.getCFG().localLoopFrequency(loopBegin());
    }

    public ProfileData.ProfileSource localFrequencySource() {
        return data.getCFG().localLoopFrequencySource(loopBegin());
    }

    public CFGLoop<HIRBlock> getCFGLoop() {
        return cfgLoop;
    }

    public LoopFragmentInside inside() {
        if (inside == null) {
            inside = new LoopFragmentInside(this);
        }
        return inside;
    }

    public LoopFragmentWhole whole() {
        if (whole == null) {
            whole = new LoopFragmentWhole(this);
        }
        return whole;
    }

    /**
     * Delete all the loop fragments of this loop. This can be necessary when floating nodes in the
     * loop have changed and thus require a recomputation of the fragments.
     */
    public void invalidateFragments() {
        inside = null;
        whole = null;
    }

    /**
     * Not only invalidate fragments but also the induction variables. This can be necessary when
     * IVs have changed since this loop data was computed.
     */
    public void invalidateFragmentsAndIVs() {
        inside = null;
        whole = null;
        /*
         * IVs might contain dead nodes for inverted loops for prev iterations. We cannot limit this
         * to inverted loops only, since e.g. unrolling can create situations where IVs are still
         * inverted form but the loop body is not since there are no fixed body nodes any more, thus
         * the condition is in a head counted form but the IVs are in inverted (next iteration)
         * form.
         */
        ivs = null;
    }

    public boolean isOutsideLoop(Node n) {
        return !whole().contains(n);
    }

    public LoopBeginNode loopBegin() {
        return (LoopBeginNode) getCFGLoop().getHeader().getBeginNode();
    }

    public FixedNode predecessor() {
        return (FixedNode) loopBegin().forwardEnd().predecessor();
    }

    public FixedNode entryPoint() {
        return loopBegin().forwardEnd();
    }

    public boolean isCounted() {
        assert countedLoopChecked;
        return counted != null;
    }

    public CountedLoopInfo counted() {
        assert countedLoopChecked;
        return counted;
    }

    public Loop parent() {
        if (cfgLoop.getParent() == null) {
            return null;
        }
        return data.loop(cfgLoop.getParent());
    }

    public int size() {
        if (size == -1) {
            size = whole().nodes().count();
        }
        return size;
    }

    public void resetCounted() {
        assert countedLoopChecked;
        ivs = null;
        counted = null;
        countedLoopChecked = false;
    }

    @Override
    public String toString() {
        return (countedLoopChecked && isCounted() ? "CountedLoop [" + counted() + "] " : "Loop ") + "(depth=" + getCFGLoop().getDepth() + ") " + loopBegin();
    }

    private class InvariantPredicate implements NodePredicate {

        private final Graph.Mark mark;

        InvariantPredicate() {
            this.mark = loopBegin().graph().getMark();
        }

        @Override
        public boolean apply(Node n) {
            if (loopBegin().graph().isNew(mark, n)) {
                // Newly created nodes are unknown. It is invariant if all of its inputs are
                // invariants.
                for (Node input : n.inputs()) {
                    if (!apply(input)) {
                        return false;
                    }
                }
                return true;
            }
            return isOutsideLoop(n);
        }
    }

    /**
     * Reassociates loop invariants by pushing loop variant operands further down the operand tree.
     *
     * <pre>
     *    inv2  var        inv1  inv2
     *       \  /             \  /
     * inv1   +     =>   var   +
     *    \  /             \  /
     *     +                +
     * </pre>
     *
     * Also ensures that loop phis are pushed down the furthest (i.e., used as late as possible) to
     * avoid long dependency chains on register level when calculating backedge values:
     *
     * <pre>
     *     inv  phi        inv   var
     *       \  /             \  /
     *  var   +     =>   phi   +
     *    \  /             \  /
     *     +                +
     * </pre>
     */
    public boolean reassociateInvariants() {
        int count = 0;
        StructuredGraph graph = loopBegin().graph();
        InvariantPredicate invariant = new InvariantPredicate();
        NodeBitMap newLoopNodes = graph.createNodeBitMap();
        var phis = loopBegin().phis();
        for (BinaryArithmeticNode<?> binary : whole().nodes().filter(BinaryArithmeticNode.class)) {
            if (!binary.mayReassociate()) {
                continue;
            }
            // pushing down loop variants will associate loop invariants at the "top"
            ValueNode result = BinaryArithmeticNode.reassociateUnmatchedValues(binary, n -> !invariant.apply(n), NodeView.DEFAULT);
            if (result == binary) {
                // use loop phis as late as possible to shorten the register dependency chains
                result = BinaryArithmeticNode.reassociateUnmatchedValues(binary, n -> n instanceof PhiNode phi && phis.contains(phi), NodeView.DEFAULT);
            }
            if (result != binary) {
                if (!result.isAlive()) {
                    assert !result.isDeleted();
                    result = graph.addOrUniqueWithInputs(result);
                    // Save all new added loop variants.
                    newLoopNodes.markAndGrow(result);
                    for (Node input : result.inputs()) {
                        if (whole().nodes().isNew(input) && !invariant.apply(input)) {
                            newLoopNodes.markAndGrow(input);
                        }
                    }
                }
                binary.replaceAtUsages(result);
                graph.getOptimizationLog().report(Loop.class, "InvariantReassociation", binary);
                GraphUtil.killWithUnusedFloatingInputs(binary);
                count++;
            }
        }
        if (newLoopNodes.isNotEmpty()) {
            whole().nodes().union(newLoopNodes);
        }
        return count != 0;
    }

    public static boolean absStrideIsOne(InductionVariable limitCheckedIV) {
        long constantStride = limitCheckedIV.constantStride();
        return constantStride == -1L || constantStride == 1L;
    }

    public boolean isCfgLoopExit(AbstractBeginNode begin) {
        HIRBlock block = data.getCFG().blockFor(begin);
        return cfgLoop.getDepth() > block.getLoopDepth() || cfgLoop.isNaturalExit(block);
    }

    public LoopsData loopsData() {
        return data;
    }

    public void nodesInLoopBranch(NodeBitMap branchNodes, AbstractBeginNode branch) {
        EconomicSet<AbstractBeginNode> blocks = EconomicSet.create();
        Collection<AbstractBeginNode> exits = new LinkedList<>();
        Queue<HIRBlock> work = new LinkedList<>();
        ControlFlowGraph cfg = loopsData().getCFG();
        NodeBitMap visited = cfg.graph.createNodeBitMap();
        HIRBlock firstSuccBlock = cfg.blockFor(branch);
        work.add(firstSuccBlock);
        while (!work.isEmpty()) {
            HIRBlock b = work.remove();
            if (getCFGLoop().isLoopExit(b)) {
                assert !exits.contains(b.getBeginNode());
                exits.add(b.getBeginNode());
            } else if (blocks.add(b.getBeginNode())) {
                HIRBlock d = b.getFirstDominated();
                while (d != null) {
                    /*
                     * if the post dominator is reachable via a branch block it means it was a merge
                     * of the current split. this is generally not part of the branch, but after the
                     * branch.
                     */
                    if (cfgLoop.getBlocks().contains(d) && firstSuccBlock.getPostdominator() != d) {
                        if (!visited.isMarked(d.getBeginNode())) {
                            visited.mark(d.getBeginNode());
                            work.add(d);
                        }
                    }
                    d = d.getDominatedSibling();
                }
            }
        }
        LoopFragment.computeNodes(branchNodes, branch.graph(), this, blocks, exits);
    }

    public EconomicMap<Node, InductionVariable> getInductionVariables() {
        return getInductionVariables(false, false);
    }

    /**
     * Gets the collection of all {@link InductionVariable} of this loop indexed by their
     * {@link InductionVariable#valueNode()}.
     *
     * If {@code forceReset==true} throws away any previously computed induction variables. The
     * collection of the IVs uses the current data in {@link #inside} and thus can change depending
     * on when IVs are computed.
     *
     * If {@code computeDeoptLoopExitIVs==true} computes a potentially broader set of IVs. It
     * includes those that have usages outside a loop if such a usage is a framestate used by a
     * {@link DeoptimizeNode}. That is necessary because there is concrete discrepancy in loop nodes
     * between Java bytecode and the actual liveness. See {@link CFGLoop#getNaturalExits()} for
     * details. Every branch leading to a deopt is already outside a loop and thus does not count as
     * usages that drive normal IV collection. Certain optimizations still need to see all induction
     * variables, also those with usages outside. Note that there are potentially IVs that are still
     * not returned by this function if they have usages outside a loop but are not used by a deopt.
     */
    public EconomicMap<Node, InductionVariable> getInductionVariables(boolean forceReset, boolean computeDeoptLoopExitIVs) {
        if (ivs == null || forceReset) {
            ivs = findInductionVariables(computeDeoptLoopExitIVs);
        }
        return ivs;
    }

    /**
     * Collect all the {@link BasicInductionVariable} variables for the loop and find any induction
     * variables which are {@link DerivedInductionVariable} from the basic ones. An
     * <a href="https://en.wikipedia.org/wiki/Induction_variable">induction variable<a/> is a
     * variable that gets increased/decreased by a fixed != 0 amount every iteration of a loop. The
     * {@code !=0} portion is guaranteed by {@link BasicInductionVariable#direction()}. More
     * importantly an induction variable, by definition, guarantees that the value of the IV is
     * strictly monotonically increasing or decreasing, as long as they don't overflow. A counted
     * loop's counter will never overflow (guaranteed by {@link CountedLoopInfo#maxTripCountNode()})
     * other IVs on the same loop might overflow. The extremum computations are correct even in the
     * presence of overflow
     *
     * Typical examples are:
     *
     * <pre>
     * int basicIV = 0;
     * while (true) {
     *     if (basivIV > limit) {
     *         break;
     *     }
     *     int derivedOffsetIV = basicIV + 3;
     *     int derivedScaledIV = basicIV * 17;
     *     long derivedConvertedIV = (long) basicIV;
     *     basicIV = basicIV + 1;
     *     // and many more
     * }
     * </pre>
     *
     * @return a map from node to induction variable
     */
    private EconomicMap<Node, InductionVariable> findInductionVariables(boolean computeDeoptLoopExitIVs) {
        EconomicMap<Node, InductionVariable> currentIvs = EconomicMap.create(Equivalence.IDENTITY);

        // first find basic induction variables
        Queue<InductionVariable> scanQueue = new LinkedList<>();
        LoopBeginNode loopBegin = this.loopBegin();
        AbstractEndNode forwardEnd = loopBegin.forwardEnd();
        for (PhiNode phi : loopBegin.valuePhis()) {
            ValueNode backValue = phi.singleBackValueOrThis();
            if (backValue == phi) {
                continue;
            }
            ValueNode stride = calcOffsetTo(this, backValue, phi, false);
            if (stride != null) {
                BasicInductionVariable biv = new BasicInductionVariable(this, (ValuePhiNode) phi, phi.valueAt(forwardEnd), stride, (BinaryArithmeticNode<?>) backValue);
                currentIvs.put(phi, biv);
                scanQueue.add(biv);
            }
        }

        // now compute derived ones
        while (!scanQueue.isEmpty()) {
            InductionVariable baseIv = scanQueue.remove();
            ValueNode baseIvNode = baseIv.valueNode();
            for (ValueNode op : baseIvNode.usages().filter(ValueNode.class)) {
                if (this.isOutsideLoop(op)) {
                    boolean needExitIVs = computeDeoptLoopExitIVs && hasDeoptUsage(op, this);
                    if (!needExitIVs) {
                        continue;
                    }
                }
                if (op.hasExactlyOneUsage() && op.usages().first() == baseIvNode) {
                    /*
                     * This is just the base induction variable increment with no other uses so
                     * don't bother reporting it.
                     */
                    continue;
                }
                InductionVariable iv = null;
                ValueNode offset = calcOffsetTo(this, op, baseIvNode, true);
                ValueNode scale;
                if (offset != null) {
                    iv = new DerivedOffsetInductionVariable(this, baseIv, offset, (BinaryArithmeticNode<?>) op);
                } else if (op instanceof NegateNode) {
                    iv = new DerivedScaledInductionVariable(this, baseIv, (NegateNode) op);
                } else if ((scale = calcScaleTo(this, op, baseIvNode)) != null) {
                    iv = new DerivedScaledInductionVariable(this, baseIv, scale, op);
                } else {
                    boolean isValidConvert = op instanceof PiNode || op instanceof SignExtendNode;
                    if (!isValidConvert && op instanceof ZeroExtendNode) {
                        ZeroExtendNode zeroExtendNode = (ZeroExtendNode) op;
                        /* A zero extension only preserves its input's numeric value if the input is not negative. */
                        isValidConvert = ((IntegerStamp) zeroExtendNode.getValue().stamp(NodeView.DEFAULT)).isPositive();
                    }
                    if (!isValidConvert && op instanceof NarrowNode) {
                        NarrowNode narrow = (NarrowNode) op;
                        isValidConvert = narrow.isLossless();
                    }

                    if (isValidConvert) {
                        iv = new DerivedConvertedInductionVariable(this, baseIv, op.stamp(NodeView.DEFAULT), op);
                    }
                }

                if (iv != null) {
                    currentIvs.put(op, iv);
                    scanQueue.offer(iv);
                }
            }
        }
        return currentIvs;
    }

    /**
     * Maximum search depth for recursive application of deopt usage search. Used when state usages
     * are found. See {@link #hasDeoptUsage(ValueNode, Loop)} for details.
     */
    private static final int MAX_DEPTH_DEOPT_USAGES = 4;

    private static boolean hasDeoptUsage(ValueNode op, Loop loop) {
        for (Node usage : op.usages()) {
            if (hasDeoptUsage(usage, loop, 0)) {
                return true;
            }
        }
        return hasDeoptUsage(op, loop, 0);
    }

    /**
     * Determine if the given operation denoted as {@code op} represents an induction variable that
     * is used in a deopt path inside the loop body. With Java bytecode liveness is always a
     * problem. There is a discrepancy between bytecode level loops and actual, natural loop exits
     * (see {@link CFGLoop#getNaturalExits()} for details). This value might only be used in a deopt
     * path outside the loop == a natural exit path. Still create IVs if wanted for such cases so
     * certain optimizations can use that information.
     *
     * If {@code depth} is reached this method returns {@code false} indicating no usage is found.
     * This however is imprecise. A return value of {@code false} can indicate there was no usage or
     * the depth filter was hit and we DO NOT KNOW.
     */
    private static boolean hasDeoptUsage(Node usage, Loop loop, int depth) {
        if (depth >= MAX_DEPTH_DEOPT_USAGES) {
            return false;
        }
        if (usage instanceof FrameState fs) {
            for (Node fsUsage : fs.usages()) {
                if (fsUsage instanceof AbstractDeoptimizeNode deopt) {
                    HIRBlock deoptBlock = loop.getCFGLoop().getHeader().getCfg().blockFor(deopt);
                    while (deoptBlock != null) {
                        if (loop.getCFGLoop().getBlocks().contains(deoptBlock)) {
                            return true;
                        }
                        deoptBlock = deoptBlock.getDominator();
                    }
                } else if (fsUsage instanceof FrameState stateUsage && stateUsage.outerFrameState() == fs) {
                    // go into recursion another level
                    if (hasDeoptUsage(fsUsage, loop, depth + 1)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Determines if {@code op} is using {@code base} as an input. If so, determines if the other
     * input of {@code op} is loop invariant with respect to {@code loop}. This marks one
     * fundamental requirement for an offset based induction variable of a loop.
     *
     * <pre>
     * int basicIV = 0;
     * while (true) {
     *     if (basivIV > limit) {
     *         break;
     *     }
     *     basicIV = basicIV + stride;
     * }
     * </pre>
     *
     * In the example above the {@code PhiNode} basicIV would be {@code base}, the add operation
     * would be {@code op} and the loop invariant stride would be the other input to {@code op} that
     * is {@code != base}.
     *
     * Note that this method is also used to compute {@code DerivedOffsetInductionVariable} which
     * are offset of a {@code BasicInductionVariable}.
     *
     * <pre>
     * int basicIV = 0;
     * while (true) {
     *     if (basicIV > limit) {
     *         break;
     *     }
     *     int offsetIV = basicIV + 123;
     *     basicIV = basicIV + stride;
     * }
     * </pre>
     *
     * Such an example can be seen in {@code offsetIV} where {@code base} is the {@code basicIV},
     * i.e., the {@link PhiNode}.
     *
     * An offset can be positive or negative as well and involve addition or subtraction. This can
     * cause some interesting patterns. In general we need to handle the following cases:
     *
     * <pre>
     * int basicIV = 0;
     *
     * while (true) {
     *     if (compare(basicIV, limit)) {
     *         break;
     *     }
     *     // case 1 - addition with positive stride == addition
     *     basicIV = basicIV + stride;
     *     // case 2 - addition with negative stride == subtraction
     *     basicIV = basicIV + (-stride);
     *     // case 3 - subtraction with positive stride == subtraction
     *     basicIV = basicIV - stride;
     *     // case 4 - subtraction with negative stride == addition
     *     basicIV = basicIV - (-stride);
     * }
     * </pre>
     *
     * While one might assume that these patterns would be transformed into their canonical form by
     * {@link CanonicalizerPhase} it is never guaranteed that a full canonicalizer has been run
     * before loop detection is done. Thus, we have to handle all patterns here.
     *
     * Note that while addition is commutative and thus can handle both inputs mirrored, the same is
     * not true for subtraction.
     *
     * For addition the following patterns are all valid IVs
     *
     * <pre>
     * int basicIV = 0;
     * while (true) {
     *     if (compare(basicIV, limit)) {
     *         break;
     *     }
     *     // case 1 - loop invariant input is y input of add
     *     basicIV = basicIV + stride;
     *     // case 2 - loop invariant input is x input of add
     *     basicIV = stride + basicIV;
     * }
     * </pre>
     *
     * because addition is commutative.
     *
     * For subtraction, this is not correct when detecting the basic induction variable.
     *
     * Here is an example of a regular down-counted loop, with a subtraction basic IV operation:
     *
     * <pre>
     * int basicIV = init;
     * while (true) {
     *     if (basicIV >= 0) {
     *         break;
     *     }
     *     basicIV = basicIV - 2;
     * }
     * </pre>
     *
     * As can be seen, the IV above has the values [init, init - 2, init - 4, ...].
     *
     * In contrast, here is an example of an invalid induction variable:
     *
     * <pre>
     * int basicIV = 0;
     * while (true) {
     *     if (basicIV <= limit) {
     *         break;
     *     }
     *     basicIV = 2 - basicIV;
     * }
     * </pre>
     *
     * because the IV value is [0,2-0=2,2-2=0,2,0,2,0, etc]. This alternating pattern is by
     * definition not an induction variable. The reason for this difference is that while addition
     * is commutative, subtraction is not. Thus we only allow subtraction of the form
     * {@code base - stride/offset}.
     *
     * Derived induction variables with mirrored inputs however are perfectly fine because the base
     * IV is a regular (non-alternating) one. So the loop
     *
     * <pre>
     * int basicIV = 0;
     * while (true) {
     *     if (basicIV >= limit) {
     *         break;
     *     }
     *     int otherIV = 124555 - basicIV;
     *     basicIV = basicIV + 1;
     * }
     * </pre>
     *
     * contains the correct IV {@code otherIV} which is an induction variable as per definition: it
     * increases/decreases its value by a fixed amount every iteration (that amount being the stride
     * of base IV).
     */
    private static ValueNode calcOffsetTo(Loop loop, ValueNode opNode, ValueNode base, boolean forDerivedIV) {
        if (isNumericInteger(opNode) && (opNode instanceof AddNode || opNode instanceof SubNode)) {
            BinaryArithmeticNode<?> arithOp = (BinaryArithmeticNode<?>) opNode;
            BinaryOp<?> op = arithOp.getArithmeticOp();
            if (arithOp.getX() == base && loop.isOutsideLoop(arithOp.getY())) {
                return arithOp.getY();
            } else if ((op.isCommutative() || forDerivedIV) && arithOp.getY() == base && loop.isOutsideLoop(arithOp.getX())) {
                return arithOp.getX();
            }
        }
        return null;
    }

    /**
     * Determine if the given {@code op} represents a {@code DerivedScaledInductionVariable}
     * variable with respect to {@code base}.
     *
     * See {@link Loop#calcOffsetTo(Loop, ValueNode, ValueNode, boolean)}. Multiplication is
     * commutative so the logic of addition applies here.
     */
    private static ValueNode calcScaleTo(Loop loop, ValueNode op, ValueNode base) {
        if (op instanceof MulNode) {
            MulNode mul = (MulNode) op;
            if (mul.getX() == base && loop.isOutsideLoop(mul.getY())) {
                return mul.getY();
            } else if (mul.getY() == base && loop.isOutsideLoop(mul.getX())) {
                return mul.getX();
            }
        }
        if (op instanceof LeftShiftNode) {
            LeftShiftNode shift = (LeftShiftNode) op;
            if (shift.getX() == base && shift.getY().isConstant()) {
                return ConstantNode.forIntegerStamp(base.stamp(NodeView.DEFAULT), 1L << shift.getY().asJavaConstant().asInt(), base.graph());
            }
        }
        return null;
    }

    /**
     * Deletes any nodes created within the scope of this object that have no usages.
     */
    public void deleteUnusedNodes() {
        if (ivs != null) {
            for (InductionVariable iv : ivs.getValues()) {
                iv.deleteUnusedNodes();
            }
        }
    }

    public static boolean canDuplicateLoopNode(Node node) {
        /*
         * Control flow anchored nodes must not be duplicated.
         */
        if (node instanceof ControlFlowAnchored) {
            return false;
        }
        if (node instanceof FrameState) {
            FrameState frameState = (FrameState) node;
            /*
             * Exception handling frame states can cause problems when they are duplicated and one
             * needs to create a framestate at the duplication merge.
             */
            if (frameState.isExceptionHandlingBCI()) {
                return false;
            }
        }
        return true;
    }

    public static boolean canStripMineLoopNode(Node node) {
        if (node instanceof NeverStripMineNode) {
            return false;
        }
        return true;
    }

    public static boolean canWriteSinkLoopNode(Node node) {
        return !(node instanceof NeverWriteSinkNode);
    }

    /**
     * @return true if all nodes in the loop can be duplicated.
     */
    public boolean canDuplicateLoop() {
        for (Node node : inside().nodes()) {
            if (!canDuplicateLoopNode(node)) {
                return false;
            }
        }
        return true;
    }

    public boolean canStripMine() {
        for (Node node : inside().nodes()) {
            if (!canStripMineLoopNode(node)) {
                return false;
            }
        }
        return true;
    }

    public boolean canWriteSink() {
        for (Node node : inside().nodes()) {
            if (!canWriteSinkLoopNode(node)) {
                return false;
            }
        }
        return true;
    }

    public boolean detectCounted(boolean ignoreProtection) {
        return analyzeCounted(ignoreProtection) == CountedLoopDetectionResult.COUNTED;
    }

    public CountedLoopDetectionResult freshAnalyzeCounted() {
        resetCounted();
        return analyzeCounted(false);
    }

    public static boolean willBecomeLoopInvariantAfterFloatingReads(Node n, Loop loop, NodeBitMap knownToBeInvariantNodes) {
        NodeStack ns = new NodeStack();
        ns.push(n);
        while (!ns.isEmpty()) {
            Node cur = ns.pop();
            if (knownToBeInvariantNodes.isMarked(cur)) {
                continue;
            } else if (loop.isOutsideLoop(cur)) {
                continue;
            } else if (loop.loopBegin().isPhiAtMerge(cur)) {
                return false;
            } else if (cur instanceof LoadFieldNode || cur instanceof GuardedUnsafeLoadNode || cur instanceof LoadIndexedNode || cur instanceof PiNode || cur instanceof ArrayLengthNode ||
                            cur instanceof FixedGuardNode || cur instanceof LogicNode) {
                if (cur instanceof LoadFieldNode) {
                    LoadFieldNode load = (LoadFieldNode) cur;
                    if (load.isStatic() && !load.ordersMemoryAccesses()) {
                        // will float
                        continue;
                    }
                }
                for (Node input : cur.inputs()) {
                    ns.push(input);
                }
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * Determine if after {@link FloatingReadPhase} this {@link IfNode} can become the new counted
     * loop exit.
     */
    public boolean canBecomeLimitTestAfterFloatingReads(IfNode ifNode) {
        FixedNode loopStartNode = loopBegin().next();
        NodeBitMap knownToBeInvariantNodes = ifNode.graph().createNodeBitMap();
        while (true) { // TERMINATION ARGUMENT: processing next nodes until exit criteria is reached
            CompilationAlarm.checkProgress(ifNode.graph());
            if (canSkipBodyNode(loopStartNode, true)) {
                // nodes that will float away between loop begin and counted check, not necessarily
                // loop invariant
                if (loopStartNode instanceof FixedGuardNode && willBecomeLoopInvariantAfterFloatingReads(loopStartNode, this, knownToBeInvariantNodes)) {
                    knownToBeInvariantNodes.mark(loopStartNode);
                }
                loopStartNode = ((FixedWithNextNode) loopStartNode).next();
                continue;
            } else if (willBecomeLoopInvariantAfterFloatingReads(loopStartNode, this, knownToBeInvariantNodes)) {
                // nodes that will truely become invariant
                knownToBeInvariantNodes.mark(loopStartNode);
                loopStartNode = ((FixedWithNextNode) loopStartNode).next();
                continue;
            }
            break;
        }
        if (loopStartNode instanceof IfNode && loopStartNode == ifNode) {
            CountedLoopInfo countedLoopInfo = detectCountedLoopIf((IfNode) loopStartNode, IfPosition.LoopStart, false, knownToBeInvariantNodes);
            if (countedLoopInfo != null) {
                if (countedLoopInfo.isInvertedLoopComplexSignednessRange()) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    public CountedLoopDetectionResult analyzeCounted(boolean ignoreProtection) {
        if (countedLoopChecked) {
            return CountedLoopDetectionResult.create(isCounted());
        }
        countedLoopChecked = true;
        LoopBeginNode loopBegin = loopBegin();
        if (loopBegin.countedLoopDisabled()) {
            return CountedLoopDetectionResult.NOT_COUNTED;
        }

        /*
         * We always try to prefer head counted loops over inverted ones for phase ordering reasons,
         * except if we inverted a loop, then we should reason about it (for the sake of guard
         * optimizations) in its inverted form.
         */
        if (loopBegin.isCompilerInverted()) {
            // try tail counted detection first
            InvertedAnalysisResult invertedCountedLoopInfo = detectInvertedCountedLoop(ignoreProtection);
            if (invertedCountedLoopInfo.result == CountedLoopDetectionResult.COUNTED) {
                counted = invertedCountedLoopInfo.info;
                return CountedLoopDetectionResult.COUNTED;
            }
        }

        FixedNode loopStartNode = loopBegin().next();
        while (canSkipBodyNode(loopStartNode, true)) {
            loopStartNode = ((FixedWithNextNode) loopStartNode).next();
        }
        if (loopStartNode instanceof IfNode) {
            CountedLoopInfo countedLoopInfo = detectCountedLoopIf((IfNode) loopStartNode, IfPosition.LoopStart, false);
            if (countedLoopInfo != null) {
                counted = countedLoopInfo;
                return CountedLoopDetectionResult.COUNTED;
            }
        }
        InvertedAnalysisResult invertedCountedLoopInfo = detectInvertedCountedLoop(ignoreProtection);
        if (invertedCountedLoopInfo.result == CountedLoopDetectionResult.COUNTED) {
            counted = invertedCountedLoopInfo.info;
            return CountedLoopDetectionResult.COUNTED;
        } else {
            return invertedCountedLoopInfo.result;
        }
    }

    /**
     * Determines if the given node can be skipped when analyzing counted loops. Skip-able body
     * nodes can appear between the {@link LoopBeginNode} and the loop header.
     */
    public static boolean canSkipBodyNode(FixedNode bodyNode, boolean skipGuards) {
        return (skipGuards && bodyNode instanceof FixedGuardNode) || bodyNode instanceof ValueAnchorNode || bodyNode instanceof FullInfopointNode;
    }

    private static class InvertedAnalysisResult {
        CountedLoopInfo info;
        CountedLoopDetectionResult result;

        InvertedAnalysisResult(CountedLoopInfo info, CountedLoopDetectionResult result) {
            this.info = info;
            this.result = result;
        }

        static InvertedAnalysisResult createCounted(CountedLoopInfo info) {
            return new InvertedAnalysisResult(info, CountedLoopDetectionResult.COUNTED);
        }

        private static final InvertedAnalysisResult NotCounted = new InvertedAnalysisResult(null, CountedLoopDetectionResult.NOT_COUNTED);

        private static final InvertedAnalysisResult NotProtected = new InvertedAnalysisResult(null, CountedLoopDetectionResult.MISSING_PROTECTION);

        private static final InvertedAnalysisResult UnsignedNegative = new InvertedAnalysisResult(null, CountedLoopDetectionResult.UNSIGNED_INIT_POTENTIALLY_NEGATIVE);

        private static final InvertedAnalysisResult NotProtectedUnsignedNegative = new InvertedAnalysisResult(null, CountedLoopDetectionResult.UNSIGNED_INIT_POTENTIALLY_NEGATIVE_MISSING_PROTECTION);

    }

    public enum CountedLoopDetectionResult {
        /**
         * The loop is not counted for various reasons.
         */
        NOT_COUNTED,
        /**
         * The inverted loop cannot be detected as counted because it is missing a protection.
         */
        MISSING_PROTECTION,
        /**
         * The inverted loop cannot be detected as counted because it is an unsigned comparison with
         * a complex iteration range potentially including negative values due to integer overflow.
         */
        UNSIGNED_INIT_POTENTIALLY_NEGATIVE,
        UNSIGNED_INIT_POTENTIALLY_NEGATIVE_MISSING_PROTECTION,
        /**
         * The loop is counted.
         */
        COUNTED;

        public static CountedLoopDetectionResult create(boolean counted) {
            return counted ? COUNTED : NOT_COUNTED;
        }
    }

    public boolean detectCounted() {
        if (detectCounted(false)) {
            assert counted.assertUnsignedIVsAreSane();
            return true;
        }
        return false;
    }

    public CountedLoopInfo detectCountedLoopIf(IfNode ifNode, IfPosition ifPosition, boolean ignoreProtection) {
        return detectCountedLoopIf(ifNode, ifPosition, ignoreProtection, null);
    }

    /**
     * IMPORTANT NOTE: Enhanced loop detection
     *
     * Loop detection also considers inverted (tail-counted) loops as counted loops. The
     * detection is closely coupled (limit, stride, etc.) with the regular loop detection, thus this
     * method shares parts of the regular counted-loop detection logic.
     *
     * In the following documentation the terms "head counted loop" and "tail counted loop" are
     * used.
     *
     * A "head counted" loop is a loop that starts with the counted loop condition first, i.e. the
     * condition is at the head of the loop.
     *
     * <pre>
     * int phi = 0;
     * while (true) {
     *     if (phi >= max)
     *         break; // head position
     *     // rest of the loop body
     *     phi++;
     * }
     * </pre>
     *
     * In contrast, a "tail counted" loop is a loop that ends with the counted loop condition.
     *
     * <pre>
     * int phi = 0;
     * while (true) {
     *     // loop body
     *     phi++;
     *     if (phi >= max)
     *         break; // tail position
     * }
     * </pre>
     *
     * Tail counted loops are also referred to as inverted loops, i.e., the counted loop condition
     * is "inverted" == at the end.
     *
     * ####################################################################################
     *
     * We need to have a clear definition when to use the inverted (prev iteration) concept for a
     * counted loop and when the head counted one.
     *
     * All loops will be considered head counted except if the loop is in inverted form, i.e., there
     * are fixed nodes between the body and the exit check.
     *
     * Example:
     *
     * <pre>
     *    int phi=0;
     *    while (true) {
     *     fixedNode1;
     *     fixedNode2;
     *     ...
     *     fixedNodeN;
     *     phi++;
     *     if (phi >= max) break; // tail position
     *    }
     * </pre>
     *
     * However, this is also a valid inverted loop, though the iv checked in the counted loop check
     * is not the next iteration value.
     *
     * <pre>
     *    int phi=0;
     *    while (true) {
     *     fixedNode1;
     *     fixedNode2;
     *     ...
     *     fixedNodeN;
     *     if (phi >= max) break; // tail position
     *     phi++;
     *    }
     * </pre>
     *
     * ####################################################################################
     *
     * If {@code ifPosition} is {@link IfPosition#LoopEnd} and {@code ignoreProtection} is
     * {@code false}, this method is optimistic that the loop will be protected. The caller
     * <em>must</em> check for that protection.
     */
    CountedLoopInfo detectCountedLoopIf(IfNode ifNode, IfPosition ifPosition, boolean ignoreProtection, NodeBitMap knownToBeInvariantNodesAfter) {
        LoopBeginNode loopBegin = loopBegin();
        boolean negated = false;
        if (!isCfgLoopExit(ifNode.falseSuccessor())) {
            if (!isCfgLoopExit(ifNode.trueSuccessor())) {
                return null;
            }
            negated = true;
        }
        LogicNode ifTest = ifNode.condition();
        CountedLoopConditionData c = detectCountedLoopIfFromCondition(this, ifTest, negated, ifPosition, ignoreProtection, knownToBeInvariantNodesAfter);
        if (c == null) {
            return null;
        }
        AbstractBeginNode body = negated ? ifNode.falseSuccessor() : ifNode.trueSuccessor();
        InductionVariable bodyUsedIV = c.limitCheckedIV;
        switch (ifPosition) {
            case LoopStart:
                break;
            case LoopEnd:
                if (c.bodyUsesPrevIterationIV) {
                    /*
                     * the check is on the next value of the actual IV, only if the IV is
                     * already a derived one
                     */
                    bodyUsedIV = InductionVariableHelper.previousIteration(c.limitCheckedIV);
                }
                /*
                 * If the exit check was (only) found from the loop end, this is an inverted loop,
                 * even if it's empty.
                 */
                body = loopBegin;
                break;
            default:
                throw GraalError.shouldNotReachHere(ifPosition.toString()); // ExcludeFromJacocoGeneratedReport
        }
        return new CountedLoopInfo(this, c.limitCheckedIV, bodyUsedIV, ifNode, c.limit, c.tripCountLimit, c.isLimitIncluded, body, c.unsigned, c.invertedLoopComplexSignednessRange);

    }

    public record CountedLoopConditionData(InductionVariable limitCheckedIV, ValueNode limit, ValueNode tripCountLimit, boolean bodyUsesPrevIterationIV, boolean isLimitIncluded, boolean unsigned,
                    boolean invertedLoopComplexSignednessRange) {

    }

    @SuppressWarnings("fallthrough")
    public static CountedLoopConditionData detectCountedLoopIfFromCondition(Loop loop, LogicNode ifTest, boolean negated, IfPosition ifPosition, boolean ignoreProtection,
                    NodeBitMap knownToBeInvariantNodesAfter) {
        if (!(ifTest instanceof CompareNode)) {
            return null;
        }
        CompareNode compare = (CompareNode) ifTest;
        Condition condition = null;
        InductionVariable limitCheckedIV = null;
        ValueNode limit = null;
        /*
         * In order to determine if a loop should be treated in its inverted form we need to
         * guarantee that the value checked for the exit check is actually a derived one, else its a
         * head counted loop that might look like a tail counted one or a tail counted loop which
         * checks the regular induction variable. See longer comment below.
         */
        boolean bodyUsesPrevIterationIV = false;
        ValueNode x = compare.getX();
        ValueNode y = compare.getY();
        if (loop.isOutsideLoop(x) || knownToBeInvariantNodesAfter != null && knownToBeInvariantNodesAfter.isMarked(x)) {
            limitCheckedIV = loop.getInductionVariables().get(y);
            if (limitCheckedIV != null) {
                condition = compare.condition().asCondition().mirror();
                limit = x;
                if (limitCheckedIV instanceof DerivedInductionVariable) {
                    ValueNode valueNode2 = limitCheckedIV.valueNode();
                    assert y == valueNode2 : y + "!=" + valueNode2;
                    bodyUsesPrevIterationIV = true;
                }
            }
        } else if (loop.isOutsideLoop(y) || knownToBeInvariantNodesAfter != null && knownToBeInvariantNodesAfter.isMarked(y)) {
            limitCheckedIV = loop.getInductionVariables().get(x);
            if (limitCheckedIV != null) {
                condition = compare.condition().asCondition();
                limit = y;
                if (limitCheckedIV instanceof DerivedInductionVariable) {
                    ValueNode valueNode = limitCheckedIV.valueNode();
                    assert x == valueNode : x + " != " + valueNode;
                    bodyUsesPrevIterationIV = true;
                }
            }
        }

        if (condition == null) {
            return null;
        }

        boolean complexSignednessRange = false;
        if (!ignoreProtection) {
            /*
             * Very subtle case that should never happen: The original unsigned compared loop was
             * not spanning the signedness-range from negative to positive but the unrolled one does
             * (or we believe it does): We trust the original logic to determine this, still build
             * any necessary protection control flow and let later counted loop detection (if so)
             * fail.
             */
            complexSignednessRange = invertedLoopComplexSignednessRange(ifPosition, condition, limitCheckedIV, bodyUsesPrevIterationIV);
        }

        if (negated) {
            condition = condition.negate();
        }
        // limit used to compute the max trip count node
        ValueNode tripCountLimit = limit;
        if (!bodyUsesPrevIterationIV && ifPosition == IfPosition.LoopEnd) {
            // inverted loops with no inverted induction variables checked for limit
            assert limitCheckedIV instanceof BasicInductionVariable : limitCheckedIV;
            tripCountLimit = getTripCountLimit(limit, limitCheckedIV);
        }
        final Direction limitCheckedIVDirection = limitCheckedIV.direction();
        if (limitCheckedIVDirection == null) {
            // we do not know which direction the stride goes
            return null;
        }
        boolean isLimitIncluded = false;
        boolean unsigned = false;
        switch (condition) {
            case EQ:
                if (limitCheckedIV.initNode() == limit) {
                    // allow "single iteration" case
                    isLimitIncluded = true;
                } else {
                    return null;
                }
                break;
            case NE: {
                IntegerStamp initStamp = (IntegerStamp) limitCheckedIV.initNode().stamp(NodeView.DEFAULT);
                IntegerStamp limitStamp = (IntegerStamp) tripCountLimit.stamp(NodeView.DEFAULT);
                ValueNode limitCheckedIVValueNode = limitCheckedIV.valueNode();
                IntegerStamp counterStamp = (IntegerStamp) limitCheckedIVValueNode.stamp(NodeView.DEFAULT);
                if (limitCheckedIVDirection == Direction.Up) {
                    if (limitStamp.asConstant() != null && (limitStamp.asConstant().asLong() == counterStamp.upperBound() ||
                                    ifPosition == IfPosition.LoopEnd &&
                                                    limitStamp.asConstant().asLong() == ((IntegerStamp) InductionVariableHelper.nextIteration(limitCheckedIV).valueNode().stamp(
                                                                    NodeView.DEFAULT)).upperBound())) {
                        // signed: i < MAX_INT
                    } else if (limitStamp.asConstant() != null && limitStamp.asConstant().asLong() == counterStamp.unsignedUpperBound() && IntegerStamp.sameSign(initStamp, limitStamp)) {
                        unsigned = true;
                    } else {
                        if (!limitCheckedIV.isConstantStride()) {
                            return null;
                        }
                        boolean strideOK = absStrideIsOne(limitCheckedIV);
                        long constantStride = limitCheckedIV.constantStride();
                        if (!strideOK && constantStride != Long.MIN_VALUE && CodeUtil.isPowerOf2(NumUtil.safeAbs(constantStride))) {
                            /*
                             * Check that we'll hit the limit exactly because the init and limit
                             * values are multiples of the stride. For example, we won't overflow in
                             * a loop like `for (i = 0; i != 10; i += 2)`. But we will miss the
                             * limit and overflow in a loop like `for (i = 0; i != 11; i += 2)`.
                             */
                            long mustBeClearLowBits = CodeUtil.mask(Long.numberOfTrailingZeros(NumUtil.safeAbs(constantStride)));
                            strideOK = (initStamp.mayBeSet() & mustBeClearLowBits) == 0 && (counterStamp.mayBeSet() & mustBeClearLowBits) == 0 && (limitStamp.mayBeSet() & mustBeClearLowBits) == 0;
                        }
                        if (!strideOK) {
                            return null;
                        }
                        if (ifPosition == IfPosition.LoopEnd && ignoreProtection) {
                            /*
                             * Inverted equality checked loop that is yet unprotected
                             */
                        } else if (initStamp.upperBound() > limitStamp.lowerBound()) {
                            /*
                             * This loop will have a positive overflow if init starts out greater
                             * than limit.
                             */
                            if (ifPosition == IfPosition.LoopEnd) {
                                /*
                                 * This is an inverted loop that is a valid counted loop if it's
                                 * protected. Optimistically fall through to return a valid
                                 * CountedLoopInfo. It is the caller's responsibility to
                                 * check for proper protection.
                                 */
                            } else {
                                /*
                                 * An upwards, head-counted loop with condition IV != limit. Look
                                 * for protection by init <= limit, i.e., Not(limit < init).
                                 */
                                LogicNode requiredProtection = LogicNegationNode.create(IntegerLessThanNode.create(limit, limitCheckedIV.initNode(), NodeView.DEFAULT));
                                if (!checkProtectionInDominatingBlock(requiredProtection, loop)) {
                                    return null;
                                }
                            }
                        }
                    }
                } else if (limitCheckedIVDirection == Direction.Down) {
                    if (limitStamp.asConstant() != null && (limitStamp.asConstant().asLong() == counterStamp.lowerBound() || ifPosition == IfPosition.LoopEnd &&
                                    limitStamp.asConstant().asLong() == ((IntegerStamp) InductionVariableHelper.previousIteration(limitCheckedIV).valueNode().stamp(NodeView.DEFAULT)).lowerBound())) {
                        // signed: i > MIN_INT
                    } else if (limitStamp.asConstant() != null && limitStamp.asConstant().asLong() == counterStamp.unsignedLowerBound() && IntegerStamp.sameSign(initStamp, limitStamp)) {
                        unsigned = true;
                    } else {
                        if (!limitCheckedIV.isConstantStride() || !absStrideIsOne(limitCheckedIV)) {
                            return null;
                        }
                        if (ifPosition == IfPosition.LoopEnd && ignoreProtection) {
                            /*
                             * Inverted equality checked loop that is yet unprotected
                             */
                        } else if (initStamp.lowerBound() < limitStamp.upperBound()) {
                            /*
                             * This loop will have a negative overflow if init starts out lower than
                             * limit.
                             */
                            if (ifPosition == IfPosition.LoopEnd) {
                                // Fall through, rely on caller for protection.
                            } else {
                                // Downwards, head-counted loop: Look for limit <= init, i.e.,
                                // Not(init < limit).
                                LogicNode requiredProtection = LogicNegationNode.create(IntegerLessThanNode.create(limitCheckedIV.initNode(), limit, NodeView.DEFAULT));
                                if (!checkProtectionInDominatingBlock(requiredProtection, loop)) {
                                    return null;
                                }
                            }
                        }
                    }
                } else {
                    return null;
                }
                break;
            }
            case BE:
                unsigned = true; // fall through
            case LE:
                isLimitIncluded = true;
                if (limitCheckedIVDirection != Direction.Up) {
                    return null;
                }
                break;
            case BT:
                unsigned = true; // fall through
            case LT:
                if (limitCheckedIVDirection != Direction.Up) {
                    return null;
                }
                break;
            case AE:
                unsigned = true; // fall through
            case GE:
                isLimitIncluded = true;
                if (limitCheckedIVDirection != Direction.Down) {
                    return null;
                }
                break;
            case AT:
                unsigned = true; // fall through
            case GT:
                if (limitCheckedIVDirection != Direction.Down) {
                    return null;
                }
                break;
            default:
                throw GraalError.shouldNotReachHere(condition.toString()); // ExcludeFromJacocoGeneratedReport
        }
        return new CountedLoopConditionData(limitCheckedIV, limit, tripCountLimit, bodyUsesPrevIterationIV, isLimitIncluded, unsigned, complexSignednessRange);
    }

    private static boolean checkProtectionInDominatingBlock(LogicNode requiredProtection, Loop loop) {
        HIRBlock cur = loop.getCFGLoop().getHeader().getDominator();
        FixedNode prev = null;
        while (cur != null) {
            // after floating guards
            for (GuardNode g : cur.getBeginNode().guards()) {
                LogicNode l = g.getCondition();
                if (l instanceof CompareNode) {
                    CompareNode c = (CompareNode) l;
                    if (c.implies(g.isNegated(), requiredProtection).isTrue()) {
                        return true;
                    }
                } else if (l instanceof IntegerTestNode test) {
                    if (test.implies(g.isNegated(), requiredProtection).isTrue()) {
                        return true;
                    }
                }
            }
            // high tier fixed guards
            for (FixedNode fg : GraphUtil.predecessorIterable(cur.getEndNode())) {
                if (fg == cur.getBeginNode()) {
                    prev = fg;
                    break;
                }
                LogicNode l = null;
                boolean negated = false;
                if (fg instanceof IfNode) {
                    l = ((IfNode) fg).condition();
                    GraalError.guarantee(prev != null, "need previous node when checking if successor");
                    negated = prev == ((IfNode) fg).falseSuccessor();
                }
                if (fg instanceof FixedGuardNode) {
                    l = ((FixedGuardNode) fg).condition();
                    negated = ((FixedGuardNode) fg).isNegated();
                }
                if (l != null) {
                    if (l instanceof CompareNode) {
                        CompareNode c = (CompareNode) l;
                        if (c.implies(negated, requiredProtection).isTrue()) {
                            return true;
                        }
                    } else if (l instanceof IntegerTestNode test) {
                        if (test.implies(negated, requiredProtection).isTrue()) {
                            return true;
                        }
                    }
                }
                prev = fg;
            }
            cur = cur.getDominator();
        }
        return false;
    }

    /**
     * Get the mathematical trip count limit of this inverted loop, see {@link CountedLoopInfo#getTripCountLimit()}
     * for details.
     *
     * Inverted loop for which the counter induction variable is not in its inverted
     * form,i.e., it does not check
     *
     *<pre>
     * if (i op stride == end) break
     *</pre>
     *
     * but checks
     *
     *<pre>
     * if (i == end) break
     *</pre>
     *
     * this means this loop's iteration space is one level further and we need to take the
     * next iteration val as limit.
     *
     *
     * @formatter:off
     * For clarity the two loops below outline how the different IVs and limits align accordingly:
     *
     *
     * Case (1) Tail counted loop with non-inverted limit checked IV.
     * <pre>
     *   i = init;
     *   do {
     *      use(i);
     *   } while (i++ < limit);
     * </pre>
     *
     * getBodyIV                    i
     * getLimitCheckedIV            i
     * bodyUsesPrevIterationIV      false // body and limit check consume the same IV
     * getLimit                     limit
     * getTripCountLimit            limit + 1
     *
     * -----------------------------------------------------------------------------------------
     *
     * Case (2) Tail counted loop with inverted limit checked IV.
     *
     * <pre>
     *    i = init;
     *    do {
     *       use(i);
     *       i++;
     *    } while (i < limit);  // equivalently, while (++i < limit)
     * </pre>
     *
     * getBodyIV                    i
     * getLimitCheckedIV            i + 1
     * bodyUsesPrevIterationIV      true // body and limit check consume different IVs
     * getLimit                     limit
     * getTripCountLimit            limit
     * @formatter:on
     */
    public static ValueNode getTripCountLimit(ValueNode limit, InductionVariable limitCheckedIV) {
        BasicInductionVariable biv = (BasicInductionVariable) limitCheckedIV;
        BinaryArithmeticNode<?> op = biv.getOp();
        if (op instanceof AddNode) {
            return limit.graph().addOrUniqueWithInputs(AddNode.create(limit, biv.rawStride(),
                            NodeView.DEFAULT));
        } else if (op instanceof SubNode) {
            return limit.graph().addOrUniqueWithInputs(SubNode.create(limit, biv.rawStride(),
                            NodeView.DEFAULT));
        }
        return limit;
    }

    /**
     * Determine if this inverted loop can spawn across the signedness range of integers.
     *
     * For inverted loops we have to differentiate between the iv used in the loop body and the IV
     * used by the counted check (compared against the limit), see
     * {@link CountedLoopInfo#getLimitCheckedIV()} and CountedLoopInfo#getBodyIV() for details.
     *
     * Unsigned checks can cause correctness issues for inverted loops if the body iv's init node
     * can be negative but the stride causes the limit checked IV to be always positive, because in
     * such a scenario the counted check can be optimized to an unsigned one while the body iv is
     * still in its signed form.
     *
     * Example
     *
     * <pre>
     * int i = -1;
     * do {
     *     // i can be < 0 in first iteration
     *     i++;
     *     // i can never be < 0 after the first iteration
     * } while (i < 10); // can be optimized to an unsigned comparison given that -1 + 1 is always
     *                   // >= 0
     * </pre>
     *
     * In order to still produce correct code and use {@link CountedLoopInfo#getBodyIVStart()} and
     * {@link CountedLoopInfo#getLimit()} to calculate ranges we disallow counted loop detection for
     * such patterns given that -1 to UnsignedInteger.MAX is larger than the unsigned range and can
     * cause overflows in calculations.
     *
     * Users of the {@linkplain CountedLoopInfo API} would need to distinguish between those two
     * cases, however, this is complex and thus we ignore such inverted loops in Graal.
     */
    protected static boolean invertedLoopComplexSignednessRange(IfPosition ifPosition, Condition condition, InductionVariable limitCheckedIV, boolean bodyUsesPrevIterationIV) {
        if (limitCheckedIV.getLoop().loopBegin().isProtectedNonOverflowingUnsigned()) {
            return false;
        }
        if (ifPosition == IfPosition.LoopEnd && condition.isUnsigned()) {
            if (bodyUsesPrevIterationIV) {
                ValueNode initNode = InductionVariableHelper.previousIteration(limitCheckedIV).initNode();
                if (((IntegerStamp) initNode.stamp(NodeView.DEFAULT)).canBeNegative()) {
                    return true;
                }
            }
        }
        return false;
    }

    public enum IfPosition {
        LoopStart,
        LoopEnd
    }

    private InvertedAnalysisResult detectInvertedCountedLoop(boolean shouldIgnoreProtection) {
        boolean ignoreProtection = shouldIgnoreProtection;
        if (!Options.DetectInvertedLoopsAsCounted.getValue(loopBegin().getOptions())) {
            return InvertedAnalysisResult.NotCounted;
        }
        if (loopBegin().getLoopEndCount() != 1) {
            return InvertedAnalysisResult.NotCounted;
        }
        Node backEdgeNode = loopBegin().loopEnds().first().predecessor();
        while (CountedLoopInfo.backedgeSkippableNode(backEdgeNode)) {
            backEdgeNode = backEdgeNode.predecessor();
        }
        if (backEdgeNode instanceof BeginNode backEdgeBegin && backEdgeBegin.predecessor() instanceof IfNode) {
            if (backEdgeBegin.hasAnchored()) {
                /**
                 * Code anchored in the {@link LoopEnd} branch: this loop is in fact not inverted
                 * because there is code between the counted exit condition and the backedge jump.
                 *
                 * Consider this loop:
                 *
                 * <pre>
                 * int phi = 0;
                 * int limit = 100;
                 * if (phi < limit) {
                 *     while (true) {
                 *         body();
                 *         phi++;
                 *         if (phi < limit) {
                 *             continue;
                 *         } else {
                 *             break;
                 *         }
                 *     }
                 * }
                 * </pre>
                 *
                 * which is a perfect inverted loop. However, this loop
                 *
                 * <pre>
                 * int phi = 0;
                 * int limit = 100;
                 * if (phi < limit) {
                 *     while (true) {
                 *         body();
                 *         phi++;
                 *         if (phi < limit) {
                 *             floatingOperationAnchoredOnPrevBegin();
                 *             continue;
                 *         } else {
                 *             break;
                 *         }
                 *     }
                 * }
                 * </pre>
                 *
                 * is not because the floating operation anchored on the {@link IfNode}'s begin is
                 * executed 1 time less than the rest of the loop body, i.e., it is not in the
                 * inverted body position. Thus, such loops are not counted.
                 */
                return InvertedAnalysisResult.NotCounted;
            }
            CountedLoopInfo countedLoopInfo = detectCountedLoopIf((IfNode) backEdgeNode.predecessor(), IfPosition.LoopEnd, ignoreProtection);
            if (countedLoopInfo != null) {
                // Looks like an inverted loop, now check if the first iteration is protected
                ValueNode start = countedLoopInfo.getBodyIVStart();
                ValueNode limit = countedLoopInfo.getTripCountLimit();
                Condition condition;
                boolean unsigned = countedLoopInfo.getCounterIntegerHelper() instanceof UnsignedIntegerHelper;
                if (countedLoopInfo.getDirection() == Direction.Up) {
                    if (countedLoopInfo.isLimitIncluded()) {
                        condition = unsigned ? Condition.BE : Condition.LE;
                    } else {
                        condition = unsigned ? Condition.BT : Condition.LT;
                    }
                } else {
                    if (countedLoopInfo.isLimitIncluded()) {
                        condition = unsigned ? Condition.AE : Condition.GE;
                    } else {
                        condition = unsigned ? Condition.AT : Condition.GT;
                    }
                }
                LogicNode requiredProtection = null;

                /*
                 * We need to ensure the first iteration of an inverted loop is protected.
                 *
                 * We can skip this logic (because we know it is protected) if:
                 *
                 * 1) Graal inverted the loop in which case it is guaranteed to be protected
                 *
                 * 2) Graal unrolled an inverted loop in which process it protects main and post
                 * loop
                 *
                 */
                if (!(loopBegin().isMainLoop() || loopBegin().isPostLoop() || loopBegin().isCompilerInverted() || loopBegin().isAnyStripMinedInner())) {
                    requiredProtection = CompareNode.createAnyCompareNode(condition, start, limit, null);
                } else {
                    ignoreProtection = true;
                }
                if (loopBegin().isProtectedNonOverflowingUnsigned()) {
                    return InvertedAnalysisResult.createCounted(countedLoopInfo);
                }
                if (requiredProtection == null || requiredProtection.isTautology() || ignoreProtection ||
                                loopBegin().isProtectedNonOverflowingUnsigned()) {

                    /*
                     * If the original loop was guaranteed to not have signedness problems this loop
                     * cannot have them. That is because the original loop had an entry node that
                     * guaranteed this. Through unrolling we might lose this info because the entry
                     * values of IVs are the phi values of the original loops.
                     */
                    boolean canSkipComplexSignednessRangeCheck = loopBegin().isMainLoop() || loopBegin().isPostLoop();
                    if (!canSkipComplexSignednessRangeCheck) {
                        if (countedLoopInfo.isInvertedLoopComplexSignednessRange()) {
                            return InvertedAnalysisResult.UnsignedNegative;
                        }
                    }
                    return InvertedAnalysisResult.createCounted(countedLoopInfo);
                } else {
                    if (checkProtectionInDominatingBlock(requiredProtection, this)) {
                        if (countedLoopInfo.isInvertedLoopComplexSignednessRange()) {
                            return InvertedAnalysisResult.UnsignedNegative;
                        }
                        return InvertedAnalysisResult.createCounted(countedLoopInfo);
                    }
                    if (countedLoopInfo.isInvertedLoopComplexSignednessRange()) {
                        return InvertedAnalysisResult.NotProtectedUnsignedNegative;
                    }
                    return InvertedAnalysisResult.NotProtected;
                }
            }
        }
        return InvertedAnalysisResult.NotCounted;
    }
}
