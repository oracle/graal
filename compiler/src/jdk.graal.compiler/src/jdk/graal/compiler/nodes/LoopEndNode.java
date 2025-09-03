/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.Collections;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.LoopBeginNode.SafepointState;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * LoopEnd nodes represent a loop back-edge. When a LoopEnd is reached, execution continues at the
 * {@linkplain #loopBegin() loop header}.
 */
@NodeInfo(cycles = CYCLES_1, cyclesRationale = "Backedge jmp", size = SIZE_1, sizeRationale = "Backedge jmp")
public final class LoopEndNode extends AbstractEndNode {

    public static final NodeClass<LoopEndNode> TYPE = NodeClass.create(LoopEndNode.class);

    /*
     * The declared type of the field cannot be LoopBeginNode, because loop explosion during partial
     * evaluation can temporarily assign a non-loop begin. This node will then be deleted shortly
     * after - but we still must not have type system violations for that short amount of time.
     */
    @Input(Association) AbstractBeginNode loopBegin;
    protected int endIndex;

    /**
     * Most loop ends need a safepoint ({@link SafepointState#canSafepoint()} yields true) so that
     * garbage collection can interrupt a long-running (possibly endless) loop. Safepoints may be
     * disabled for two reasons: 1) Some code must be safepoint free, i.e., uninterruptible by
     * garbage collection. 2) An optimization phase determined that the loop already has another
     * safepoint or cannot be endless, so there is no need for a loop-end safepoint.
     *
     * Note that 1) is a hard correctness issue: emitting a safepoint in uninterruptible code is a
     * bug, i.e., it is not allowed to set the flag back to true once it is false. To ensure that
     * loop ends that are created late, e.g., during control flow simplifications, have no
     * safepoints in such cases, the safepoints are actually disabled for the
     * {@link LoopBeginNode#canEndsSafepoint loop begin}. New loop ends inherit the state value from
     * the loop begin.
     */
    SafepointState safepointState;

    /**
     * If Graal is used as a compiler for a guest language then in addition to host safepoints there
     * is also a need for guest safepoints. Unlike host safepoints, guest safepoints are not needed
     * to support garbage collectors but to support features like cancellation or reading stack
     * frames from other threads.
     * <p>
     * The state is used to store the information whether safepoints can be disabled for this loop
     * end. It depends on the guest language implementation framework whether this state indicates a
     * possible safepoint or not.
     * <p>
     * If Graal is not used to compile a guest language then this state will yield
     * {@link SafepointState#canSafepoint()} {@code false} for all loops seen by the safepoint
     * elimination phase. It will yield <code>true</code> before safepoint elimination.
     * <p>
     * More information on the guest safepoint implementation in Truffle can be found
     * <a href="http://github.com/oracle/graal/blob/master/truffle/docs/Safepoints.md">here</a>.
     */
    SafepointState guestSafepointState;

    public LoopEndNode(LoopBeginNode begin) {
        super(TYPE);
        int idx = begin.nextEndIndex();
        assert NumUtil.assertNonNegativeInt(idx);
        this.endIndex = idx;
        this.loopBegin = begin;
        this.safepointState = begin.loopEndsSafepointState;
        this.guestSafepointState = begin.guestLoopEndsSafepointState;
    }

    @Override
    public AbstractMergeNode merge() {
        return loopBegin();
    }

    public LoopBeginNode loopBegin() {
        return (LoopBeginNode) loopBegin;
    }

    public void setLoopBegin(LoopBeginNode x) {
        updateUsages(this.loopBegin, x);
        this.loopBegin = x;
    }

    public void setSafepointState(SafepointState newState) {
        this.safepointState = newState;
    }

    public void setGuestSafepointState(SafepointState newState) {
        this.guestSafepointState = newState;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.visitLoopEnd(this);
        super.generate(gen);
    }

    @Override
    public boolean verifyNode() {
        assertTrue(loopBegin != null, "must have a loop begin");
        assertTrue(hasNoUsages(), "LoopEnds can not be used");
        return super.verifyNode();
    }

    /**
     * Returns the index of this loop end amongst its {@link LoopBeginNode}'s loop ends.<br>
     *
     * Since a LoopBeginNode also has {@linkplain LoopBeginNode#forwardEnds() forward ends}, this is
     * <b>not</b> the index into {@link PhiNode} values at the loop begin. Use
     * {@link LoopBeginNode#phiPredecessorIndex(AbstractEndNode)} for this purpose.
     *
     */
    int endIndex() {
        return endIndex;
    }

    void setEndIndex(int idx) {
        this.endIndex = idx;
    }

    public SafepointState getSafepointState() {
        return safepointState;
    }

    public SafepointState getGuestSafepointState() {
        return guestSafepointState;
    }

    @Override
    public Iterable<? extends Node> cfgSuccessors() {
        return Collections.emptyList();
    }

    @Override
    public NodeCycles estimatedNodeCycles() {
        if (!(loopBegin instanceof LoopBeginNode) || loopBegin() == null) {
            return CYCLES_UNKNOWN;
        }
        if (safepointState.canSafepoint()) {
            // jmp+read
            return CYCLES_2;
        }
        return super.estimatedNodeCycles();
    }

    @Override
    protected NodeSize dynamicNodeSizeEstimate() {
        if (!(loopBegin instanceof LoopBeginNode)) {
            return SIZE_UNKNOWN;
        }
        if (safepointState.canSafepoint()) {
            return SIZE_2;
        }
        return super.dynamicNodeSizeEstimate();
    }
}
