/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.loop;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.iterators.NodePredicate;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchored;
import org.graalvm.compiler.nodes.debug.NeverStripMineNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.loop.InductionVariable.Direction;
import org.graalvm.compiler.nodes.util.GraphUtil;

public class LoopEx {
    protected final Loop<Block> loop;
    protected LoopFragmentInside inside;
    protected LoopFragmentWhole whole;
    protected CountedLoopInfo counted;
    protected LoopsData data;
    protected EconomicMap<Node, InductionVariable> ivs;
    protected boolean countedLoopChecked;
    protected int size = -1;

    protected LoopEx(Loop<Block> loop, LoopsData data) {
        this.loop = loop;
        this.data = data;
    }

    public double localLoopFrequency() {
        return data.getCFG().localLoopFrequency(loopBegin());
    }

    public ProfileSource localFrequencySource() {
        return data.getCFG().localLoopFrequencySource(loopBegin());
    }

    public Loop<Block> loop() {
        return loop;
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

    public void invalidateFragments() {
        inside = null;
        whole = null;
    }

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

    @SuppressWarnings("unused")
    public LoopFragmentInsideFrom insideFrom(FixedNode point) {
        // TODO (gd)
        return null;
    }

    @SuppressWarnings("unused")
    public LoopFragmentInsideBefore insideBefore(FixedNode point) {
        // TODO (gd)
        return null;
    }

    public boolean isOutsideLoop(Node n) {
        return !whole().contains(n);
    }

    public LoopBeginNode loopBegin() {
        return (LoopBeginNode) loop().getHeader().getBeginNode();
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

    public LoopEx parent() {
        if (loop.getParent() == null) {
            return null;
        }
        return data.loop(loop.getParent());
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
        return (countedLoopChecked && isCounted() ? "CountedLoop [" + counted() + "] " : "Loop ") + "(depth=" + loop().getDepth() + ") " + loopBegin();
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

    public boolean reassociateInvariants() {
        int count = 0;
        StructuredGraph graph = loopBegin().graph();
        InvariantPredicate invariant = new InvariantPredicate();
        NodeBitMap newLoopNodes = graph.createNodeBitMap();
        for (BinaryArithmeticNode<?> binary : whole().nodes().filter(BinaryArithmeticNode.class)) {
            if (!binary.isAssociative()) {
                continue;
            }
            ValueNode result = BinaryArithmeticNode.reassociateMatchedValues(binary, invariant, binary.getX(), binary.getY(), NodeView.DEFAULT);
            if (result == binary) {
                result = BinaryArithmeticNode.reassociateUnmatchedValues(binary, invariant, NodeView.DEFAULT);
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
                DebugContext debug = graph.getDebug();
                if (debug.isLogEnabled()) {
                    debug.log("%s : Re-associated %s into %s", graph.method().format("%H::%n"), binary, result);
                }
                binary.replaceAtUsages(result);
                GraphUtil.killWithUnusedFloatingInputs(binary);
                count++;
            }
        }
        if (newLoopNodes.isNotEmpty()) {
            whole().nodes().union(newLoopNodes);
        }
        return count != 0;
    }

    @SuppressWarnings("fallthrough")
    public boolean detectCounted() {
        if (countedLoopChecked) {
            return isCounted();
        }
        countedLoopChecked = true;
        LoopBeginNode loopBegin = loopBegin();
        if (loopBegin.countedLoopDisabled()) {
            return false;
        }
        FixedNode next = loopBegin.next();
        while (next instanceof FixedGuardNode || next instanceof ValueAnchorNode || next instanceof FullInfopointNode) {
            next = ((FixedWithNextNode) next).next();
        }
        if (next instanceof IfNode) {
            IfNode ifNode = (IfNode) next;
            boolean negated = false;
            if (!isCfgLoopExit(ifNode.falseSuccessor())) {
                if (!isCfgLoopExit(ifNode.trueSuccessor())) {
                    return false;
                }
                negated = true;
            }
            LogicNode ifTest = ifNode.condition();
            if (!(ifTest instanceof CompareNode)) {
                return false;
            }
            CompareNode compare = (CompareNode) ifTest;
            Condition condition = null;
            InductionVariable iv = null;
            ValueNode limit = null;
            if (isOutsideLoop(compare.getX())) {
                iv = getInductionVariables().get(compare.getY());
                if (iv != null) {
                    condition = compare.condition().asCondition().mirror();
                    limit = compare.getX();
                }
            } else if (isOutsideLoop(compare.getY())) {
                iv = getInductionVariables().get(compare.getX());
                if (iv != null) {
                    condition = compare.condition().asCondition();
                    limit = compare.getY();
                }
            }
            if (condition == null) {
                return false;
            }
            if (negated) {
                condition = condition.negate();
            }
            boolean isLimitIncluded = false;
            boolean unsigned = false;
            switch (condition) {
                case EQ:
                    if (iv.initNode() == limit) {
                        // allow "single iteration" case
                        isLimitIncluded = true;
                    } else {
                        return false;
                    }
                    break;
                case NE: {
                    IntegerStamp initStamp = (IntegerStamp) iv.initNode().stamp(NodeView.DEFAULT);
                    IntegerStamp limitStamp = (IntegerStamp) limit.stamp(NodeView.DEFAULT);
                    IntegerStamp counterStamp = (IntegerStamp) iv.valueNode().stamp(NodeView.DEFAULT);
                    if (iv.direction() == Direction.Up) {
                        if (limitStamp.asConstant() != null && limitStamp.asConstant().asLong() == counterStamp.upperBound()) {
                            // signed: i < MAX_INT
                        } else if (limitStamp.asConstant() != null && limitStamp.asConstant().asLong() == counterStamp.unsignedUpperBound()) {
                            unsigned = true;
                        } else if (!iv.isConstantStride() || Math.abs(iv.constantStride()) != 1 || initStamp.upperBound() > limitStamp.lowerBound()) {
                            return false;
                        }
                    } else if (iv.direction() == Direction.Down) {
                        if (limitStamp.asConstant() != null && limitStamp.asConstant().asLong() == counterStamp.lowerBound()) {
                            // signed: MIN_INT > i
                        } else if (limitStamp.asConstant() != null && limitStamp.asConstant().asLong() == counterStamp.unsignedLowerBound()) {
                            unsigned = true;
                        } else if (!iv.isConstantStride() || Math.abs(iv.constantStride()) != 1 || initStamp.lowerBound() < limitStamp.upperBound()) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                    break;
                }
                case BE:
                    unsigned = true; // fall through
                case LE:
                    isLimitIncluded = true;
                    if (iv.direction() != Direction.Up) {
                        return false;
                    }
                    break;
                case BT:
                    unsigned = true; // fall through
                case LT:
                    if (iv.direction() != Direction.Up) {
                        return false;
                    }
                    break;
                case AE:
                    unsigned = true; // fall through
                case GE:
                    isLimitIncluded = true;
                    if (iv.direction() != Direction.Down) {
                        return false;
                    }
                    break;
                case AT:
                    unsigned = true; // fall through
                case GT:
                    if (iv.direction() != Direction.Down) {
                        return false;
                    }
                    break;
                default:
                    throw GraalError.shouldNotReachHere(condition.toString());
            }
            counted = new CountedLoopInfo(this, iv, ifNode, limit, isLimitIncluded, negated ? ifNode.falseSuccessor() : ifNode.trueSuccessor(), unsigned);
            return true;
        }
        return false;
    }

    public boolean isCfgLoopExit(AbstractBeginNode begin) {
        Block block = data.getCFG().blockFor(begin);
        return loop.getDepth() > block.getLoopDepth() || loop.isNaturalExit(block);
    }

    public LoopsData loopsData() {
        return data;
    }

    public void nodesInLoopBranch(NodeBitMap branchNodes, AbstractBeginNode branch) {
        EconomicSet<AbstractBeginNode> blocks = EconomicSet.create();
        Collection<AbstractBeginNode> exits = new LinkedList<>();
        Queue<Block> work = new LinkedList<>();
        ControlFlowGraph cfg = loopsData().getCFG();
        work.add(cfg.blockFor(branch));
        while (!work.isEmpty()) {
            Block b = work.remove();
            if (loop().isLoopExit(b)) {
                assert !exits.contains(b.getBeginNode());
                exits.add(b.getBeginNode());
            } else if (blocks.add(b.getBeginNode())) {
                Block d = b.getDominatedSibling();
                while (d != null) {
                    if (loop.getBlocks().contains(d)) {
                        work.add(d);
                    }
                    d = d.getDominatedSibling();
                }
            }
        }
        LoopFragment.computeNodes(branchNodes, branch.graph(), this, blocks, exits);
    }

    public EconomicMap<Node, InductionVariable> getInductionVariables() {
        if (ivs == null) {
            ivs = findInductionVariables();
        }
        return ivs;
    }

    /**
     * Collect all the basic induction variables for the loop and the find any induction variables
     * which are derived from the basic ones.
     *
     * @return a map from node to induction variable
     */
    private EconomicMap<Node, InductionVariable> findInductionVariables() {
        EconomicMap<Node, InductionVariable> currentIvs = EconomicMap.create(Equivalence.IDENTITY);

        Queue<InductionVariable> scanQueue = new LinkedList<>();
        LoopBeginNode loopBegin = this.loopBegin();
        AbstractEndNode forwardEnd = loopBegin.forwardEnd();
        for (PhiNode phi : loopBegin.valuePhis()) {
            ValueNode backValue = phi.singleBackValueOrThis();
            if (backValue == phi) {
                continue;
            }
            ValueNode stride = addSub(this, backValue, phi);
            if (stride != null) {
                BasicInductionVariable biv = new BasicInductionVariable(this, (ValuePhiNode) phi, phi.valueAt(forwardEnd), stride, (BinaryArithmeticNode<?>) backValue);
                currentIvs.put(phi, biv);
                scanQueue.add(biv);
            }
        }

        while (!scanQueue.isEmpty()) {
            InductionVariable baseIv = scanQueue.remove();
            ValueNode baseIvNode = baseIv.valueNode();
            for (ValueNode op : baseIvNode.usages().filter(ValueNode.class)) {
                if (this.isOutsideLoop(op)) {
                    continue;
                }
                if (op.hasExactlyOneUsage() && op.usages().first() == baseIvNode) {
                    /*
                     * This is just the base induction variable increment with no other uses so
                     * don't bother reporting it.
                     */
                    continue;
                }
                InductionVariable iv = null;
                ValueNode offset = addSub(this, op, baseIvNode);
                ValueNode scale;
                if (offset != null) {
                    iv = new DerivedOffsetInductionVariable(this, baseIv, offset, (BinaryArithmeticNode<?>) op);
                } else if (op instanceof NegateNode) {
                    iv = new DerivedScaledInductionVariable(this, baseIv, (NegateNode) op);
                } else if ((scale = mul(this, op, baseIvNode)) != null) {
                    iv = new DerivedScaledInductionVariable(this, baseIv, scale, op);
                } else {
                    boolean isValidConvert = op instanceof PiNode || op instanceof SignExtendNode;
                    if (!isValidConvert && op instanceof ZeroExtendNode) {
                        ZeroExtendNode zeroExtendNode = (ZeroExtendNode) op;
                        isValidConvert = zeroExtendNode.isInputAlwaysPositive() || ((IntegerStamp) zeroExtendNode.stamp(NodeView.DEFAULT)).isPositive();
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

    private static ValueNode addSub(LoopEx loop, ValueNode op, ValueNode base) {
        if (op.stamp(NodeView.DEFAULT) instanceof IntegerStamp && (op instanceof AddNode || op instanceof SubNode)) {
            BinaryArithmeticNode<?> aritOp = (BinaryArithmeticNode<?>) op;
            if (aritOp.getX() == base && loop.isOutsideLoop(aritOp.getY())) {
                return aritOp.getY();
            } else if (aritOp.getY() == base && loop.isOutsideLoop(aritOp.getX())) {
                return aritOp.getX();
            }
        }
        return null;
    }

    private static ValueNode mul(LoopEx loop, ValueNode op, ValueNode base) {
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
                return ConstantNode.forIntegerStamp(base.stamp(NodeView.DEFAULT), 1 << shift.getY().asJavaConstant().asInt(), base.graph());
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

    /**
     * @return true if all nodes in the loop can be duplicated.
     */
    public boolean canDuplicateLoop() {
        for (Node node : inside().nodes()) {
            /*
             * Control flow anchored nodes must not be duplicated.
             */
            if (node instanceof ControlFlowAnchored) {
                return false;
            }
            if (node instanceof FrameState) {
                FrameState frameState = (FrameState) node;
                /*
                 * Exception handling frame states can cause problems when they are duplicated and
                 * one needs to create a framestate at the duplication merge.
                 */
                if (frameState.isExceptionHandlingBCI()) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean canStripMine() {
        for (Node node : inside().nodes()) {
            if (node instanceof NeverStripMineNode) {
                return false;
            }
        }
        return true;
    }

    public boolean canBecomeLimitTestAfterFloatingReads(@SuppressWarnings("unused") IfNode ifNode) {
        return false;
    }
}
