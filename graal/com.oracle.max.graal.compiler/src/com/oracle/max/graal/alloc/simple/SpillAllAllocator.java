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

import static com.oracle.max.graal.compiler.lir.LIRPhiMapping.*;
import static com.oracle.max.cri.ci.CiValueUtil.*;
import static com.oracle.max.graal.alloc.util.ValueUtil.*;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ci.CiRegister.RegisterFlag;
import com.oracle.max.cri.ri.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.alloc.util.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.lir.LIRInstruction.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.*;

public class SpillAllAllocator {
    private final GraalContext context;
    private final LIR lir;
    private final FrameMap frameMap;
    private final RiRegisterConfig registerConfig;

    private final DataFlowAnalysis dataFlow;

    public SpillAllAllocator(GraalContext context, LIR lir, GraalCompilation compilation, RiRegisterConfig registerConfig) {
        this.context = context;
        this.lir = lir;
        this.registerConfig = registerConfig;
        this.frameMap = compilation.frameMap();

        this.dataFlow = new DataFlowAnalysis(context, lir, registerConfig);
        this.blockLocations = new LocationMap[lir.linearScanOrder().size()];
        this.moveResolver = new MoveResolverImpl(frameMap);
    }

    private class MoveResolverImpl extends MoveResolver {
        public MoveResolverImpl(FrameMap frameMap) {
            super(frameMap);
        }

        @Override
        protected CiValue scratchRegister(Variable spilled) {
            EnumMap<RegisterFlag, CiRegister[]> categorizedRegs = registerConfig.getCategorizedAllocatableRegisters();
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
        public ResolveDataFlowImpl(LIR lir, MoveResolver moveResolver) {
            super(lir, moveResolver);
        }

        @Override
        protected LocationMap locationsForBlockBegin(LIRBlock block) {
            assert block.numberOfPreds() > 0 && block.dominator() != null;
            return locationsFor(block.dominator());
        }

        @Override
        protected LocationMap locationsForBlockEnd(LIRBlock block) {
            return locationsFor(block);
        }
    }

    private class AssignRegistersImpl extends AssignRegisters {
        public AssignRegistersImpl(LIR lir, FrameMap frameMap) {
            super(lir, frameMap);
        }

        @Override
        protected LocationMap locationsForBlockEnd(LIRBlock block) {
            return locationsFor(block);
        }
    }


    private int maxRegisterNum() {
        return frameMap.target.arch.registers.length;
    }

    private boolean isAllocatableRegister(CiValue value) {
        return isRegister(value) && registerConfig.getAttributesMap()[asRegister(value).number].isAllocatable;
    }


    private final LocationMap[] blockLocations;

    private LocationMap locationsFor(Block block) {
        return blockLocations[block.blockID()];
    }
    private void setLocationsFor(Block block, LocationMap locations) {
        blockLocations[block.blockID()] = locations;
    }

    private MoveResolver moveResolver;
    private LocationMap curStackLocations;
    private LocationMap curRegisterLocations;
    private Object[] curInRegisterState;
    private Object[] curOutRegisterState;
    private BitSet curLiveIn;
    private LIRInstruction curInstruction;

    public void execute() {
        assert LIRVerifier.verify(true, lir, frameMap, registerConfig);

        dataFlow.execute();
        allocate();
        frameMap.finish();

        context.observable.fireCompilationEvent("After spill all allocation", lir);

        ResolveDataFlow resolveDataFlow = new ResolveDataFlowImpl(lir, moveResolver);
        resolveDataFlow.execute();

        context.observable.fireCompilationEvent("After resolve data flow", lir);
        assert RegisterVerifier.verify(lir, frameMap, registerConfig);

        AssignRegisters assignRegisters = new AssignRegistersImpl(lir, frameMap);
        assignRegisters.execute();

        context.observable.fireCompilationEvent("After register asignment", lir);
        assert LIRVerifier.verify(true, lir, frameMap, registerConfig);
    }

    private void allocate() {
        ValueProcedure killNonLiveProc =  new ValueProcedure() {    @Override public CiValue doValue(CiValue value) { return killNonLive(value); } };
        ValueProcedure killBeginProc =    new ValueProcedure() {    @Override public CiValue doValue(CiValue value) { return kill(value, false); } };
        ValueProcedure killEndProc =      new ValueProcedure() {    @Override public CiValue doValue(CiValue value) { return kill(value, true); } };
        ValueProcedure killLocationProc = new ValueProcedure() {    @Override public CiValue doValue(CiValue value) { return killLocation(value); } };
        ValueProcedure blockProc =        new ValueProcedure() {    @Override public CiValue doValue(CiValue value) { return block(value); } };
        ValueProcedure loadProc =         new ValueProcedure() {    @Override public CiValue doValue(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return load(value, mode, flags); } };
        ValueProcedure spillProc =        new ValueProcedure() {    @Override public CiValue doValue(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return spill(value, mode, flags); } };
        PhiValueProcedure useSlotProc =   new PhiValueProcedure() { @Override public CiValue doValue(CiValue value) { return useSlot(value); } };
        ValueProcedure defSlotProc =      new ValueProcedure() {    @Override public CiValue doValue(CiValue value) { return defSlot(value); } };

        assert trace("==== start spill all allocation ====");
        curInRegisterState = new Object[maxRegisterNum()];
        curOutRegisterState = new Object[maxRegisterNum()];
        curRegisterLocations = new LocationMap(lir.numVariables());
        for (LIRBlock block : lir.linearScanOrder()) {
            assert trace("start block %s  loop %d depth %d", block, block.loopIndex(), block.loopDepth());
            assert checkEmpty(curOutRegisterState);

            if (block.dominator() != null) {
                LocationMap dominatorState = locationsFor(block.dominator());
                curStackLocations = new LocationMap(dominatorState);
                // Clear out all variables that are not live at the begin of this block
                curLiveIn = dataFlow.liveIn(block);
                curStackLocations.forEachLocation(killNonLiveProc);
                assert checkInputState(block);
            } else {
                curStackLocations = new LocationMap(lir.numVariables());
            }
            assert traceState();

            if (block.phis != null) {
                assert trace("  phis");
                block.phis.forEachOutput(defSlotProc);
            }

            for (int opIdx = 0; opIdx < block.lir().size(); opIdx++) {
                LIRInstruction op = block.lir().get(opIdx);
                curInstruction = op;
                assert trace("  op %d %s", op.id(), op);

                assert curRegisterLocations.checkEmpty();

                System.arraycopy(curOutRegisterState, 0, curInRegisterState, 0, curOutRegisterState.length);

                // Block fixed registers that are defined by this instruction, so that they are no longer available for normal allocation.
                op.forEachTemp(blockProc);
                op.forEachOutput(blockProc);

                moveResolver.init(block.lir(), opIdx);
                // Process Alive before Input because they are more restricted and the same variable can be Alive and Input.
                op.forEachAlive(loadProc);
                op.forEachInput(loadProc);
                moveResolver.resolve();
                op.forEachState(useSlotProc);

                dataFlow.forEachKilled(op, false, killBeginProc);
                assert !op.hasCall() || checkNoCallerSavedRegister() : "caller saved register in use accross call site";

                moveResolver.init(block.lir(), opIdx + 1);
                op.forEachTemp(spillProc);
                op.forEachOutput(spillProc);
                moveResolver.resolve();

                dataFlow.forEachKilled(op, true, killEndProc);
                curRegisterLocations.forEachLocation(killLocationProc);

                assert curRegisterLocations.checkEmpty();
                curInstruction = null;
            }
            assert checkEmpty(curOutRegisterState);

            for (LIRBlock sux : block.getLIRSuccessors()) {
                if (sux.phis != null) {
                    assert trace("  phis of successor %s", sux);
                    sux.phis.forEachInput(block, useSlotProc);
                }
            }

            assert checkEmpty(curOutRegisterState);
            assert locationsFor(block) == null;
            setLocationsFor(block, curStackLocations);

            traceState();
            assert trace("end block %s", block);
        }

        moveResolver.finish();
        assert trace("==== end spill all allocation ====");
    }

    private CiValue killNonLive(CiValue value) {
        assert isLocation(value);
        if (!curLiveIn.get(asLocation(value).variable.index)) {
            return null;
        }
        return value;
    }

    private CiValue kill(CiValue value, boolean end) {
        if (isVariable(value)) {
            assert trace("    kill variable %s", value);

            Variable variable = asVariable(value);
            curStackLocations.clear(variable);

            Location loc = curRegisterLocations.get(variable);
            if (loc != null) {
                killLocation(loc);
                curRegisterLocations.clear(variable);

                assert trace("      location %s", loc);
                assert isAllocatableRegister(loc.location);

                int regNum = asRegister(loc.location).number;
                if (curOutRegisterState[regNum] == loc) {
                    curOutRegisterState[regNum] = null;
                }
            }

        } else if (isAllocatableRegister(value)) {
            assert trace("    kill register %s", value);
            int regNum = asRegister(value).number;
            assert curOutRegisterState[regNum] == null || curOutRegisterState[regNum] instanceof LIRInstruction && curInstruction != null;

            if (end || curOutRegisterState[regNum] != curInstruction) {
                curOutRegisterState[regNum] = null;
            }

        } else {
            throw Util.shouldNotReachHere();
        }
        return value;
    }

    private CiValue killLocation(CiValue value) {
        assert trace("    kill location %s", value);
        assert isAllocatableRegister(asLocation(value).location);

        int regNum = asRegister(asLocation(value).location).number;
        if (curOutRegisterState[regNum] == value) {
            curOutRegisterState[regNum] = null;
        }
        return null;
    }

    private CiValue block(CiValue value) {
        if (isAllocatableRegister(value)) {
            assert trace("    block %s", value);
            int regNum = asRegister(value).number;
            assert curInstruction != null;
            assert curOutRegisterState[regNum] == null || curOutRegisterState[regNum] instanceof LIRInstruction;
            curOutRegisterState[regNum] = curInstruction;
        }
        return value;
    }

    private CiValue load(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) {
        assert mode == OperandMode.Input || mode == OperandMode.Alive;
        if (flags.contains(OperandFlag.Stack)) {
            return useSlot(value);
        }
        if (isVariable(value)) {
            assert trace("    load %s", value);
            Location regLoc = curRegisterLocations.get(asVariable(value));
            if (regLoc != null) {
                // This variable has already been processed before.
                assert trace("      found location %s", regLoc);
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

    private CiValue spill(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) {
        assert mode == OperandMode.Temp || mode == OperandMode.Output;
        if (flags.contains(OperandFlag.Stack)) {
            return defSlot(value);
        }
        if (isVariable(value)) {
            assert trace("    spill %s", value);
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

    private CiValue useSlot(CiValue value) {
        if (isVariable(value)) {
            assert trace("    useSlot %s", value);
            Location stackLoc = curStackLocations.get(asVariable(value));
            assert stackLoc != null;
            assert trace("      slot %s", stackLoc);
            return stackLoc;
        } else {
            return value;
        }
    }

    private CiValue defSlot(CiValue value) {
        if (isVariable(value)) {
            assert trace("    assignSlot %s", value);
            Location stackLoc = new Location(asVariable(value), frameMap.allocateSpillSlot(value.kind));
            assert curStackLocations.get(asVariable(value)) == null;
            curStackLocations.put(stackLoc);
            assert trace("      slot %s", stackLoc);
            return stackLoc;
        } else {
            return value;
        }
    }

    private Location allocateRegister(final Variable variable, final Object[] inRegisterState, final Object[] outRegisterState, OperandMode mode, EnumSet<OperandFlag> flags) {
        if (flags.contains(OperandFlag.RegisterHint)) {
            CiValue result = curInstruction.forEachRegisterHint(variable, mode, new ValueProcedure() {
                @Override
                public CiValue doValue(CiValue registerHint) {
                    assert trace("      registerHint %s", registerHint);
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

        EnumMap<RegisterFlag, CiRegister[]> categorizedRegs = registerConfig.getCategorizedAllocatableRegisters();
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
        assert trace("      selected register %s", loc);
        return loc;
    }

    private boolean checkInputState(final LIRBlock block) {
        final BitSet liveState = new BitSet();
        curStackLocations.forEachLocation(new ValueProcedure() {
            @Override
            public CiValue doValue(CiValue value) {
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
        for (CiRegister reg : registerConfig.getCallerSaveRegisters()) {
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


    private boolean traceState() {
        if (GraalOptions.TraceRegisterAllocation) {
            TTY.print("  curVariableLocations: ");
            curStackLocations.forEachLocation(new ValueProcedure() {
                @Override
                public CiValue doValue(CiValue value) {
                    TTY.print("%s ", value);
                    return value;
                }
            });
            TTY.println();
        }
        return true;
    }

    private static boolean trace(String format, Object...args) {
        if (GraalOptions.TraceRegisterAllocation) {
            TTY.println(format, args);
        }
        return true;
    }
}
