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

import static com.oracle.max.cri.ci.CiValueUtil.*;
import static com.oracle.max.graal.alloc.util.ValueUtil.*;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ci.CiRegister.RegisterFlag;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.alloc.util.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.lir.LIRInstruction.OperandFlag;
import com.oracle.max.graal.compiler.lir.LIRInstruction.OperandMode;
import com.oracle.max.graal.compiler.lir.LIRInstruction.ValueProcedure;
import com.oracle.max.graal.compiler.lir.LIRPhiMapping.PhiValueProcedure;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.*;

public class LinearScanAllocator {
    private final GraalContext context;
    private final LIR lir;
    private final FrameMap frameMap;

    private final DataFlowAnalysis dataFlow;

    public LinearScanAllocator(GraalContext context, LIR lir, FrameMap frameMap) {
        this.context = context;
        this.lir = lir;
        this.frameMap = frameMap;

        this.dataFlow = new DataFlowAnalysis(context, lir, frameMap.registerConfig);
        this.blockBeginLocations = new LocationMap[lir.linearScanOrder().size()];
        this.blockEndLocations = new LocationMap[lir.linearScanOrder().size()];
        this.moveResolver = new MoveResolverImpl(frameMap);

        this.variableLastUse = new int[lir.numVariables()];
    }

    private class MoveResolverImpl extends MoveResolver {
        public MoveResolverImpl(FrameMap frameMap) {
            super(frameMap);
        }

        @Override
        protected CiValue scratchRegister(Variable spilled) {
            Util.shouldNotReachHere("needs working implementation");

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
        protected LocationMap locationsForBlockBegin(LIRBlock block) {
            return beginLocationsFor(block);
        }

        @Override
        protected LocationMap locationsForBlockEnd(LIRBlock block) {
            return endLocationsFor(block);
        }
    }

    private class AssignRegistersImpl extends AssignRegisters {
        public AssignRegistersImpl(LIR lir, FrameMap frameMap) {
            super(lir, frameMap);
        }

        @Override
        protected LocationMap locationsForBlockEnd(LIRBlock block) {
            return endLocationsFor(block);
        }
    }


    private int maxRegisterNum() {
        return frameMap.target.arch.registers.length;
    }

    private boolean isAllocatableRegister(CiValue value) {
        return isRegister(value) && frameMap.registerConfig.getAttributesMap()[asRegister(value).number].isAllocatable;
    }


    private final LocationMap[] blockBeginLocations;

    private LocationMap beginLocationsFor(Block block) {
        return blockBeginLocations[block.blockID()];
    }
    private void setBeginLocationsFor(Block block, LocationMap locations) {
        blockBeginLocations[block.blockID()] = locations;
    }

    private final LocationMap[] blockEndLocations;

    private LocationMap endLocationsFor(Block block) {
        return blockEndLocations[block.blockID()];
    }
    private void setEndLocationsFor(Block block, LocationMap locations) {
        blockEndLocations[block.blockID()] = locations;
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
    private CiValue[] curInRegisterState;
    private CiValue[] curOutRegisterState;
    private BitSet curLiveIn;
    private int curOpId;
    private LIRBlock curPhiBlock;

    private LocationMap canonicalSpillLocations;

    public void execute() {
        assert LIRVerifier.verify(true, lir, frameMap);

        dataFlow.execute();
        allocate();

        context.observable.fireCompilationEvent("After linear scan allocation", lir);

        ResolveDataFlow resolveDataFlow = new ResolveDataFlowImpl(lir, moveResolver, dataFlow);
        resolveDataFlow.execute();

        frameMap.finish();

        context.observable.fireCompilationEvent("After resolve data flow", lir);
        assert RegisterVerifier.verify(lir, frameMap);

        AssignRegisters assignRegisters = new AssignRegistersImpl(lir, frameMap);
        assignRegisters.execute();

        context.observable.fireCompilationEvent("After register asignment", lir);
        assert LIRVerifier.verify(false, lir, frameMap);
    }

    private void allocate() {
        ValueProcedure recordUseProc =    new ValueProcedure() {    @Override public CiValue doValue(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return recordUse(value, mode); } };
        ValueProcedure killNonLiveProc =  new ValueProcedure() {    @Override public CiValue doValue(CiValue value) { return killNonLive(value); } };
        ValueProcedure unblockProc =      new ValueProcedure() {    @Override public CiValue doValue(CiValue value) { return unblock(value); } };
        ValueProcedure killProc =      new ValueProcedure() {    @Override public CiValue doValue(CiValue value) { return kill(value); } };
        ValueProcedure blockProc =        new ValueProcedure() {    @Override public CiValue doValue(CiValue value) { return block(value); } };
        PhiValueProcedure useProc =          new PhiValueProcedure() {    @Override public CiValue doValue(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return use(value, mode, flags); } };
        ValueProcedure defProc =          new ValueProcedure() {    @Override public CiValue doValue(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return def(value, mode, flags); } };

        assert trace("==== start linear scan allocation ====");
        canonicalSpillLocations = new LocationMap(lir.numVariables());
        curInRegisterState = new CiValue[maxRegisterNum()];
        curOutRegisterState = new CiValue[maxRegisterNum()];
        for (LIRBlock block : lir.linearScanOrder()) {
            assert trace("start block %s  loop %d depth %d", block, block.loopIndex(), block.loopDepth());

            Arrays.fill(curOutRegisterState, null);
            if (block.dominator() != null) {
                LocationMap dominatorState = endLocationsFor(block.dominator());
                curLocations = new LocationMap(dominatorState);
                // Clear out all variables that are not live at the begin of this block
                curLiveIn = dataFlow.liveIn(block);
                curLocations.forEachLocation(killNonLiveProc);
                assert checkInputState(block);
            } else {
                curLocations = new LocationMap(lir.numVariables());
            }
            assert traceState();

            if (block.phis != null) {
                assert trace("  phis");
                curPhiBlock = block;
                curOpId = block.firstLirInstructionId();
                block.phis.forEachOutput(defProc);
                curOpId = -1;
                curPhiBlock = null;
            }

            setBeginLocationsFor(block, new LocationMap(curLocations));

            for (int opIdx = 0; opIdx < block.lir().size(); opIdx++) {
                LIRInstruction op = block.lir().get(opIdx);
                curOpId = op.id();
                assert trace("  op %d %s", op.id(), op);

                System.arraycopy(curOutRegisterState, 0, curInRegisterState, 0, curOutRegisterState.length);

                // Unblock fixed registers that are only used for inputs in curOutRegisterState.
                dataFlow.forEachKilled(op, false, unblockProc);
                // Block fixed registers defined by this instruction in curOutRegisterState.
                op.forEachTemp(blockProc);
                op.forEachOutput(blockProc);

                op.forEachInput(recordUseProc);
                op.forEachAlive(recordUseProc);

                moveResolver.init(block.lir(), opIdx);
                // Process Alive before Input because they are more restricted and the same variable can be Alive and Input.
                op.forEachAlive(useProc);
                op.forEachInput(useProc);

                dataFlow.forEachKilled(op, false, killProc);

                if (op.hasCall()) {
                    spillCallerSaveRegisters();
                }

                op.forEachTemp(defProc);
                op.forEachOutput(defProc);

                // Fixed temp and output registers can evict variables from their assigned register, allocate new location for them.
                fixupEvicted();
                // State values are the least critical and can get the leftover registers (or stack slots if no more register available).
                op.forEachState(useProc);


                moveResolver.resolve();

                dataFlow.forEachKilled(op, true, unblockProc);
                dataFlow.forEachKilled(op, true, killProc);

//                curInstruction = null;
                curOpId = -1;
            }

            for (LIRBlock sux : block.getLIRSuccessors()) {
                if (sux.phis != null) {
                    assert trace("  phis of successor %s", sux);
                    System.arraycopy(curOutRegisterState, 0, curInRegisterState, 0, curOutRegisterState.length);
                    curOpId = block.lastLirInstructionId() + 1;
                    sux.phis.forEachInput(block, useProc);
                    curOpId = -1;
                }
            }

            assert endLocationsFor(block) == null;
            setEndLocationsFor(block, curLocations);

            traceState();
            assert trace("end block %s", block);
        }

        moveResolver.finish();
        assert trace("==== end linear scan allocation ====");
    }

    private CiValue killNonLive(CiValue value) {
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

    private CiValue unblock(CiValue value) {
        if (isAllocatableRegister(value)) {
            assert trace("    unblock register %s", value);
            int regNum = asRegister(value).number;
            assert curOutRegisterState[regNum] == value;
            curOutRegisterState[regNum] = null;
        }
        return value;
    }

    private CiValue kill(CiValue value) {
        if (isVariable(value)) {
            Location location = curLocations.get(asVariable(value));
            assert trace("    kill location %s", location);
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


    private CiValue block(CiValue value) {
        if (isAllocatableRegister(value)) {
            assert trace("    block %s", value);
            int regNum = asRegister(value).number;
            assert curOutRegisterState[regNum] == null || curOutRegisterState[regNum] instanceof Location;
            curOutRegisterState[regNum] = value;
        }
        return value;
    }

    private void spillCallerSaveRegisters() {
        assert trace("    spill caller save registers in curInRegisterState %s", Arrays.toString(curInRegisterState));
        for (CiRegister reg : frameMap.registerConfig.getCallerSaveRegisters()) {
            CiValue in = curInRegisterState[reg.number];
            if (in != null && isLocation(in)) {
                spill(asLocation(in));
            }
        }
    }

    private CiValue recordUse(CiValue value, OperandMode mode) {
        if (isVariable(value)) {
            int id = mode == OperandMode.Input ? curOpId : curOpId + 1;
            assert lastUseFor(asVariable(value)) <= id;
            setLastUseFor(asVariable(value), id);

        }
        return value;
    }

    private CiValue use(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) {
        assert mode == OperandMode.Input || mode == OperandMode.Alive;
        if (isVariable(value)) {
            // State values are not recorded beforehand because it does not matter if they are spilled. Still, it is necessary to record them as used now.
            recordUse(value, mode);

            Location curLoc = curLocations.get(asVariable(value));
            if (isStackSlot(curLoc.location) && flags.contains(OperandFlag.Stack)) {
                assert trace("    use %s %s: use current stack slot %s", mode, value, curLoc.location);
                return curLoc;
            }
            if (isRegister(curLoc.location)) {
                int regNum = asRegister(curLoc.location).number;
                assert curInRegisterState[regNum] == curLoc;
                if (mode == OperandMode.Input || curOutRegisterState[regNum] == curLoc) {
                    assert trace("    use %s %s: use current register %s", mode, value, curLoc.location);
                    return curLoc;
                }
            }

            assert trace("    use %s %s", mode, value);

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

    private CiValue def(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) {
        assert mode == OperandMode.Temp || mode == OperandMode.Output;
        if (isVariable(value)) {
            assert trace("    def %s %s", mode, value);
            assert curLocations.get(asVariable(value)) == null;

            Location newLoc = allocateRegister(asVariable(value), mode, flags);
            return newLoc;
        }
        return value;
    }


    private void fixupEvicted() {
        for (int i = 0; i < curInRegisterState.length; i++) {
            CiValue in = curInRegisterState[i];
            CiValue out = curOutRegisterState[i];

            if (in != null && in != out && isLocation(in) && curLocations.get(asLocation(in).variable) == in) {
                assert trace("    %s was evicted by %s, need to allocate new location", in, out);
                Location oldLoc = asLocation(in);
                Location newLoc = allocateRegister(oldLoc.variable, OperandMode.Alive, SPILL_FLAGS);
                assert oldLoc != newLoc;
                moveResolver.add(oldLoc, newLoc);
            }


        }
    }


    private Location allocateRegister(final Variable variable, OperandMode mode, EnumSet<OperandFlag> flags) {
//        if (flags.contains(OperandFlag.RegisterHint)) {
//            CiValue result = curInstruction.forEachRegisterHint(variable, mode, new ValueProcedure() {
//                @Override
//                public CiValue doValue(CiValue registerHint) {
//                    assert trace("      registerHint %s", registerHint);
//                    CiRegister hint = null;
//                    if (isRegister(registerHint)) {
//                        hint = asRegister(registerHint);
//                    } else if (isLocation(registerHint) && isRegister(asLocation(registerHint).location)) {
//                        hint = asRegister(asLocation(registerHint).location);
//                    }
//                    if (hint != null && hint.isSet(variable.flag) && isFree(hint, inRegisterState, outRegisterState)) {
//                        return selectRegister(hint, variable, inRegisterState, outRegisterState);
//                    }
//                    return null;
//                }
//            });
//
//            if (result != null) {
//                return asLocation(result);
//            }
//        }
//
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
            return selectSpillSlot(variable, mode);
        }

        if (bestSpillCandidate == null) {
            // This should not happen as long as all LIR instructions have fulfillable register constraints. But be safe in product mode and bail out.
            assert false;
            throw new CiBailout("No register available");
        }

        spill(bestSpillCandidate);

        return selectRegister(asRegister(bestSpillCandidate.location), variable, mode);
    }

    private void spill(Location value) {
        Location newLoc = spillLocation(value.variable);
        assert trace("      spill %s to %s", value, newLoc);
        if (curPhiBlock == null) {
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
            default:     throw Util.shouldNotReachHere();
        }
    }

    private Location spillCandidate(CiRegister reg) {
        CiValue in = curInRegisterState[reg.number];
        CiValue out = curOutRegisterState[reg.number];
        if (in == out && in != null && isLocation(in) && lastUseFor(asLocation(in).variable) < curOpId) {
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
        recordUse(variable, mode);

        assert trace("      selected register %s", loc);
        return loc;
    }

    private Location selectSpillSlot(Variable variable, OperandMode mode) {
        Location loc = spillLocation(variable);
        curLocations.put(loc);
        recordUse(variable, mode);

        assert trace("      selected spill slot %s", loc);
        return loc;
    }

    private boolean checkInputState(final LIRBlock block) {
        final BitSet liveState = new BitSet();
        curLocations.forEachLocation(new ValueProcedure() {
            @Override
            public CiValue doValue(CiValue value) {
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


    private boolean traceState() {
        if (GraalOptions.TraceRegisterAllocation) {
            TTY.print("  current lcoations: ");
            curLocations.forEachLocation(new ValueProcedure() {
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
