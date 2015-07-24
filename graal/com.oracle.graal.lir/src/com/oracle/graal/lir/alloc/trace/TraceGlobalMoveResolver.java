/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.trace;

import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.alloc.lsra.*;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;

/**
 */
class TraceGlobalMoveResolver {

    private int insertIdx;
    private LIRInsertionBuffer insertionBuffer; // buffer where moves are inserted

    private final List<Value> mappingFrom;
    private final List<AllocatableValue> mappingTo;
    private final int[] registerBlocked;
    private static final int STACK_SLOT_IN_CALLER_FRAME_IDX = -1;
    private int[] stackBlocked;
    private final int firstVirtualStackIndex;
    private final SpillMoveFactory spillMoveFactory;
    private final FrameMapBuilder frameMapBuilder;

    private void setValueBlocked(Value location, int direction) {
        assert direction == 1 || direction == -1 : "out of bounds";
        if (isStackSlotValue(location)) {
            int stackIdx = getStackArrayIndex(asStackSlotValue(location));
            if (stackIdx == STACK_SLOT_IN_CALLER_FRAME_IDX) {
                // incoming stack arguments can be ignored
                return;
            }
            if (stackIdx >= stackBlocked.length) {
                stackBlocked = Arrays.copyOf(stackBlocked, stackIdx + 1);
            }
            stackBlocked[stackIdx] += direction;
        } else {
            assert direction == 1 || direction == -1 : "out of bounds";
            if (isRegister(location)) {
                registerBlocked[asRegister(location).number] += direction;
            } else {
                throw JVMCIError.shouldNotReachHere("unhandled value " + location);
            }
        }
    }

    private int valueBlocked(Value location) {
        if (isStackSlotValue(location)) {
            int stackIdx = getStackArrayIndex(asStackSlotValue(location));
            if (stackIdx == STACK_SLOT_IN_CALLER_FRAME_IDX) {
                // incoming stack arguments are always blocked (aka they can not be written)
                return 1;
            }
            if (stackIdx >= stackBlocked.length) {
                return 0;
            }
            return stackBlocked[stackIdx];
        }
        if (isRegister(location)) {
            return registerBlocked[asRegister(location).number];
        }
        throw JVMCIError.shouldNotReachHere("unhandled value " + location);
    }

    private static boolean areMultipleReadsAllowed() {
        return true;
    }

    private boolean hasMappings() {
        return mappingFrom.size() > 0;
    }

    private SpillMoveFactory getSpillMoveFactory() {
        return spillMoveFactory;
    }

    private Register[] getRegisters() {
        return frameMapBuilder.getRegisterConfig().getAllocatableRegisters();
    }

    public TraceGlobalMoveResolver(LIRGenerationResult res, SpillMoveFactory spillMoveFactory, Architecture arch) {

        this.mappingFrom = new ArrayList<>(8);
        this.mappingTo = new ArrayList<>(8);
        this.insertIdx = -1;
        this.insertionBuffer = new LIRInsertionBuffer();

        this.frameMapBuilder = res.getFrameMapBuilder();
        this.spillMoveFactory = spillMoveFactory;
        this.registerBlocked = new int[arch.getRegisters().length];

        FrameMapBuilderTool frameMapBuilderTool = (FrameMapBuilderTool) frameMapBuilder;
        this.stackBlocked = new int[frameMapBuilderTool.getNumberOfStackSlots()];

        FrameMap frameMap = frameMapBuilderTool.getFrameMap();
        this.firstVirtualStackIndex = !frameMap.frameNeedsAllocating() ? 0 : frameMap.currentFrameSize() + 1;
    }

    private boolean checkEmpty() {
        for (int i = 0; i < stackBlocked.length; i++) {
            assert stackBlocked[i] == 0 : "stack map must be empty before and after processing";
        }
        assert mappingFrom.size() == 0 && mappingTo.size() == 0 : "list must be empty before and after processing";
        for (int i = 0; i < getRegisters().length; i++) {
            assert registerBlocked[i] == 0 : "register map must be empty before and after processing";
        }
        return true;
    }

    private boolean verifyBeforeResolve() {
        assert mappingFrom.size() == mappingTo.size() : "length must be equal";
        assert insertIdx != -1 : "insert position not set";

        int i;
        int j;
        if (!areMultipleReadsAllowed()) {
            for (i = 0; i < mappingFrom.size(); i++) {
                for (j = i + 1; j < mappingFrom.size(); j++) {
                    assert mappingFrom.get(i) == null || mappingFrom.get(i) != mappingFrom.get(j) : "cannot read from same interval twice";
                }
            }
        }

        for (i = 0; i < mappingTo.size(); i++) {
            for (j = i + 1; j < mappingTo.size(); j++) {
                assert mappingTo.get(i) != mappingTo.get(j) : "cannot write to same interval twice";
            }
        }

        for (i = 0; i < mappingTo.size(); i++) {
            Value to = mappingTo.get(i);
            assert !isStackSlotValue(to) || getStackArrayIndex(asStackSlotValue(to)) != STACK_SLOT_IN_CALLER_FRAME_IDX : "Cannot move to in argument: " + to;
        }

        HashSet<Value> usedRegs = new HashSet<>();
        if (!areMultipleReadsAllowed()) {
            for (i = 0; i < mappingFrom.size(); i++) {
                Value from = mappingFrom.get(i);
                if (from != null && !isIllegal(from)) {
                    boolean unique = usedRegs.add(from);
                    assert unique : "cannot read from same register twice";
                }
            }
        }

        usedRegs.clear();
        for (i = 0; i < mappingTo.size(); i++) {
            Value to = mappingTo.get(i);
            if (isIllegal(to)) {
                // After insertion the location may become illegal, so don't check it since multiple
                // intervals might be illegal.
                continue;
            }
            boolean unique = usedRegs.add(to);
            assert unique : "cannot write to same register twice";
        }

        return true;
    }

    // mark assignedReg and assignedRegHi of the interval as blocked
    private void block(Value location) {
        if (mightBeBlocked(location)) {
            assert areMultipleReadsAllowed() || valueBlocked(location) == 0 : "location already marked as used: " + location;
            setValueBlocked(location, 1);
            Debug.log("block %s", location);
        }
    }

    // mark assignedReg and assignedRegHi of the interval as unblocked
    private void unblock(Value location) {
        if (mightBeBlocked(location)) {
            assert valueBlocked(location) > 0 : "location already marked as unused: " + location;
            setValueBlocked(location, -1);
            Debug.log("unblock %s", location);
        }
    }

    /**
     * Checks if the {@linkplain Interval#location() location} of {@code to} is not blocked or is
     * only blocked by {@code from}.
     */
    private boolean safeToProcessMove(Value fromLocation, Value toLocation) {
        if (mightBeBlocked(toLocation)) {
            if ((valueBlocked(toLocation) > 1 || (valueBlocked(toLocation) == 1 && !isMoveToSelf(fromLocation, toLocation)))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isMoveToSelf(Value from, Value to) {
        assert to != null;
        if (to.equals(from)) {
            return true;
        }
        if (from != null && isRegister(from) && isRegister(to) && asRegister(from).equals(asRegister(to))) {
            assert LIRKind.verifyMoveKinds(to.getLIRKind(), from.getLIRKind()) : String.format("Same register but Kind mismatch %s <- %s", to, from);
            return true;
        }
        return false;
    }

    private static boolean mightBeBlocked(Value location) {
        return isRegister(location) || isStackSlotValue(location);
    }

    private void createInsertionBuffer(List<LIRInstruction> list) {
        assert !insertionBuffer.initialized() : "overwriting existing buffer";
        insertionBuffer.init(list);
    }

    private void appendInsertionBuffer() {
        if (insertionBuffer.initialized()) {
            insertionBuffer.finish();
        }
        assert !insertionBuffer.initialized() : "must be uninitialized now";

        insertIdx = -1;
    }

    private void insertMove(Value fromOperand, AllocatableValue toOperand) {
        assert !fromOperand.equals(toOperand) : "from and to are equal: " + fromOperand + " vs. " + toOperand;
        assert LIRKind.verifyMoveKinds(fromOperand.getLIRKind(), fromOperand.getLIRKind()) : "move between different types";
        assert insertIdx != -1 : "must setup insert position first";

        insertionBuffer.append(insertIdx, createMove(fromOperand, toOperand));

        if (Debug.isLogEnabled()) {
            Debug.log("insert move from %s to %s at %d", fromOperand, toOperand, insertIdx);
        }
    }

    /**
     * @param fromOpr {@link Interval#operand operand} of the {@code from} interval
     * @param toOpr {@link Interval#operand operand} of the {@code to} interval
     */
    private LIRInstruction createMove(Value fromOpr, AllocatableValue toOpr) {
        if (isStackSlotValue(toOpr) && isStackSlotValue(fromOpr)) {
            return getSpillMoveFactory().createStackMove(toOpr, fromOpr);
        }
        return getSpillMoveFactory().createMove(toOpr, fromOpr);
    }

    private void resolveMappings() {
        try (Indent indent = Debug.logAndIndent("resolveMapping")) {
            assert verifyBeforeResolve();
            if (Debug.isLogEnabled()) {
                printMapping();
            }

            // Block all registers that are used as input operands of a move.
            // When a register is blocked, no move to this register is emitted.
            // This is necessary for detecting cycles in moves.
            for (int i = mappingFrom.size() - 1; i >= 0; i--) {
                Value from = mappingFrom.get(i);
                block(from);
            }

            int spillCandidate = -1;
            while (mappingFrom.size() > 0) {
                boolean processedInterval = false;

                for (int i = mappingFrom.size() - 1; i >= 0; i--) {
                    Value fromInterval = mappingFrom.get(i);
                    AllocatableValue toInterval = mappingTo.get(i);

                    Value fromLocation = fromInterval;
                    AllocatableValue toLocation = toInterval;
                    if (safeToProcessMove(fromLocation, toLocation)) {
                        // this interval can be processed because target is free
                        insertMove(fromLocation, toLocation);
                        unblock(fromLocation);
                        mappingFrom.remove(i);
                        mappingTo.remove(i);

                        processedInterval = true;
                    } else if (fromInterval != null && isRegister(fromLocation)) {
                        // this interval cannot be processed now because target is not free
                        // it starts in a register, so it is a possible candidate for spilling
                        spillCandidate = i;
                    }
                }

                if (!processedInterval) {
                    breakCycle(spillCandidate);
                }
            }
        }

        // check that all intervals have been processed
        assert checkEmpty();
    }

    private void breakCycle(int spillCandidate) {
        // no move could be processed because there is a cycle in the move list
        // (e.g. r1 . r2, r2 . r1), so one interval must be spilled to memory
        assert spillCandidate != -1 : "no interval in register for spilling found";

        // create a new spill interval and assign a stack slot to it
        Value from = mappingFrom.get(spillCandidate);
        try (Indent indent = Debug.logAndIndent("BreakCycle: %s", from)) {
            StackSlotValue spillSlot = frameMapBuilder.allocateSpillSlot(from.getLIRKind());
            if (Debug.isLogEnabled()) {
                Debug.log("created new slot for spilling: %s", spillSlot);
            }
            // insert a move from register to stack and update the mapping
            insertMove(from, spillSlot);
            block(spillSlot);
            mappingFrom.set(spillCandidate, spillSlot);
            unblock(from);
        }
    }

    private void printMapping() {
        try (Indent indent = Debug.logAndIndent("Mapping")) {
            for (int i = mappingFrom.size() - 1; i >= 0; i--) {
                Debug.log("move %s <- %s", mappingTo.get(i), mappingFrom.get(i));
            }
        }
    }

    public void setInsertPosition(List<LIRInstruction> insertList, int insertIdx) {
        assert this.insertIdx == -1 : "use moveInsertPosition instead of setInsertPosition when data already set";

        createInsertionBuffer(insertList);
        this.insertIdx = insertIdx;
    }

    public void addMapping(Value from, AllocatableValue to) {
        if (Debug.isLogEnabled()) {
            Debug.log("add move mapping from %s to %s", from, to);
        }

        assert !from.equals(to) : "from and to interval equal: " + from;
        assert LIRKind.verifyMoveKinds(to.getLIRKind(), from.getLIRKind()) : String.format("Kind mismatch: %s vs. %s, from=%s, to=%s", from.getLIRKind(), to.getLIRKind(), from, to);
        mappingFrom.add(from);
        mappingTo.add(to);
    }

    public void resolveAndAppendMoves() {
        if (hasMappings()) {
            resolveMappings();
        }
        appendInsertionBuffer();
    }

    private int getStackArrayIndex(StackSlotValue stackSlotValue) {
        if (isStackSlot(stackSlotValue)) {
            return getStackArrayIndex(asStackSlot(stackSlotValue));
        }
        if (isVirtualStackSlot(stackSlotValue)) {
            return getStackArrayIndex(asVirtualStackSlot(stackSlotValue));
        }
        throw JVMCIError.shouldNotReachHere("Unhandled StackSlotValue: " + stackSlotValue);
    }

    private int getStackArrayIndex(StackSlot stackSlot) {
        int stackIdx;
        if (stackSlot.isInCallerFrame()) {
            // incoming stack arguments can be ignored
            stackIdx = STACK_SLOT_IN_CALLER_FRAME_IDX;
        } else {
            assert stackSlot.getRawAddFrameSize() : "Unexpected stack slot: " + stackSlot;
            int offset = -stackSlot.getRawOffset();
            assert 0 <= offset && offset < firstVirtualStackIndex : String.format("Wrong stack slot offset: %d (first virtual stack slot index: %d", offset, firstVirtualStackIndex);
            stackIdx = offset;
        }
        return stackIdx;
    }

    private int getStackArrayIndex(VirtualStackSlot virtualStackSlot) {
        return firstVirtualStackIndex + virtualStackSlot.getId();
    }

}
