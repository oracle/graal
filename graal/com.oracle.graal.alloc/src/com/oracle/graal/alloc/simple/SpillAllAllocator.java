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

import static com.oracle.max.cri.ci.CiValueUtil.*;
import static com.oracle.graal.alloc.util.LocationUtil.*;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ci.CiRegister.RegisterFlag;
import com.oracle.max.cri.ri.*;
import com.oracle.graal.alloc.util.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.*;
import com.oracle.graal.lir.cfg.*;

public class SpillAllAllocator {
    private final LIR lir;
    private final FrameMap frameMap;

    private final DataFlowAnalysis dataFlow;

    public SpillAllAllocator(LIR lir, FrameMap frameMap) {
        this.lir = lir;
        this.frameMap = frameMap;

        this.dataFlow = new DataFlowAnalysis(lir, frameMap.registerConfig);
        this.blockLocations = new LocationMap[lir.linearScanOrder().size()];
        this.moveResolver = new MoveResolverImpl(lir, frameMap);
    }

    private class MoveResolverImpl extends MoveResolver {
        public MoveResolverImpl(LIR lir, FrameMap frameMap) {
            super(lir, frameMap);
        }

        @Override
        protected RiValue scratchRegister(Variable spilled) {
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
            assert block.numberOfPreds() > 0 && block.getDominator() != null;
            return locationsFor(block.getDominator());
        }

        @Override
        protected LocationMap locationsForBlockEnd(Block block) {
            return locationsFor(block);
        }
    }

    private class AssignRegistersImpl extends AssignRegisters {
        public AssignRegistersImpl(LIR lir, FrameMap frameMap) {
            super(lir, frameMap);
        }

        @Override
        protected LocationMap locationsForBlockEnd(Block block) {
            return locationsFor(block);
        }
    }


    private int maxRegisterNum() {
        return frameMap.target.arch.registers.length;
    }

    private boolean isAllocatableRegister(RiValue value) {
        return isRegister(value) && frameMap.registerConfig.getAttributesMap()[asRegister(value).number].isAllocatable;
    }


    private final LocationMap[] blockLocations;

    private LocationMap locationsFor(Block block) {
        return blockLocations[block.getId()];
    }
    private void setLocationsFor(Block block, LocationMap locations) {
        blockLocations[block.getId()] = locations;
    }

    private MoveResolver moveResolver;
    private LocationMap curStackLocations;
    private LocationMap curRegisterLocations;
    private Object[] curInRegisterState;
    private Object[] curOutRegisterState;
    private BitSet curLiveIn;
    private LIRInstruction curInstruction;

    public void execute() {
        assert LIRVerifier.verify(true, lir, frameMap);

        dataFlow.execute();
        IntervalPrinter.printBeforeAllocation("Before register allocation", lir, frameMap.registerConfig, dataFlow);

        allocate();

        IntervalPrinter.printAfterAllocation("After spill all allocation", lir, frameMap.registerConfig, dataFlow, blockLocations);

        ResolveDataFlow resolveDataFlow = new ResolveDataFlowImpl(lir, moveResolver, dataFlow);
        resolveDataFlow.execute();
        frameMap.finish();

        IntervalPrinter.printAfterAllocation("After resolve data flow", lir, frameMap.registerConfig, dataFlow, blockLocations);
        assert RegisterVerifier.verify(lir, frameMap);

        AssignRegisters assignRegisters = new AssignRegistersImpl(lir, frameMap);
        assignRegisters.execute();

        Debug.dump(lir, "After register asignment");
        assert LIRVerifier.verify(false, lir, frameMap);
    }

    private void allocate() {
        ValueProcedure killNonLiveProc =  new ValueProcedure() { @Override public RiValue doValue(RiValue value) { return killNonLive(value); } };
        ValueProcedure killBeginProc =    new ValueProcedure() { @Override public RiValue doValue(RiValue value) { return kill(value, false); } };
        ValueProcedure killEndProc =      new ValueProcedure() { @Override public RiValue doValue(RiValue value) { return kill(value, true); } };
        ValueProcedure killLocationProc = new ValueProcedure() { @Override public RiValue doValue(RiValue value) { return killLocation(value); } };
        ValueProcedure blockProc =        new ValueProcedure() { @Override public RiValue doValue(RiValue value) { return block(value); } };
        ValueProcedure loadProc =         new ValueProcedure() { @Override public RiValue doValue(RiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return load(value, mode, flags); } };
        ValueProcedure spillProc =        new ValueProcedure() { @Override public RiValue doValue(RiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return spill(value, mode, flags); } };
        ValueProcedure useSlotProc =      new ValueProcedure() { @Override public RiValue doValue(RiValue value) { return useSlot(value); } };

        Debug.log("==== start spill all allocation ====");
        curInRegisterState = new Object[maxRegisterNum()];
        curOutRegisterState = new Object[maxRegisterNum()];
        curRegisterLocations = new LocationMap(lir.numVariables());
        for (Block block : lir.linearScanOrder()) {
            Debug.log("start block %s %s", block, block.getLoop());
            assert checkEmpty(curOutRegisterState);

            if (block.getDominator() != null) {
                LocationMap dominatorState = locationsFor(block.getDominator());
                curStackLocations = new LocationMap(dominatorState);
                // Clear out all variables that are not live at the begin of this block
                curLiveIn = dataFlow.liveIn(block);
                curStackLocations.forEachLocation(killNonLiveProc);
                assert checkInputState(block);
            } else {
                curStackLocations = new LocationMap(lir.numVariables());
            }
            Debug.log(logCurrentState());

            for (int opIdx = 0; opIdx < block.lir.size(); opIdx++) {
                LIRInstruction op = block.lir.get(opIdx);
                curInstruction = op;
                Debug.log("  op %d %s", op.id(), op);

                assert curRegisterLocations.checkEmpty();

                System.arraycopy(curOutRegisterState, 0, curInRegisterState, 0, curOutRegisterState.length);

                // Block fixed registers that are defined by this instruction, so that they are no longer available for normal allocation.
                op.forEachTemp(blockProc);
                op.forEachOutput(blockProc);

                moveResolver.init(block.lir, opIdx);
                // Process Alive before Input because they are more restricted and the same variable can be Alive and Input.
                op.forEachAlive(loadProc);
                op.forEachInput(loadProc);
                moveResolver.resolve();
                op.forEachState(useSlotProc);

                dataFlow.forEachKilled(op, false, killBeginProc);
                assert !op.hasCall() || checkNoCallerSavedRegister() : "caller saved register in use accross call site";

                moveResolver.init(block.lir, opIdx + 1);
                op.forEachTemp(spillProc);
                op.forEachOutput(spillProc);
                moveResolver.resolve();

                dataFlow.forEachKilled(op, true, killEndProc);
                curRegisterLocations.forEachLocation(killLocationProc);

                assert curRegisterLocations.checkEmpty();
                curInstruction = null;
            }
            assert checkEmpty(curOutRegisterState);
            assert locationsFor(block) == null;
            setLocationsFor(block, curStackLocations);

            logCurrentState();
            Debug.log("end block %s", block);
        }

        moveResolver.finish();
        Debug.log("==== end spill all allocation ====");
    }

    private RiValue killNonLive(RiValue value) {
        assert isLocation(value);
        if (!curLiveIn.get(asLocation(value).variable.index)) {
            return null;
        }
        return value;
    }

    private RiValue kill(RiValue value, boolean end) {
        if (isVariable(value)) {
            Debug.log("    kill variable %s", value);

            Variable variable = asVariable(value);
            curStackLocations.clear(variable);

            Location loc = curRegisterLocations.get(variable);
            if (loc != null) {
                killLocation(loc);
                curRegisterLocations.clear(variable);

                Debug.log("      location %s", loc);
                assert isAllocatableRegister(loc.location);

                int regNum = asRegister(loc.location).number;
                if (curOutRegisterState[regNum] == loc) {
                    curOutRegisterState[regNum] = null;
                }
            }

        } else if (isAllocatableRegister(value)) {
            Debug.log("    kill register %s", value);
            int regNum = asRegister(value).number;
            assert curOutRegisterState[regNum] == null || curOutRegisterState[regNum] instanceof LIRInstruction && curInstruction != null;

            if (end || curOutRegisterState[regNum] != curInstruction) {
                curOutRegisterState[regNum] = null;
            }

        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
        return value;
    }

    private RiValue killLocation(RiValue value) {
        Debug.log("    kill location %s", value);
        assert isAllocatableRegister(asLocation(value).location);

        int regNum = asRegister(asLocation(value).location).number;
        if (curOutRegisterState[regNum] == value) {
            curOutRegisterState[regNum] = null;
        }
        return null;
    }

    private RiValue block(RiValue value) {
        if (isAllocatableRegister(value)) {
            Debug.log("    block %s", value);
            int regNum = asRegister(value).number;
            assert curInstruction != null;
            assert curOutRegisterState[regNum] == null || curOutRegisterState[regNum] instanceof LIRInstruction;
            curOutRegisterState[regNum] = curInstruction;
        }
        return value;
    }

    private RiValue load(RiValue value, OperandMode mode, EnumSet<OperandFlag> flags) {
        assert mode == OperandMode.Input || mode == OperandMode.Alive;
        if (flags.contains(OperandFlag.Stack)) {
            return useSlot(value);
        }
        if (isVariable(value)) {
            Debug.log("    load %s", value);
            Location regLoc = curRegisterLocations.get(asVariable(value));
            if (regLoc != null) {
                // This variable has already been processed before.
                Debug.log("      found location %s", regLoc);
            } else {
                regLoc = allocateRegister(asVariable(value), curInRegisterState, mode == OperandMode.Alive ? curOutRegisterState : null, mode, flags);
                Location stackLoc = curStackLocations.get(asVariable(value));
                assert stackLoc != null;
                moveResolver.add(stackLoc, regLoc);
            }
            return regLoc;
        } else {
            assert !isAllocatableRegister(value) || curInRegisterState[asRegister(value).number] instanceof LIRInstruction;
            return value;
        }
    }

    private RiValue spill(RiValue value, OperandMode mode, EnumSet<OperandFlag> flags) {
        assert mode == OperandMode.Temp || mode == OperandMode.Output;
        if (flags.contains(OperandFlag.Stack)) {
            return defSlot(value);
        }
        if (isVariable(value)) {
            Debug.log("    spill %s", value);
            assert curStackLocations.get(asVariable(value)) == null;
            Location regLoc = allocateRegister(asVariable(value), null, curOutRegisterState, mode, flags);
            if (mode == OperandMode.Output) {
                Location stackLoc = new Location(asVariable(value), frameMap.allocateSpillSlot(value.kind));
                curStackLocations.put(stackLoc);
                moveResolver.add(regLoc, stackLoc);
            }
            return regLoc;
        } else {
            assert !isAllocatableRegister(value) || curOutRegisterState[asRegister(value).number] == curInstruction && curInstruction != null;
            return value;
        }
    }

    private RiValue useSlot(RiValue value) {
        if (isVariable(value)) {
            Debug.log("    useSlot %s", value);
            Location stackLoc = curStackLocations.get(asVariable(value));
            assert stackLoc != null;
            Debug.log("      slot %s", stackLoc);
            return stackLoc;
        } else {
            return value;
        }
    }

    private RiValue defSlot(RiValue value) {
        if (isVariable(value)) {
            Debug.log("    assignSlot %s", value);
            Location stackLoc = new Location(asVariable(value), frameMap.allocateSpillSlot(value.kind));
            assert curStackLocations.get(asVariable(value)) == null;
            curStackLocations.put(stackLoc);
            Debug.log("      slot %s", stackLoc);
            return stackLoc;
        } else {
            return value;
        }
    }

    private Location allocateRegister(final Variable variable, final Object[] inRegisterState, final Object[] outRegisterState, OperandMode mode, EnumSet<OperandFlag> flags) {
        if (flags.contains(OperandFlag.RegisterHint)) {
            RiValue result = curInstruction.forEachRegisterHint(variable, mode, new ValueProcedure() {
                @Override
                public RiValue doValue(RiValue registerHint) {
                    Debug.log("      registerHint %s", registerHint);
                    CiRegister hint = null;
                    if (isRegister(registerHint)) {
                        hint = asRegister(registerHint);
                    } else if (isLocation(registerHint) && isRegister(asLocation(registerHint).location)) {
                        hint = asRegister(asLocation(registerHint).location);
                    }
                    if (hint != null && hint.isSet(variable.flag) && isFree(hint, inRegisterState, outRegisterState)) {
                        return selectRegister(hint, variable, inRegisterState, outRegisterState);
                    }
                    return null;
                }
            });

            if (result != null) {
                return asLocation(result);
            }
        }

        EnumMap<RegisterFlag, CiRegister[]> categorizedRegs = frameMap.registerConfig.getCategorizedAllocatableRegisters();
        CiRegister[] availableRegs = categorizedRegs.get(variable.flag);

        for (CiRegister reg : availableRegs) {
            if (isFree(reg, inRegisterState, outRegisterState)) {
                return selectRegister(reg, variable, inRegisterState, outRegisterState);
            }

        }
        throw new CiBailout("No register found");
    }

    private static boolean isFree(CiRegister reg, Object[] inRegisterState, Object[] outRegisterState) {
        return (inRegisterState == null || inRegisterState[reg.number] == null) && (outRegisterState == null || outRegisterState[reg.number] == null);
    }

    private Location selectRegister(CiRegister reg, Variable variable, Object[] inRegisterState, Object[] outRegisterState) {
        Location loc = new Location(variable, reg.asValue(variable.kind));
        if (inRegisterState != null) {
            inRegisterState[reg.number] = loc;
        }
        if (outRegisterState != null) {
            outRegisterState[reg.number] = loc;
        }
        assert curRegisterLocations.get(variable) == null;
        curRegisterLocations.put(loc);
        Debug.log("      selected register %s", loc);
        return loc;
    }

    private boolean checkInputState(final Block block) {
        final BitSet liveState = new BitSet();
        curStackLocations.forEachLocation(new ValueProcedure() {
            @Override
            public RiValue doValue(RiValue value) {
                liveState.set(asLocation(value).variable.index);

                for (Block pred : block.getPredecessors()) {
                    LocationMap predState = locationsFor(pred);
                    if (predState != null) {
                        assert predState.get(asLocation(value).variable) == value;
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

    private boolean checkNoCallerSavedRegister() {
        for (CiRegister reg : frameMap.registerConfig.getCallerSaveRegisters()) {
            assert curOutRegisterState[reg.number] == null || curOutRegisterState[reg.number] == curInstruction : "caller saved register in use accross call site";
        }
        return true;
    }

    private static boolean checkEmpty(Object[] array) {
        for (Object o : array) {
            assert o == null;
        }
        return true;
    }


    private String logCurrentState() {
        final StringBuilder sb = new StringBuilder();
        sb.append("  curVariableLocations: ");
        curStackLocations.forEachLocation(new ValueProcedure() {
            @Override
            public RiValue doValue(RiValue value) {
                sb.append(value).append(" ");
                return value;
            }
        });
        return sb.toString();
    }
}
