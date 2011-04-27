/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.opt;

import com.sun.c1x.graph.IR;
import com.sun.c1x.ir.*;
import com.sun.c1x.value.*;

/**
 * The {@code PhiSimplifier} class is a helper class that can reduce phi instructions.
 *
 * @author Ben L. Titzer
 */
public final class PhiSimplifier implements BlockClosure {

    final IR ir;
    final InstructionSubstituter subst;

    public PhiSimplifier(IR ir) {
        this.ir = ir;
        this.subst = new InstructionSubstituter(ir);
        ir.startBlock.iterateAnyOrder(this, false);
        subst.finish();
    }

    /**
     * This method is called for each block and processes any phi statements in the block.
     * @param block the block to apply the simplification to
     */
    public void apply(BlockBegin block) {
        FrameState state = block.stateBefore();
        for (int i = 0; i < state.stackSize(); i++) {
            simplify(state.stackAt(i));
        }
        for (int i = 0; i < state.localsSize(); i++) {
            simplify(state.localAt(i));
        }
    }

    Value simplify(Value x) {
        if (x == null || !(x instanceof Phi)) {
            return x;
        }
        Phi phi = (Phi) x;
        if (phi.hasSubst()) {
            // already substituted, but the subst could be a phi itself, so simplify
            return simplify(subst.getSubst(phi));
        } else if (phi.checkFlag(Value.Flag.PhiCannotSimplify)) {
            // already tried, cannot simplify this phi
            return phi;
        } else if (phi.checkFlag(Value.Flag.PhiVisited)) {
            // break cycles in phis
            return phi;
        } else if (phi.isIllegal()) {
            // don't bother with illegals
            return phi;
        } else {
            // attempt to simplify the phi by recursively simplifying its operands
            phi.setFlag(Value.Flag.PhiVisited);
            Value phiSubst = null;
            int max = phi.inputCount();
            boolean cannotSimplify = false;
            for (int i = 0; i < max; i++) {
                Value oldInstr = phi.inputAt(i);

                if (oldInstr == null || oldInstr.isIllegal() || oldInstr.isDeadPhi()) {
                    // if one operand is illegal, make the entire phi illegal
                    phi.makeDead();
                    phi.clearFlag(Value.Flag.PhiVisited);
                    return phi;
                }

                Value newInstr = simplify(oldInstr);

                if (newInstr == null || newInstr.isIllegal() || newInstr.isDeadPhi()) {
                    // if the subst instruction is illegal, make the entire phi illegal
                    phi.makeDead();
                    phi.clearFlag(Value.Flag.PhiVisited);
                    return phi;
                }

                // attempt to simplify this operand
                if (!cannotSimplify) {

                    if (newInstr != phi && newInstr != phiSubst) {
                        if (phiSubst == null) {
                            phiSubst = newInstr;
                            continue;
                        }
                        // this phi cannot be simplified
                        cannotSimplify = true;
                    }
                }
            }
            if (cannotSimplify) {
                phi.setFlag(Value.Flag.PhiCannotSimplify);
                phi.clearFlag(Value.Flag.PhiVisited);
                return phi;
            }

            // successfully simplified the phi
            assert phiSubst != null : "illegal phi function";
            phi.clearFlag(Value.Flag.PhiVisited);
            subst.setSubst(phi, phiSubst);
            return phiSubst;
        }
    }
}
