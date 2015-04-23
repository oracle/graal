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
package com.oracle.graal.lir.ssa;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.StandardOp.LabelOp;

/**
 * Utilities for working with Static-Single-Assignment LIR form.
 *
 * <h2>Representation of <code>PHI</code>s</h2>
 *
 * There is no explicit <code>PHI</code> {@linkplain LIRInstruction}. Instead, they are implemented
 * as parallel copy that span across a control-flow edge.
 *
 * The variables introduced by <code>PHI</code>s of a specific {@linkplain AbstractBlockBase merge
 * block} are {@linkplain LabelOp#setIncomingValues attached} to the {@linkplain LabelOp} of the
 * block. The outgoing values from the predecessor are {@link JumpOp#setOutgoingValues input} to the
 * {@linkplain BlockEndOp} of the predecessor. Because there are no critical edges we know that the
 * {@link BlockEndOp} of the predecessor has to be a {@link JumpOp}.
 *
 * <h3>Example:</h3>
 *
 * <pre>
 * B0 -> B1
 *   ...
 *   v0|i = ...
 *   JUMP ~[v0|i, int[0|0x0]] destination: B0 -> B1
 * ________________________________________________
 *
 * B2 -> B1
 *   ...
 *   v1|i = ...
 *   v2|i = ...
 *   JUMP ~[v1|i, v2|i] destination: B2 -> B1
 * ________________________________________________
 *
 * B1 <- B0,B2
 *   [v3|i, v4|i] = LABEL
 *   ...
 * </pre>
 */
public final class SSAUtils {

    public interface PhiValueVisitor {
        /**
         * @param phiIn the incoming value at the merge block
         * @param phiOut the outgoing value from the predecessor block
         */
        void visit(Value phiIn, Value phiOut);
    }

    /**
     * Visits each phi value pair of an edge, i.e. the outgoing value from the predecessor and the
     * incoming value to the merge block.
     */
    public static void forEachPhiValuePair(LIR lir, AbstractBlockBase<?> merge, AbstractBlockBase<?> pred, PhiValueVisitor visitor) {
        if (merge.getPredecessorCount() < 2) {
            return;
        }
        assert merge.getPredecessors().contains(pred) : String.format("%s not in predecessor list: %s", pred, merge.getPredecessors());
        assert pred.getSuccessorCount() == 1 : String.format("Merge predecessor block %s has more than one successor? %s", pred, pred.getSuccessors());
        assert pred.getSuccessors().get(0) == merge : String.format("Predecessor block %s has wrong successor: %s, should be: %s", pred, pred.getSuccessors().get(0), merge);

        List<LIRInstruction> instructions = lir.getLIRforBlock(pred);
        JumpOp jump = (JumpOp) instructions.get(instructions.size() - 1);
        LabelOp label = (LabelOp) lir.getLIRforBlock(merge).get(0);

        assert label.getIncomingSize() == jump.getOutgoingSize() : String.format("Phi In/Out size mismatch: in=%d vs. out=%d", label.getIncomingSize(), jump.getOutgoingSize());

        for (int i = 0; i < label.getIncomingSize(); i++) {
            visitor.visit(label.getIncomingValue(i), jump.getOutgoingValue(i));
        }
    }

    public static boolean verifySSAForm(LIR lir) {
        return new SSAVerifier(lir).verify();
    }

    public static void verifyPhi(LIR lir, AbstractBlockBase<?> merge) {
        assert merge.getPredecessorCount() > 1;
        for (AbstractBlockBase<?> pred : merge.getPredecessors()) {
            forEachPhiValuePair(lir, merge, pred, (phiIn, phiOut) -> {
                assert phiIn.getLIRKind().equals(phiOut.getLIRKind()) ||
                                (phiIn.getPlatformKind().equals(phiOut.getPlatformKind()) && phiIn.getLIRKind().isDerivedReference() && phiOut.getLIRKind().isValue());
            });
        }
    }

    public static void removePhiOut(LIR lir, AbstractBlockBase<?> block) {
        assert block.getSuccessorCount() == 1;
        List<LIRInstruction> instructions = lir.getLIRforBlock(block);
        JumpOp jump = (JumpOp) instructions.get(instructions.size() - 1);
        jump.clearOutgoingValues();
    }

    public static void removePhiIn(LIR lir, AbstractBlockBase<?> block) {
        assert block.getPredecessorCount() > 1;
        LabelOp label = (LabelOp) lir.getLIRforBlock(block).get(0);
        label.clearIncomingValues();
    }

    public static int phiOutIndex(LIR lir, AbstractBlockBase<?> block) {
        assert block.getSuccessorCount() == 1;
        List<LIRInstruction> instructions = lir.getLIRforBlock(block);
        int index = instructions.size() - 1;
        assert instructions.get(index) instanceof JumpOp;
        return index;
    }

}
