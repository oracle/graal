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
package com.oracle.max.graal.alloc.simple;

import static com.oracle.max.graal.alloc.util.ValueUtil.*;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.alloc.util.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.lir.LIRInstruction.ValueProcedure;
import com.oracle.max.graal.compiler.lir.LIRPhiMapping.PhiValueProcedure;
import com.oracle.max.graal.compiler.util.*;

public abstract class ResolveDataFlow {
    public final LIR lir;
    public final MoveResolver moveResolver;
    public final DataFlowAnalysis dataFlow;

    public ResolveDataFlow(LIR lir, MoveResolver moveResolver, DataFlowAnalysis dataFlow) {
        this.lir = lir;
        this.moveResolver = moveResolver;
        this.dataFlow = dataFlow;
    }

    private LIRBlock curToBlock;
    private LocationMap curFromLocations;

    public void execute() {
        ValueProcedure locMappingProc =    new ValueProcedure() {    @Override public CiValue doValue(CiValue value) { return locMapping(value); } };
        PhiValueProcedure phiMappingProc = new PhiValueProcedure() { @Override public CiValue doValue(CiValue input, CiValue output) { return phiMapping(input, output); } };

        assert trace("==== start resolve data flow ====");
        for (LIRBlock toBlock : lir.linearScanOrder()) {
            curToBlock = toBlock;

            for (LIRBlock fromBlock : toBlock.getLIRPredecessors()) {
                assert trace("start edge %s -> %s", fromBlock, toBlock);
                findInsertPos(fromBlock, toBlock);

                LocationMap toLocations = locationsForBlockBegin(toBlock);
                curFromLocations = locationsForBlockEnd(fromBlock);
                if (toLocations != curFromLocations) {
                    toLocations.forEachLocation(locMappingProc);
                }

                if (toBlock.phis != null) {
                    toBlock.phis.forEachInput(fromBlock, phiMappingProc);
                }

                moveResolver.resolve();
                assert trace("end edge %s -> %s", fromBlock, toBlock);
            }

            // Phi functions are resolved with moves now, so delete them.
            toBlock.phis = null;
        }
        moveResolver.finish();
        assert trace("==== end resolve data flow ====");
    }

    private CiValue locMapping(CiValue value) {
        Location to = asLocation(value);
        Location from = curFromLocations.get(to.variable);
        if (value != from && dataFlow.liveIn(curToBlock).get(to.variable.index)) {
            moveResolver.add(from, to);
        }
        return value;
    }

    private CiValue phiMapping(CiValue input, CiValue output) {
        if (input != output) {
            moveResolver.add(input, asLocation(output));
        }
        return input;
    }

    private void findInsertPos(LIRBlock fromBlock, LIRBlock toBlock) {
        assert fromBlock.getSuccessors().contains(toBlock) && toBlock.getPredecessors().contains(fromBlock);

        if (fromBlock.numberOfSux() == 1) {
            List<LIRInstruction> instructions = fromBlock.lir();
            LIRInstruction instr = instructions.get(instructions.size() - 1);
            assert instr instanceof LIRBranch && instr.code == StandardOpcode.JUMP : "block does not end with an unconditional jump";
            moveResolver.init(instructions, instructions.size() - 1);
            assert trace("  insert at end of %s before %d", fromBlock, instructions.size() - 1);

        } else if (toBlock.numberOfPreds() == 1) {
            moveResolver.init(toBlock.lir(), 1);
            assert trace("  insert at beginning of %s before %d", toBlock, 1);

        } else {
            Util.shouldNotReachHere("Critical edge not split");
        }
    }

    protected abstract LocationMap locationsForBlockBegin(LIRBlock block);
    protected abstract LocationMap locationsForBlockEnd(LIRBlock block);


    private static boolean trace(String format, Object...args) {
        if (GraalOptions.TraceRegisterAllocation) {
            TTY.println(format, args);
        }
        return true;
    }
}
