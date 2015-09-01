/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.lir.framemap.*;

/**
 */
public class MoveResolver {

    private static final int STACK_SLOT_IN_CALLER_FRAME_IDX = -1;
    private final TraceLinearScan allocator;

    private int insertIdx;
    private LIRInsertionBuffer insertionBuffer; // buffer where moves are inserted

    private final List<Interval> mappingFrom;
    private final List<Constant> mappingFromOpr;
    private final List<Interval> mappingTo;
    private final int[] registerBlocked;

    private int[] stackBlocked;
    private final int firstVirtualStackIndex;

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

    protected void setValueBlocked(Value location, int direction) {
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

    protected Interval getMappingFrom(int i) {
        return mappingFrom.get(i);
    }

    protected int mappingFromSize() {
        return mappingFrom.size();
    }

    protected int valueBlocked(Value location) {
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

    protected boolean areMultipleReadsAllowed() {
        return true;
    }

    boolean hasMappings() {
        return mappingFrom.size() > 0;
    }

    protected TraceLinearScan getAllocator() {
        return allocator;
    }

    protected MoveResolver(TraceLinearScan allocator) {

        this.allocator = allocator;
        this.mappingFrom = new ArrayList<>(8);
        this.mappingFromOpr = new ArrayList<>(8);
        this.mappingTo = new ArrayList<>(8);
        this.insertIdx = -1;
        this.insertionBuffer = new LIRInsertionBuffer();
        this.registerBlocked = new int[allocator.getRegisters().length];
        FrameMapBuilderTool frameMapBuilderTool = (FrameMapBuilderTool) allocator.getFrameMapBuilder();
        FrameMap frameMap = frameMapBuilderTool.getFrameMap();
        this.stackBlocked = new int[frameMapBuilderTool.getNumberOfStackSlots()];
        this.firstVirtualStackIndex = !frameMap.frameNeedsAllocating() ? 0 : frameMap.currentFrameSize() + 1;
    }

    protected boolean checkEmpty() {
        assert mappingFrom.size() == 0 && mappingFromOpr.size() == 0 && mappingTo.size() == 0 : "list must be empty before and after processing";
        for (int i = 0; i < stackBlocked.length; i++) {
            assert stackBlocked[i] == 0 : "stack map must be empty before and after processing";
        }
        for (int i = 0; i < getAllocator().getRegisters().length; i++) {
            assert registerBlocked[i] == 0 : "register map must be empty before and after processing";
        }
        checkMultipleReads();
        return true;
    }

    protected void checkMultipleReads() {
        // multiple reads are allowed in SSA LSRA
    }

    private boolean verifyBeforeResolve() {
        assert mappingFrom.size() == mappingFromOpr.size() : "length must be equal";
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

        HashSet<Value> usedRegs = new HashSet<>();
        if (!areMultipleReadsAllowed()) {
            for (i = 0; i < mappingFrom.size(); i++) {
                Interval interval = mappingFrom.get(i);
                if (interval != null && !isIllegal(interval.location())) {
                    boolean unique = usedRegs.add(interval.location());
                    assert unique : "cannot read from same register twice";
                }
            }
        }

        usedRegs.clear();
        for (i = 0; i < mappingTo.size(); i++) {
            Interval interval = mappingTo.get(i);
            if (isIllegal(interval.location())) {
                // After insertion the location may become illegal, so don't check it since multiple
                // intervals might be illegal.
                continue;
            }
            boolean unique = usedRegs.add(interval.location());
            assert unique : "cannot write to same register twice";
        }

        verifyStackSlotMapping();

        return true;
    }

    protected void verifyStackSlotMapping() {
        // relax disjoint stack maps invariant
    }

    // mark assignedReg and assignedRegHi of the interval as blocked
    private void blockRegisters(Interval interval) {
        Value location = interval.location();
        if (mightBeBlocked(location)) {
            assert areMultipleReadsAllowed() || valueBlocked(location) == 0 : "location already marked as used: " + location;
            int direction = 1;
            setValueBlocked(location, direction);
            Debug.log("block %s", location);
        }
    }

    // mark assignedReg and assignedRegHi of the interval as unblocked
    private void unblockRegisters(Interval interval) {
        Value location = interval.location();
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
    private boolean safeToProcessMove(Interval from, Interval to) {
        Value fromReg = from != null ? from.location() : null;

        Value location = to.location();
        if (mightBeBlocked(location)) {
            if ((valueBlocked(location) > 1 || (valueBlocked(location) == 1 && !isMoveToSelf(fromReg, location)))) {
                return false;
            }
        }

        return true;
    }

    protected boolean isMoveToSelf(Value from, Value to) {
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

    protected boolean mightBeBlocked(Value location) {
        if (isRegister(location)) {
            return true;
        }
        if (isStackSlotValue(location)) {
            return true;
        }
        return false;
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

    private void insertMove(Interval fromInterval, Interval toInterval) {
        assert !fromInterval.operand.equals(toInterval.operand) : "from and to interval equal: " + fromInterval;
        assert LIRKind.verifyMoveKinds(toInterval.kind(), fromInterval.kind()) : "move between different types";
        assert insertIdx != -1 : "must setup insert position first";

        insertionBuffer.append(insertIdx, createMove(fromInterval.operand, toInterval.operand, fromInterval.location(), toInterval.location()));

        if (Debug.isLogEnabled()) {
            Debug.log("insert move from %s to %s at %d", fromInterval, toInterval, insertIdx);
        }
    }

    /**
     * @param fromOpr {@link Interval#operand operand} of the {@code from} interval
     * @param toOpr {@link Interval#operand operand} of the {@code to} interval
     * @param fromLocation {@link Interval#location() location} of the {@code to} interval
     * @param toLocation {@link Interval#location() location} of the {@code to} interval
     */
    protected LIRInstruction createMove(AllocatableValue fromOpr, AllocatableValue toOpr, AllocatableValue fromLocation, AllocatableValue toLocation) {
        if (isStackSlotValue(toLocation) && isStackSlotValue(fromLocation)) {
            return getAllocator().getSpillMoveFactory().createStackMove(toOpr, fromOpr);
        }
        return getAllocator().getSpillMoveFactory().createMove(toOpr, fromOpr);
    }

    private void insertMove(Constant fromOpr, Interval toInterval) {
        assert insertIdx != -1 : "must setup insert position first";

        AllocatableValue toOpr = toInterval.operand;
        LIRInstruction move = getAllocator().getSpillMoveFactory().createLoad(toOpr, fromOpr);
        insertionBuffer.append(insertIdx, move);

        if (Debug.isLogEnabled()) {
            Debug.log("insert move from value %s to %s at %d", fromOpr, toInterval, insertIdx);
        }
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
            int i;
            for (i = mappingFrom.size() - 1; i >= 0; i--) {
                Interval fromInterval = mappingFrom.get(i);
                if (fromInterval != null) {
                    blockRegisters(fromInterval);
                }
            }

            int spillCandidate = -1;
            while (mappingFrom.size() > 0) {
                boolean processedInterval = false;

                for (i = mappingFrom.size() - 1; i >= 0; i--) {
                    Interval fromInterval = mappingFrom.get(i);
                    Interval toInterval = mappingTo.get(i);

                    if (safeToProcessMove(fromInterval, toInterval)) {
                        // this interval can be processed because target is free
                        if (fromInterval != null) {
                            insertMove(fromInterval, toInterval);
                            unblockRegisters(fromInterval);
                        } else {
                            insertMove(mappingFromOpr.get(i), toInterval);
                        }
                        mappingFrom.remove(i);
                        mappingFromOpr.remove(i);
                        mappingTo.remove(i);

                        processedInterval = true;
                    } else if (fromInterval != null && isRegister(fromInterval.location())) {
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

    protected void breakCycle(int spillCandidate) {
        if (spillCandidate != -1) {
            // no move could be processed because there is a cycle in the move list
            // (e.g. r1 . r2, r2 . r1), so one interval must be spilled to memory
            assert spillCandidate != -1 : "no interval in register for spilling found";

            // create a new spill interval and assign a stack slot to it
            Interval fromInterval1 = mappingFrom.get(spillCandidate);
            // do not allocate a new spill slot for temporary interval, but
            // use spill slot assigned to fromInterval. Otherwise moves from
            // one stack slot to another can happen (not allowed by LIRAssembler
            StackSlotValue spillSlot1 = fromInterval1.spillSlot();
            if (spillSlot1 == null) {
                spillSlot1 = getAllocator().getFrameMapBuilder().allocateSpillSlot(fromInterval1.kind());
                fromInterval1.setSpillSlot(spillSlot1);
            }
            spillInterval(spillCandidate, fromInterval1, spillSlot1);
            return;
        }
        assert mappingFromSize() > 1;
        // Arbitrarily select the first entry for spilling.
        int stackSpillCandidate = 0;
        Interval fromInterval = getMappingFrom(stackSpillCandidate);
        assert isStackSlotValue(fromInterval.location());
        // allocate new stack slot
        StackSlotValue spillSlot = getAllocator().getFrameMapBuilder().allocateSpillSlot(fromInterval.kind());
        spillInterval(stackSpillCandidate, fromInterval, spillSlot);
    }

    protected void spillInterval(int spillCandidate, Interval fromInterval, StackSlotValue spillSlot) {
        assert mappingFrom.get(spillCandidate).equals(fromInterval);
        Interval spillInterval = getAllocator().createDerivedInterval(fromInterval);
        spillInterval.setKind(fromInterval.kind());

        // add a dummy range because real position is difficult to calculate
        // Note: this range is a special case when the integrity of the allocation is
        // checked
        spillInterval.addRange(1, 2);

        spillInterval.assignLocation(spillSlot);

        if (Debug.isLogEnabled()) {
            Debug.log("created new Interval for spilling: %s", spillInterval);
        }
        blockRegisters(spillInterval);

        // insert a move from register to stack and update the mapping
        insertMove(fromInterval, spillInterval);
        mappingFrom.set(spillCandidate, spillInterval);
        unblockRegisters(fromInterval);
    }

    private void printMapping() {
        try (Indent indent = Debug.logAndIndent("Mapping")) {
            for (int i = mappingFrom.size() - 1; i >= 0; i--) {
                Interval fromInterval = mappingFrom.get(i);
                Interval toInterval = mappingTo.get(i);
                String from;
                Value to = toInterval.location();
                if (fromInterval == null) {
                    from = mappingFromOpr.get(i).toString();
                } else {
                    from = fromInterval.location().toString();
                }
                Debug.log("move %s <- %s", from, to);
            }
        }
    }

    void setInsertPosition(List<LIRInstruction> insertList, int insertIdx) {
        assert this.insertIdx == -1 : "use moveInsertPosition instead of setInsertPosition when data already set";

        createInsertionBuffer(insertList);
        this.insertIdx = insertIdx;
    }

    void moveInsertPosition(List<LIRInstruction> newInsertList, int newInsertIdx) {
        if (insertionBuffer.lirList() != null && (insertionBuffer.lirList() != newInsertList || this.insertIdx != newInsertIdx)) {
            // insert position changed . resolve current mappings
            resolveMappings();
        }

        if (insertionBuffer.lirList() != newInsertList) {
            // block changed . append insertionBuffer because it is
            // bound to a specific block and create a new insertionBuffer
            appendInsertionBuffer();
            createInsertionBuffer(newInsertList);
        }

        this.insertIdx = newInsertIdx;
    }

    public void addMapping(Interval fromInterval, Interval toInterval) {

        if (isIllegal(toInterval.location()) && toInterval.canMaterialize()) {
            if (Debug.isLogEnabled()) {
                Debug.log("no store to rematerializable interval %s needed", toInterval);
            }
            return;
        }
        if (isIllegal(fromInterval.location()) && fromInterval.canMaterialize()) {
            // Instead of a reload, re-materialize the value
            JavaConstant rematValue = fromInterval.getMaterializedValue();
            addMapping(rematValue, toInterval);
            return;
        }
        if (Debug.isLogEnabled()) {
            Debug.log("add move mapping from %s to %s", fromInterval, toInterval);
        }

        assert !fromInterval.operand.equals(toInterval.operand) : "from and to interval equal: " + fromInterval;
        assert LIRKind.verifyMoveKinds(toInterval.kind(), fromInterval.kind()) : String.format("Kind mismatch: %s vs. %s, from=%s, to=%s", fromInterval.kind(), toInterval.kind(), fromInterval,
                        toInterval);
        mappingFrom.add(fromInterval);
        mappingFromOpr.add(null);
        mappingTo.add(toInterval);
    }

    public void addMapping(Constant fromOpr, Interval toInterval) {
        if (Debug.isLogEnabled()) {
            Debug.log("add move mapping from %s to %s", fromOpr, toInterval);
        }

        mappingFrom.add(null);
        mappingFromOpr.add(fromOpr);
        mappingTo.add(toInterval);
    }

    void resolveAndAppendMoves() {
        if (hasMappings()) {
            resolveMappings();
        }
        appendInsertionBuffer();
    }
}
