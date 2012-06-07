/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.alloc.simple;

import static com.oracle.graal.alloc.util.LocationUtil.*;

import java.util.*;

import com.oracle.max.cri.ri.*;
import com.oracle.graal.alloc.util.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.cfg.*;

public abstract class ResolveDataFlow {
    public final LIR lir;
    public final MoveResolver moveResolver;
    public final DataFlowAnalysis dataFlow;

    public ResolveDataFlow(LIR lir, MoveResolver moveResolver, DataFlowAnalysis dataFlow) {
        this.lir = lir;
        this.moveResolver = moveResolver;
        this.dataFlow = dataFlow;
    }

    private LocationMap curFromLocations;

    public void execute() {
        ValueProcedure locMappingProc = new ValueProcedure() { @Override public RiValue doValue(RiValue value) { return locMapping(value); } };

        Debug.log("==== start resolve data flow ====");
        for (Block toBlock : lir.linearScanOrder()) {
            PhiLabelOp phiDefs = null;
            if (toBlock.lir.get(0) instanceof PhiLabelOp) {
                phiDefs = (PhiLabelOp) toBlock.lir.get(0);
            }

            for (Block fromBlock : toBlock.getPredecessors()) {
                Debug.log("start edge %s -> %s", fromBlock, toBlock);
                findInsertPos(fromBlock, toBlock);

                LocationMap toLocations = locationsForBlockBegin(toBlock);
                curFromLocations = locationsForBlockEnd(fromBlock);
                if (toLocations != curFromLocations) {
                    toLocations.forEachLocation(locMappingProc);
                }

                if (phiDefs != null) {
                    PhiJumpOp phiInputs = (PhiJumpOp) fromBlock.lir.get(fromBlock.lir.size() - 1);
                    phiMapping(phiInputs.getPhiInputs(), phiDefs.getPhiDefinitions());
                    phiInputs.markResolved();
                }

                moveResolver.resolve();
                Debug.log("end edge %s -> %s", fromBlock, toBlock);
            }

            if (phiDefs != null) {
                // Phi functions are resolved with moves now, so delete them.
                phiDefs.markResolved();
            }
        }
        moveResolver.finish();
        Debug.log("==== end resolve data flow ====");
    }

    private RiValue locMapping(RiValue value) {
        Location to = asLocation(value);
        Location from = curFromLocations.get(to.variable);
        if (value != from && from != null) {
            moveResolver.add(from, to);
        }
        return value;
    }

    private void phiMapping(RiValue[] inputs, RiValue[] outputs) {
        assert inputs.length == outputs.length;
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] != outputs[i]) {
                moveResolver.add(inputs[i], asLocation(outputs[i]));
            }
        }
    }

    private void findInsertPos(Block fromBlock, Block toBlock) {
        assert fromBlock.getSuccessors().contains(toBlock) && toBlock.getPredecessors().contains(fromBlock);

        if (fromBlock.numberOfSux() == 1) {
            List<LIRInstruction> instructions = fromBlock.lir;
            LIRInstruction instr = instructions.get(instructions.size() - 1);
            assert instr instanceof StandardOp.JumpOp : "block does not end with an unconditional jump";
            moveResolver.init(instructions, instructions.size() - 1);
            Debug.log("  insert at end of %s before %d", fromBlock, instructions.size() - 1);

        } else if (toBlock.numberOfPreds() == 1) {
            moveResolver.init(toBlock.lir, 1);
            Debug.log("  insert at beginning of %s before %d", toBlock, 1);

        } else {
            GraalInternalError.shouldNotReachHere("Critical edge not split");
        }
    }

    protected abstract LocationMap locationsForBlockBegin(Block block);
    protected abstract LocationMap locationsForBlockEnd(Block block);
}
