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

import com.oracle.graal.alloc.util.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CiRegister.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.*;
import com.oracle.graal.lir.cfg.*;

public class LinearScanAllocator {
    private final LIR lir;
    private final FrameMap frameMap;

    private final DataFlowAnalysis dataFlow;

    public LinearScanAllocator(LIR lir, FrameMap frameMap) {
        this.lir = lir;
        this.frameMap = frameMap;

        this.dataFlow = new DataFlowAnalysis(lir, frameMap.registerConfig);
        this.blockBeginLocations = new LocationMap[lir.linearScanOrder().size()];
        this.blockEndLocations = new LocationMap[lir.linearScanOrder().size()];
        this.moveResolver = new MoveResolverImpl(lir, frameMap);

        this.variableLastUse = new int[lir.numVariables()];
    }

    private class MoveResolverImpl extends MoveResolver {
        public MoveResolverImpl(LIR lir, FrameMap frameMap) {
            super(lir, frameMap);
        }

        @Override
        protected RiValue scratchRegister(Variable spilled) {
            GraalInternalError.shouldNotReachHere("needs working implementation");

            EnumMap<RegisterFlag, CiRegister[]> categorizedRegs = frameMap.registerConfig.getCategorizedAllocatableRegisters();
            CiRegister[] availableRegs = categorizedRegs.get(spilled.flag);
            for (CiRegister reg : availableRegs) {
                if (curInRegisterState[reg.number] == null && curOutRegisterState[reg.number] == null) {
                    return reg.asValue(spilled.kind);
                }
            }
            throw new CiBailout("No register found");
        }
    }

    private class ResolveDataFlowImpl extends ResolveDataFlow {
        public ResolveDataFlowImpl(LIR lir, MoveResolver moveResolver, DataFlowAnalysis dataFlow) {
            super(lir, moveResolver, dataFlow);
        }

        @Override
        protected LocationMap locationsForBlockBegin(Block block) {
            return beginLocationsFor(block);
        }

        @Override
        protected LocationMap locationsForBlockEnd(Block block) {
            return endLocationsFor(block);
        }
    }

    private class AssignRegistersImpl extends AssignRegisters {
        public AssignRegistersImpl(LIR lir, FrameMap frameMap) {
            super(lir, frameMap);
        }

        @Override
        protected LocationMap locationsForBlockEnd(Block block) {
            return endLocationsFor(block);
        }
    }


    private int maxRegisterNum() {
        return frameMap.target.arch.registers.length;
    }

    private boolean isAllocatableRegister(RiValue value) {
        return isRegister(value) && frameMap.registerConfig.getAttributesMap()[asRegister(value).number].isAllocatable;
    }


    private final LocationMap[] blockBeginLocations;

    private LocationMap beginLocationsFor(Block block) {
        return blockBeginLocations[block.getId()];
    }
    private void setBeginLocationsFor(Block block, LocationMap locations) {
        blockBeginLocations[block.getId()] = locations;
    }

    private final LocationMap[] blockEndLocations;

    private LocationMap endLocationsFor(Block block) {
        return blockEndLocations[block.getId()];
    }
    private void setEndLocationsFor(Block block, LocationMap locations) {
        blockEndLocations[block.getId()] = locations;
    }

    private final int[] variableLastUse;

    private int lastUseFor(Variable variable) {
        return variableLastUse[variable.index];
    }

    private void setLastUseFor(Variable variable, int lastUse) {
        variableLastUse[variable.index] = lastUse;
    }

    private MoveResolver moveResolver;
    private LocationMap curLocations;
    private RiValue[] curInRegisterState;
    private RiValue[] curOutRegisterState;
    private BitSet curLiveIn;
    private LIRInstruction curOp;

    /**
     * The spill slot for a variable, if the variable has ever been spilled.
     */
    private LocationMap canonicalSpillLocations;

    /**
     * The register that a variable got assigned at its definition, and so it should get that register when reloading after spilling.
     */
    private LocationMap hintRegisterLocations;

    public void execute() {
        assert LIRVerifier.verify(true, lir, frameMap);

        dataFlow.execute();
        IntervalPrinter.printBeforeAllocation("Before register allocation", lir, frameMap.registerConfig, dataFlow);

        allocate();

        IntervalPrinter.printAfterAllocation("After linear scan allocation", lir, frameMap.registerConfig, dataFlow, blockEndLocations);

        ResolveDataFlow resolveDataFlow = new ResolveDataFlowImpl(lir, moveResolver, dataFlow);
        resolveDataFlow.execute();
        frameMap.finish();

        IntervalPrinter.printAfterAllocation("After resolve data flow", lir, frameMap.registerConfig, dataFlow, blockEndLocations);
        assert RegisterVerifier.verify(lir, frameMap);

        AssignRegisters assignRegisters = new AssignRegistersImpl(lir, frameMap);
        assignRegisters.execute();

        Debug.dump(lir, "After register asignment");
        assert LIRVerifier.verify(false, lir, frameMap);
    }

    private void allocate() {
        ValueProcedure recordUseProc =    new ValueProcedure() { @Override public RiValue doValue(RiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return recordUse(value); } };
        ValueProcedure killNonLiveProc =  new ValueProcedure() { @Override public RiValue doValue(RiValue value) { return killNonLive(value); } };
        ValueProcedure unblockProc =      new ValueProcedure() { @Override public RiValue doValue(RiValue value) { return unblock(value); } };
        ValueProcedure killProc =         new ValueProcedure() { @Override public RiValue doValue(RiValue value) { return kill(value); } };
        ValueProcedure blockProc =        new ValueProcedure() { @Override public RiValue doValue(RiValue value) { return block(value); } };
        ValueProcedure useProc =          new ValueProcedure() { @Override public RiValue doValue(RiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return use(value, mode, flags); } };
        ValueProcedure defProc =          new ValueProcedure() { @Override public RiValue doValue(RiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return def(value, mode, flags); } };

        Debug.log("==== start linear scan allocation ====");
        canonicalSpillLocations = new LocationMap(lir.numVariables());
        hintRegisterLocations = new LocationMap(lir.numVariables());
        curInRegisterState = new RiValue[maxRegisterNum()];
        curOutRegisterState = new RiValue[maxRegisterNum()];
        for (Block block : lir.linearScanOrder()) {
            Debug.log("start block %s %s", block, block.getLoop());

            Arrays.fill(curOutRegisterState, null);
            if (block.getDominator() != null) {
                LocationMap dominatorState = endLocationsFor(block.getDominator());
                curLocations = new LocationMap(dominatorState);
                // Clear out all variables that are not live at the begin of this block
                curLiveIn = dataFlow.liveIn(block);
                curLocations.forEachLocation(killNonLiveProc);
                assert checkInputState(block);
            } else {
                curLocations = new LocationMap(lir.numVariables());
            }
            Debug.log(logCurrentState());

            for (int opIdx = 0; opIdx < block.lir.size(); opIdx++) {
                LIRInstruction op = block.lir.get(opIdx);
                curOp = op;

                Debug.log("  op %d %s", op.id(), op);

                System.arraycopy(curOutRegisterState, 0, curInRegisterState, 0, curOutRegisterState.length);

                // Unblock fixed registers that are only used for inputs in curOutRegisterState.
                dataFlow.forEachKilled(op, false, unblockProc);
                // Block fixed registers defined by this instruction in curOutRegisterState.
                op.forEachTemp(blockProc);
                op.forEachOutput(blockProc);

                op.forEachInput(recordUseProc);
                op.forEachAlive(recordUseProc);

                moveResolver.init(block.lir, opIdx);
                // Process Alive before Input because they are more restricted and the same variable can be Alive and Input.
                op.forEachAlive(useProc);
                op.forEachInput(useProc);

                dataFlow.forEachKilled(op, false, killProc);

                if (op.hasCall()) {
                    spillCallerSaveRegisters();
                }
                if (op instanceof StandardOp.PhiLabelOp) {
                    assert opIdx == 0;
                    phiRegisterHints(block);
                }

                op.forEachOutput(defProc);
                op.forEachTemp(defProc);

                // Fixed temp and output registers can evict variables from their assigned register, allocate new location for them.
                fixupEvicted();
                // State values are the least critical and can get the leftover registers (or stack slots if no more register available).
                op.forEachState(useProc);

                if (opIdx == 0) {
                    assert !moveResolver.hasMappings() : "cannot insert spill moves before label";
                    setBeginLocationsFor(block, new LocationMap(curLocations));
                }
                moveResolver.resolve();

                dataFlow.forEachKilled(op, true, unblockProc);
                dataFlow.forEachKilled(op, true, killProc);

                curOp = null;
            }

            assert endLocationsFor(block) == null;
            setEndLocationsFor(block, curLocations);

            logCurrentState();
            Debug.log("end block %s", block);
        }

        moveResolver.finish();
        Debug.log("==== end linear scan allocation ====");
    }

    private RiValue killNonLive(RiValue value) {
        assert isLocation(value);
        if (!curLiveIn.get(asLocation(value).variable.index)) {
            return null;

        } else if (isAllocatableRegister(asLocation(value).location)) {
            int regNum = asRegister(asLocation(value).location).number;
            assert curOutRegisterState[regNum] == null;
            curOutRegisterState[regNum] = value;
        }
        return value;
    }

    private RiValue unblock(RiValue value) {
        if (isAllocatableRegister(value)) {
            Debug.log("    unblock register %s", value);
            int regNum = asRegister(value).number;
            assert curOutRegisterState[regNum] == value;
            curOutRegisterState[regNum] = null;
        }
        return value;
    }

    private RiValue kill(RiValue value) {
        if (isVariable(value)) {
            Location location = curLocations.get(asVariable(value));
            Debug.log("    kill location %s", location);
            if (isRegister(location.location)) {
                int regNum = asRegister(location.location).number;
                if (curOutRegisterState[regNum] == location) {
                    curOutRegisterState[regNum] = null;
                }
            }
            curLocations.clear(asVariable(value));
        }
        return value;
    }


    private RiValue block(RiValue value) {
        if (isAllocatableRegister(value)) {
            Debug.log("    block %s", value);
            int regNum = asRegister(value).number;
            assert curOutRegisterState[regNum] == null || curOutRegisterState[regNum] instanceof Location;
            curOutRegisterState[regNum] = value;
        }
        return value;
    }

    private void spillCallerSaveRegisters() {
        Debug.log("    spill caller save registers in curInRegisterState %s", Arrays.toString(curInRegisterState));
        for (CiRegister reg : frameMap.registerConfig.getCallerSaveRegisters()) {
            RiValue in = curInRegisterState[reg.number];
            if (in != null && isLocation(in)) {
                spill(asLocation(in));
            }
        }
    }

    private RiValue recordUse(RiValue value) {
        if (isVariable(value)) {
            assert lastUseFor(asVariable(value)) <= curOp.id();
            setLastUseFor(asVariable(value), curOp.id());

        }
        return value;
    }

    private RiValue use(RiValue value, OperandMode mode, EnumSet<OperandFlag> flags) {
        assert mode == OperandMode.Input || mode == OperandMode.Alive;
        if (isVariable(value)) {
            // State values are not recorded beforehand because it does not matter if they are spilled. Still, it is necessary to record them as used now.
            recordUse(value);

            Location curLoc = curLocations.get(asVariable(value));
            if (isStackSlot(curLoc.location) && flags.contains(OperandFlag.Stack)) {
                Debug.log("    use %s %s: use current stack slot %s", mode, value, curLoc.location);
                return curLoc;
            }
            if (isRegister(curLoc.location)) {
                int regNum = asRegister(curLoc.location).number;
                assert curInRegisterState[regNum] == curLoc;
                if (mode == OperandMode.Input || curOutRegisterState[regNum] == curLoc) {
                    Debug.log("    use %s %s: use current register %s", mode, value, curLoc.location);
                    return curLoc;
                }
            }

            Debug.log("    use %s %s", mode, value);

            Location newLoc = allocateRegister(asVariable(value), mode, flags);
            if (newLoc != curLoc) {
                moveResolver.add(curLoc, newLoc);
            }
            return newLoc;
        } else {
            assert !isAllocatableRegister(value) || curInRegisterState[asRegister(value).number] == value;
        }
        return value;
    }

    private static final EnumSet<OperandFlag> SPILL_FLAGS = EnumSet.of(OperandFlag.Register, OperandFlag.Stack);

    private RiValue def(RiValue value, OperandMode mode, EnumSet<OperandFlag> flags) {
        assert mode == OperandMode.Temp || mode == OperandMode.Output;
        if (isVariable(value)) {
            Debug.log("    def %s %s", mode, value);
            assert curLocations.get(asVariable(value)) == null;

            Location newLoc = allocateRegister(asVariable(value), mode, flags);
            return newLoc;
        }
        return value;
    }


    private void fixupEvicted() {
        for (int i = 0; i < curInRegisterState.length; i++) {
            RiValue in = curInRegisterState[i];
            RiValue out = curOutRegisterState[i];

            if (in != null && in != out && isLocation(in) && curLocations.get(asLocation(in).variable) == in) {
                Debug.log("    %s was evicted by %s, need to allocate new location", in, out);
                Location oldLoc = asLocation(in);
                Location newLoc = allocateRegister(oldLoc.variable, OperandMode.Alive, SPILL_FLAGS);
                assert oldLoc != newLoc;
                moveResolver.add(oldLoc, newLoc);
            }


        }
    }


    private void phiRegisterHints(Block block) {
        Debug.log("    phi register hints for %s", block);
        RiValue[] phiDefinitions = ((StandardOp.PhiLabelOp) block.lir.get(0)).getPhiDefinitions();
        for (Block pred : block.getPredecessors()) {
            RiValue[] phiInputs = ((StandardOp.PhiJumpOp) pred.lir.get(pred.lir.size() - 1)).getPhiInputs();

            for (int i = 0; i < phiDefinitions.length; i++) {
                RiValue phiDefinition = phiDefinitions[i];
                RiValue phiInput = phiInputs[i];

                if (isVariable(phiDefinition)) {
                    Location hintResult = processRegisterHint(asVariable(phiDefinition), OperandMode.Output, phiInput);
                    if (hintResult != null) {
                        phiDefinitions[i] = hintResult;
                    }
                }
            }
        }
    }

    private Location processRegisterHint(Variable variable, OperandMode mode, RiValue registerHint) {
        if (registerHint == null) {
            return null;
        }
        Debug.log("      try registerHint for %s %s: %s", mode, variable, registerHint);
        CiRegister hint = null;
        if (isRegister(registerHint)) {
            hint = asRegister(registerHint);
        } else if (isLocation(registerHint) && isRegister(asLocation(registerHint).location)) {
            hint = asRegister(asLocation(registerHint).location);
        }
        if (hint != null && hint.isSet(variable.flag) && isFree(hint, mode)) {
            return selectRegister(hint, variable, mode);
        }
        return null;
    }

    private Location allocateRegister(final Variable variable, final OperandMode mode, EnumSet<OperandFlag> flags) {
        if (flags.contains(OperandFlag.RegisterHint)) {
            RiValue hintResult = curOp.forEachRegisterHint(variable, mode, new ValueProcedure() {
                @Override
                public RiValue doValue(RiValue registerHint) {
                    return processRegisterHint(variable, mode, registerHint);
                }
            });
            if (hintResult != null) {
                return asLocation(hintResult);
            }
        }

        RiValue hintResult = processRegisterHint(variable, mode, hintRegisterLocations.get(variable));
        if (hintResult != null) {
            return asLocation(hintResult);
        }

        EnumMap<RegisterFlag, CiRegister[]> categorizedRegs = frameMap.registerConfig.getCategorizedAllocatableRegisters();
        CiRegister[] availableRegs = categorizedRegs.get(variable.flag);

        Location bestSpillCandidate = null;
        for (CiRegister reg : availableRegs) {
            if (isFree(reg, mode)) {
                return selectRegister(reg, variable, mode);
            } else {
                Location spillCandidate = spillCandidate(reg);
                if (betterSpillCandidate(spillCandidate, bestSpillCandidate)) {
                    bestSpillCandidate = spillCandidate;
                }
            }
        }

        if (flags.contains(OperandFlag.Stack) && betterSpillCandidate(curLocations.get(variable), bestSpillCandidate)) {
            return selectSpillSlot(variable);
        }

        if (bestSpillCandidate == null) {
            // This should not happen as long as all LIR instructions have fulfillable register constraints. But be safe in product mode and bail out.
            assert false;
            throw new GraalInternalError("No register available");
        }

        spill(bestSpillCandidate);

        return selectRegister(asRegister(bestSpillCandidate.location), variable, mode);
    }

    private void spill(Location value) {
        Location newLoc = spillLocation(value.variable);
        Debug.log("      spill %s to %s", value, newLoc);
        if (!(curOp instanceof StandardOp.PhiLabelOp)) {
            moveResolver.add(value, newLoc);
        }
        curLocations.put(newLoc);

        CiRegister reg = asRegister(value.location);
        assert curInRegisterState[reg.number] == value;
        curInRegisterState[reg.number] = null;
        if (curOutRegisterState[reg.number] == value) {
            curOutRegisterState[reg.number] = null;
        }
    }

    private boolean isFree(CiRegister reg, OperandMode mode) {
        switch (mode) {
            case Input:  return curInRegisterState[reg.number] == null;
            case Alive:  return curInRegisterState[reg.number] == null && curOutRegisterState[reg.number] == null;
            case Temp:   return curOutRegisterState[reg.number] == null;
            case Output: return curOutRegisterState[reg.number] == null;
            default:     throw GraalInternalError.shouldNotReachHere();
        }
    }

    private Location spillCandidate(CiRegister reg) {
        RiValue in = curInRegisterState[reg.number];
        RiValue out = curOutRegisterState[reg.number];
        if (in == out && in != null && isLocation(in) && lastUseFor(asLocation(in).variable) < curOp.id()) {
            return asLocation(in);
        }
        return null;
    }

    private boolean betterSpillCandidate(Location loc, Location compare) {
        if (loc == null) {
            return false;
        }
        if (compare == null) {
            return true;
        }
        if (canonicalSpillLocations.get(loc.variable) != null && canonicalSpillLocations.get(compare.variable) == null) {
            return true;
        }
        return dataFlow.definition(loc.variable) < dataFlow.definition(compare.variable);
    }

    private Location spillLocation(Variable variable) {
        Location result = canonicalSpillLocations.get(variable);
        if (result == null) {
            result = new Location(variable, frameMap.allocateSpillSlot(variable.kind));
            canonicalSpillLocations.put(result);
        }
        return result;
    }

    private Location selectRegister(CiRegister reg, Variable variable, OperandMode mode) {
        assert isFree(reg, mode);

        Location loc = new Location(variable, reg.asValue(variable.kind));
        if (mode == OperandMode.Input || mode == OperandMode.Alive) {
            curInRegisterState[reg.number] = loc;
        }
        curOutRegisterState[reg.number] = loc;
        curLocations.put(loc);
        recordUse(variable);
        if (hintRegisterLocations.get(variable) == null) {
            hintRegisterLocations.put(loc);
        }

        Debug.log("      selected register %s", loc);
        return loc;
    }

    private Location selectSpillSlot(Variable variable) {
        Location loc = spillLocation(variable);
        curLocations.put(loc);
        recordUse(variable);

        Debug.log("      selected spill slot %s", loc);
        return loc;
    }

    private boolean checkInputState(final Block block) {
        final BitSet liveState = new BitSet();
        curLocations.forEachLocation(new ValueProcedure() {
            @Override
            public RiValue doValue(RiValue value) {
                liveState.set(asLocation(value).variable.index);

                for (Block pred : block.getPredecessors()) {
                    LocationMap predState = endLocationsFor(pred);
                    if (predState != null) {
                        assert predState.get(asLocation(value).variable) != null;
                    } else {
                        assert block.isLoopHeader();
                    }
                }
                return value;
            }
        });
        assert liveState.equals(curLiveIn);
        return true;
    }


    private String logCurrentState() {
        final StringBuilder sb = new StringBuilder();
        sb.append("  current lcoations: ");
        curLocations.forEachLocation(new ValueProcedure() {
            @Override
            public RiValue doValue(RiValue value) {
                sb.append(value).append(" ");
                return value;
            }
        });
        return sb.toString();
    }
}
