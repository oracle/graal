/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.graph.iterators.NodePredicates.isNotA;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

@NodeInfo
public final class LoopBeginNode extends AbstractMergeNode implements IterableNodeType, LIRLowerable {

    public static final NodeClass<LoopBeginNode> TYPE = NodeClass.create(LoopBeginNode.class);

    protected double loopOrigFrequency;
    protected int nextEndIndex;
    protected int unswitches;
    protected int splits;
    protected int peelings;
    protected boolean compilerInverted;
    protected LoopType loopType;
    protected int unrollFactor;
    protected boolean osrLoop;
    protected boolean nonCountedStripMinedOuter;
    protected boolean nonCountedStripMinedInner;
    protected boolean countedStripMinedOuter;
    protected boolean countedStripMinedInner;
    protected boolean rotated;
    protected int stripMinedLimit = -1;

    /**
     * Flag to indicate that this loop must not be detected as a counted loop.
     */
    protected boolean disableCounted;
    /**
     * Flag indicating that this loop can never overflow based on some property not visible in the
     * loop control computations.
     */
    protected boolean canNeverOverflow;

    public enum LoopType {
        SIMPLE_LOOP,
        PRE_LOOP,
        MAIN_LOOP,
        POST_LOOP
    }

    /**
     * A {@link GuardingNode} protecting an unsigned inverted counted loop. {@link IntegerStamp}
     * does not record information about the sign of a stamp, i.e., it cannot represent
     * {@link IntegerStamp#mayBeSet()}} and {@link IntegerStamp#mustBeSet()} in relation with
     * unsigned stamps. Thus, if we have such a guard we set it explicitly to a loop.
     *
     * An example for such a loop would be
     *
     * <pre>
     * public static long foo(int start) {
     *     if (Integer.compareUnsigned(start, 2) &lt; 0) {
     *         deoptimize();
     *     }
     *     int i = start;
     *     do {
     *         // body
     *         i = i - 2;
     *     } while (Integer.compareUnsigned(i, 2) >= 0);
     *     return res;
     * }
     * </pre>
     *
     * Counted loop detection must ensure that start is |>| 1 else the loop would underflow unsigned
     * integer range immediately. The unsigned comparison with a deopt dominating the loop ensures
     * that start is always an unsigned integer in the range of [2,UnsignedIntMax], however this can
     * currently not be expressed with regular {@link IntegerStamp}.
     */
    @OptionalInput(InputType.Guard) protected GuardingNode protectedNonOverflowingUnsigned;

    public GuardingNode getUnsignedRangeGuard() {
        return protectedNonOverflowingUnsigned;
    }

    public void setUnsignedRangeGuard(GuardingNode guard) {
        updateUsagesInterface(this.protectedNonOverflowingUnsigned, guard);
        this.protectedNonOverflowingUnsigned = guard;
    }

    public boolean isProtectedNonOverflowingUnsigned() {
        return protectedNonOverflowingUnsigned != null;
    }

    /**
     * State of the safepoint properties of this loop.
     *
     * For the various points in a loop (e.g. loop ends, loop exits, guest loop ends) for which a
     * safepoint may be emitted, a SafepointState value specifies if the emission is (or should be)
     * suppressed and if so, why.
     */
    public enum SafepointState {
        /**
         * Determines that, for whatever reason, we must never create a safepoint related to this
         * loop. This includes on the backedge or the loop exit.
         */
        MUST_NEVER_SAFEPOINT(false),
        /**
         * Determines that the optimizer decided there is no need for a safepoint on any backedge or
         * loop exit.
         */
        OPTIMIZER_DISABLED(false),
        /**
         * Determines the safepoint poll should be performed.
         */
        ENABLED(true);

        private final boolean canSafepoint;

        SafepointState(boolean canSafepoint) {
            this.canSafepoint = canSafepoint;
        }

        public boolean canSafepoint() {
            return canSafepoint;
        }

    }

    /** See {@link LoopEndNode#safepointState} for more information. */
    SafepointState loopEndsSafepointState;

    /** Same as {@link #loopEndsSafepointState} but for {@link LoopExitNode}. */
    SafepointState loopExitsSafepointState;

    /** See {@link LoopEndNode#guestSafepointState} for more information. */
    SafepointState guestLoopEndsSafepointState;

    /**
     * A guard that proves that this loop's counter never overflows and wraps around (either in the
     * positive or negative direction).
     */
    @OptionalInput(InputType.Guard) GuardingNode overflowGuard;

    /**
     * A guard that proves that memory accesses in this loop don't alias in certain ways that must
     * not be reordered.
     */
    @OptionalInput(InputType.Guard) GuardingNode interIterationAliasingGuard;

    public static final CounterKey overflowSpeculationTaken = DebugContext.counter("CountedLoops_OverflowSpeculation_Taken");
    public static final CounterKey overflowSpeculationNotTaken = DebugContext.counter("CountedLoops_OverflowSpeculation_NotTaken");

    public static final SpeculationReasonGroup LOOP_OVERFLOW_DEOPT = new SpeculationReasonGroup("LoopOverflowDeopt", ResolvedJavaMethod.class, int.class);

    /**
     * A number based on the {@link Node#getId()} of the original {@link LoopBeginNode} this node
     * was cloned from. It may additionally encode information about the compressions of a graph. Do
     * not assume anything about the actual value of this number. The only important data it
     * preserves is that it encodes information about the original loop begin. This can be used when
     * comparing two different loop begin nodes if they are both result of a clone operation of the
     * same loop in the same graph. Only used for debugging and verification purposes. This number
     * is highly implementation dependent and can change over the course of a graph because of
     * {@link Graph#maybeCompress()} compression.
     */
    @SuppressWarnings("javadoc") private long cloneFromNodeId = -1;

    public LoopBeginNode() {
        super(TYPE);
        loopOrigFrequency = 1;
        unswitches = 0;
        loopEndsSafepointState = SafepointState.ENABLED;
        loopExitsSafepointState = SafepointState.ENABLED;
        guestLoopEndsSafepointState = SafepointState.ENABLED;
        loopType = LoopType.SIMPLE_LOOP;
        unrollFactor = 1;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void afterClone(Node other) {
        super.afterClone(other);
        assert other instanceof LoopBeginNode : Assertions.errorMessage("Must be cloned from a previous loop begin", this, other);
        // ideally we would want to verify that cloneFrom==-1 but when we copy a node manually with
        // (addDuplicates) and call afterClone on it we have to override this value

        final int otherNodeId = other.getId();
        this.cloneFromNodeId = other.graph() != null ? ((long) other.graph().getCompressions() << 32L | otherNodeId) : otherNodeId;
    }

    public long getClonedFromNodeId() {
        return cloneFromNodeId;
    }

    public void checkDisableCountedBySpeculation(int bci, StructuredGraph graph) {
        SpeculationLog speculationLog = graph.getSpeculationLog();
        boolean disableCountedBasedOnSpeculation = false;
        if (speculationLog != null) {
            SpeculationLog.SpeculationReason speculationReason = LOOP_OVERFLOW_DEOPT.createSpeculationReason(graph.method(), bci);
            if (!speculationLog.maySpeculate(speculationReason)) {
                overflowSpeculationNotTaken.increment(graph.getDebug());
                disableCountedBasedOnSpeculation = true;
            }
        }
        disableCounted = disableCountedBasedOnSpeculation;
    }

    public void setStripMinedLimit(int stripMinedLimit) {
        this.stripMinedLimit = stripMinedLimit;
    }

    public int getStripMinedLimit() {
        return stripMinedLimit;
    }

    public boolean canEndsSafepoint() {
        return loopEndsSafepointState.canSafepoint();
    }

    public SafepointState getLoopEndsSafepointState() {
        return loopEndsSafepointState;
    }

    public boolean canEndsGuestSafepoint() {
        return guestLoopEndsSafepointState.canSafepoint();
    }

    public boolean canExitsSafepoint() {
        return loopExitsSafepointState.canSafepoint();
    }

    public SafepointState getLoopExitsSafepointState() {
        return loopExitsSafepointState;
    }

    public void markNonCountedStripMinedInner() {
        this.nonCountedStripMinedInner = true;
    }

    public boolean isNonCountedStripMinedInner() {
        return nonCountedStripMinedInner;
    }

    public void markNonCountedStripMinedOuter() {
        this.nonCountedStripMinedOuter = true;
    }

    public boolean isNonCountedStripMinedOuter() {
        return nonCountedStripMinedOuter;
    }

    public void markCountedStripMinedInner() {
        this.countedStripMinedInner = true;
    }

    public boolean isCountedStripMinedInner() {
        return countedStripMinedInner;
    }

    public void markCountedStripMinedOuter() {
        this.countedStripMinedOuter = true;
    }

    public boolean isCountedStripMinedOuter() {
        return countedStripMinedOuter;
    }

    public boolean isAnyStripMinedOuter() {
        return isCountedStripMinedOuter() || isNonCountedStripMinedOuter();
    }

    public boolean isAnyStripMinedInner() {
        return isCountedStripMinedInner() || isNonCountedStripMinedInner();
    }

    public boolean canNeverOverflow() {
        return canNeverOverflow;
    }

    public boolean canOverflow() {
        return !canNeverOverflow();
    }

    public boolean isRotated() {
        return rotated;
    }

    public void setRotated(boolean rotated) {
        this.rotated = rotated;
    }

    public void setCanNeverOverflow() {
        assert !canNeverOverflow;
        this.canNeverOverflow = true;
    }

    public boolean countedLoopDisabled() {
        return disableCounted;
    }

    public boolean isSimpleLoop() {
        return (loopType == LoopType.SIMPLE_LOOP);
    }

    public void setPreLoop() {
        assert isSimpleLoop();
        loopType = LoopType.PRE_LOOP;
    }

    public boolean isPreLoop() {
        return (loopType == LoopType.PRE_LOOP);
    }

    public void setMainLoop() {
        assert isSimpleLoop();
        loopType = LoopType.MAIN_LOOP;
    }

    public boolean isMainLoop() {
        return (loopType == LoopType.MAIN_LOOP);
    }

    public void setPostLoop() {
        assert isSimpleLoop();
        loopType = LoopType.POST_LOOP;
    }

    public boolean isPostLoop() {
        return (loopType == LoopType.POST_LOOP);
    }

    public int getUnrollFactor() {
        return unrollFactor;
    }

    public void setUnrollFactor(int currentUnrollFactor) {
        unrollFactor = currentUnrollFactor;
    }

    public void setLoopEndSafepoint(SafepointState newState) {
        GraalError.guarantee(loopEndsSafepointState.canSafepoint() || !newState.canSafepoint(), "New state must not allow safepoints if old did not, old=%s, new=%s", loopEndsSafepointState, newState);
        if (loopEndsSafepointState == SafepointState.MUST_NEVER_SAFEPOINT) {
            GraalError.guarantee(!newState.canSafepoint(), "Safepoints have been disabled for this loop, cannot re-enable them old=%s, new=%s", loopExitsSafepointState, newState);
        }
        /* Store flag locally in case new loop ends are created later on. */
        this.loopEndsSafepointState = newState;
        /* Propagate flag to all existing loop ends. */
        for (LoopEndNode loopEnd : loopEnds()) {
            loopEnd.setSafepointState(newState);
        }
    }

    public void setLoopExitSafepoint(SafepointState newState) {
        /*
         * The safepoint state for the loop exits can change over the course of compilation. It
         * depends on what is inside the body of a loop, how many ends, which calls etc. So it can
         * be that this becomes weaker and stronger over time as long as we do not force safepoint
         * on one that was explicitly disabled.
         */
        if (loopExitsSafepointState == SafepointState.MUST_NEVER_SAFEPOINT) {
            GraalError.guarantee(!newState.canSafepoint(), "Safepoints have been disabled for this loop, cannot re-enable them old=%s, new=%s", loopExitsSafepointState, newState);
        }
        this.loopExitsSafepointState = newState;
    }

    public void setGuestSafepoint(SafepointState newState) {
        GraalError.guarantee(guestLoopEndsSafepointState.canSafepoint() || !newState.canSafepoint(), "New state must not allow safepoints if old did not, old=%s, new=%s", guestLoopEndsSafepointState,
                        newState);
        /* Store flag locally in case new loop ends are created later on. */
        this.guestLoopEndsSafepointState = newState;
        /* Propagate flag to all existing loop ends. */
        for (LoopEndNode loopEnd : loopEnds()) {
            loopEnd.setGuestSafepointState(newState);
        }
    }

    public double loopOrigFrequency() {
        return loopOrigFrequency;
    }

    public void setLoopOrigFrequency(double loopOrigFrequency) {
        assert NumUtil.assertNonNegativeDouble(loopOrigFrequency);
        this.loopOrigFrequency = loopOrigFrequency;
    }

    /**
     * Returns the <b>unordered</b> set of {@link LoopEndNode} that correspond to back-edges for
     * this loop. The order of the back-edges is unspecified, if you need to get an ordering
     * compatible for {@link PhiNode} creation, use {@link #orderedLoopEnds()}.
     *
     * @return the set of {@code LoopEndNode} that correspond to back-edges for this loop
     */
    public NodeIterable<LoopEndNode> loopEnds() {
        return usages().filter(LoopEndNode.class);
    }

    public NodeIterable<LoopExitNode> loopExits() {
        return usages().filter(LoopExitNode.class);
    }

    public List<SafepointNode> loopSafepoints() {
        ArrayList<SafepointNode> safePoints = new ArrayList<>();
        for (LoopExitNode lex : loopExits()) {
            safePoints.addAll(lex.usages().filter(SafepointNode.class).snapshot());
        }
        return safePoints;
    }

    @Override
    public NodeIterable<Node> anchored() {
        return super.anchored().filter(isNotA(LoopEndNode.class).nor(LoopExitNode.class));
    }

    /**
     * Returns the set of {@link LoopEndNode} that correspond to back-edges for this loop, in
     * increasing {@link #phiPredecessorIndex} order. This method is suited to create new loop
     * {@link PhiNode}.<br>
     *
     * For example a new PhiNode may be added as follow:
     *
     * <pre>
     * PhiNode phi = new ValuePhiNode(stamp, loop);
     * phi.addInput(forwardEdgeValue);
     * for (LoopEndNode loopEnd : loop.orderedLoopEnds()) {
     *     phi.addInput(backEdgeValue(loopEnd));
     * }
     * </pre>
     *
     * @return the set of {@code LoopEndNode} that correspond to back-edges for this loop
     */
    public LoopEndNode[] orderedLoopEnds() {
        LoopEndNode[] result = new LoopEndNode[this.getLoopEndCount()];
        for (LoopEndNode end : loopEnds()) {
            result[end.endIndex()] = end;
        }
        return result;
    }

    public EndNode forwardEnd() {
        assert forwardEndCount() == 1 : forwardEnds();
        return forwardEndAt(0);
    }

    public int peelings() {
        return peelings;
    }

    public void incrementPeelings() {
        peelings++;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // Nothing to emit, since this is node is used for structural purposes only.
    }

    @Override
    protected void deleteEnd(AbstractEndNode end) {
        if (end instanceof LoopEndNode) {
            LoopEndNode loopEnd = (LoopEndNode) end;
            loopEnd.setLoopBegin(null);
            int idx = loopEnd.endIndex();
            for (LoopEndNode le : loopEnds()) {
                int leIdx = le.endIndex();
                assert leIdx != idx : Assertions.errorMessageContext("this", this, "end", end, "otherEnd", le);
                if (leIdx > idx) {
                    le.setEndIndex(leIdx - 1);
                }
            }
            nextEndIndex--;
        } else {
            super.deleteEnd(end);
        }
    }

    @Override
    public int phiPredecessorCount() {
        return forwardEndCount() + loopEnds().count();
    }

    @Override
    public int phiPredecessorIndex(AbstractEndNode pred) {
        if (pred instanceof LoopEndNode) {
            LoopEndNode loopEnd = (LoopEndNode) pred;
            if (loopEnd.loopBegin() == this) {
                assert loopEnd.endIndex() < loopEnds().count() : "Invalid endIndex : " + loopEnd;
                return loopEnd.endIndex() + forwardEndCount();
            }
        } else {
            return super.forwardEndIndex((EndNode) pred);
        }
        throw GraalError.shouldNotReachHere("unknown pred : " + pred); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public AbstractEndNode phiPredecessorAt(int index) {
        if (index < forwardEndCount()) {
            return forwardEndAt(index);
        }
        for (LoopEndNode end : loopEnds()) {
            int idx = index - forwardEndCount();
            assert NumUtil.assertNonNegativeInt(idx);
            if (end.endIndex() == idx) {
                return end;
            }
        }
        throw GraalError.shouldNotReachHere("unknown index: " + index); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean verifyNode() {
        assertTrue(loopEnds().isNotEmpty(), "missing loopEnd");
        return super.verifyNode();
    }

    int nextEndIndex() {
        return nextEndIndex++;
    }

    public int getLoopEndCount() {
        return nextEndIndex;
    }

    public int unswitches() {
        return unswitches;
    }

    public void incrementUnswitches() {
        unswitches++;
    }

    public boolean isCompilerInverted() {
        return compilerInverted;
    }

    public void setCompilerInverted() {
        assert !compilerInverted;
        compilerInverted = true;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        canonicalizePhis(tool);
    }

    public boolean isLoopExit(AbstractBeginNode begin) {
        return begin instanceof LoopExitNode && ((LoopExitNode) begin).loopBegin() == this;
    }

    public LoopEndNode getSingleLoopEnd() {
        assert loopEnds().count() == 1 : loopEnds();
        return loopEnds().first();
    }

    @SuppressWarnings("try")
    public void removeExits(boolean forKillCFG) {
        for (LoopExitNode loopexit : loopExits().snapshot()) {
            try (DebugCloseable position = graph().withNodeSourcePosition(loopexit)) {
                loopexit.removeExit(forKillCFG);
            }
        }
    }

    public GuardingNode getOverflowGuard() {
        return overflowGuard;
    }

    public void setOverflowGuard(GuardingNode overflowGuard) {
        updateUsagesInterface(this.overflowGuard, overflowGuard);
        this.overflowGuard = overflowGuard;
    }

    public GuardingNode getInterIterationAliasingGuard() {
        return interIterationAliasingGuard;
    }

    public void setInterIterationAliasingGuard(GuardingNode guard) {
        updateUsagesInterface(this.interIterationAliasingGuard, guard);
        this.interIterationAliasingGuard = guard;
    }

    private static final int NO_INCREMENT = Integer.MIN_VALUE;

    /**
     * Returns an array with one entry for each input of the phi, which is either
     * {@link #NO_INCREMENT} or the increment, i.e., the value by which the phi is incremented in
     * the corresponding branch.
     */
    private static int[] getSelfIncrements(PhiNode phi) {
        int[] selfIncrement = new int[phi.valueCount()];
        for (int i = 0; i < phi.valueCount(); i++) {
            ValueNode input = phi.valueAt(i);
            long increment = NO_INCREMENT;
            if (input != null && input instanceof AddNode && input.stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                AddNode add = (AddNode) input;
                if (add.getX() == phi && add.getY().isConstant()) {
                    increment = add.getY().asJavaConstant().asLong();
                } else if (add.getY() == phi && add.getX().isConstant()) {
                    increment = add.getX().asJavaConstant().asLong();
                }
            } else if (input == phi) {
                increment = 0;
            }
            if (increment < Integer.MIN_VALUE || increment > Integer.MAX_VALUE || increment == NO_INCREMENT) {
                increment = NO_INCREMENT;
            }
            selfIncrement[i] = (int) increment;
        }
        return selfIncrement;
    }

    /**
     * Coalesces loop phis that represent the same value (which is not handled by normal Global
     * Value Numbering).
     */
    public void canonicalizePhis(SimplifierTool tool) {
        int phiCount = phis().count();
        if (phiCount > 1) {
            int phiInputCount = phiPredecessorCount();
            int phiIndex = 0;
            int[][] selfIncrement = new int[phiCount][];
            PhiNode[] phis = this.phis().snapshot().toArray(new PhiNode[phiCount]);

            for (phiIndex = 0; phiIndex < phiCount; phiIndex++) {
                PhiNode phi = phis[phiIndex];
                if (phi != null) {
                    nextPhi: for (int otherPhiIndex = phiIndex + 1; otherPhiIndex < phiCount; otherPhiIndex++) {
                        PhiNode otherPhi = phis[otherPhiIndex];
                        if (otherPhi == null || phi.getNodeClass() != otherPhi.getNodeClass() || !phi.valueEquals(otherPhi)) {
                            continue nextPhi;
                        }
                        if (selfIncrement[phiIndex] == null) {
                            selfIncrement[phiIndex] = getSelfIncrements(phi);
                        }
                        if (selfIncrement[otherPhiIndex] == null) {
                            selfIncrement[otherPhiIndex] = getSelfIncrements(otherPhi);
                        }
                        int[] phiIncrement = selfIncrement[phiIndex];
                        int[] otherPhiIncrement = selfIncrement[otherPhiIndex];
                        for (int inputIndex = 0; inputIndex < phiInputCount; inputIndex++) {
                            if (phiIncrement[inputIndex] == NO_INCREMENT) {
                                if (phi.valueAt(inputIndex) != otherPhi.valueAt(inputIndex)) {
                                    continue nextPhi;
                                }
                            }
                            if (phiIncrement[inputIndex] != otherPhiIncrement[inputIndex]) {
                                continue nextPhi;
                            }
                        }
                        if (tool != null) {
                            tool.addToWorkList(otherPhi.usages());
                        }
                        otherPhi.replaceAtUsages(phi);
                        GraphUtil.killWithUnusedFloatingInputs(otherPhi);
                        phis[otherPhiIndex] = null;
                    }
                }
            }
        }
    }

    public void markOsrLoop() {
        osrLoop = true;
    }

    public boolean isOsrLoop() {
        return osrLoop;
    }

    @Override
    protected boolean verifyState() {
        return !this.graph().getGraphState().getFrameStateVerification().implies(GraphState.FrameStateVerificationFeature.LOOP_BEGINS) || super.verifyState();
    }

    @Override
    public void setStateAfter(FrameState x) {
        super.setStateAfter(x);
        if (x != null && graph() != null) {
            /*
             * We disable counted loop checking for loops whose overflow guard failed. Some
             * optimizations can change the loop begin's frame state and thus the associated BCI. In
             * this case we need to check the speculation log again to see if the new BCI is
             * associated with a failed overflow guard.
             */
            checkDisableCountedBySpeculation(x.bci, graph());
        }
    }

    public void removeSafepoints() {
        this.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, this.graph(), "Before removing safepoints at %s", this);
        for (SafepointNode loopSafepoint : loopSafepoints()) {
            if (loopSafepoint.isAlive()) {
                if (loopSafepoint.predecessor() != null && loopSafepoint.next() != null) {
                    this.graph().removeFixed(loopSafepoint);
                } else {
                    loopSafepoint.replaceAtPredecessor(null);
                    loopSafepoint.safeDelete();
                }
            }
        }

        this.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, this.graph(), "After removing safepoints at %s", this);
    }

}
