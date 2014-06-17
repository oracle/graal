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
package com.oracle.graal.compiler.alloc;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;

/**
 */
final class MoveResolver {

    private final LinearScan allocator;

    private int insertIdx;
    private LIRInsertionBuffer insertionBuffer; // buffer where moves are inserted

    private final List<Interval> mappingFrom;
    private final List<Value> mappingFromOpr;
    private final List<Interval> mappingTo;
    private boolean multipleReadsAllowed;
    private final int[] registerBlocked;

    private int registerBlocked(int reg) {
        return registerBlocked[reg];
    }

    private void setRegisterBlocked(int reg, int direction) {
        assert direction == 1 || direction == -1 : "out of bounds";
        registerBlocked[reg] += direction;
    }

    void setMultipleReadsAllowed() {
        multipleReadsAllowed = true;
    }

    boolean hasMappings() {
        return mappingFrom.size() > 0;
    }

    MoveResolver(LinearScan allocator) {

        this.allocator = allocator;
        this.multipleReadsAllowed = false;
        this.mappingFrom = new ArrayList<>(8);
        this.mappingFromOpr = new ArrayList<>(8);
        this.mappingTo = new ArrayList<>(8);
        this.insertIdx = -1;
        this.insertionBuffer = new LIRInsertionBuffer();
        this.registerBlocked = new int[allocator.registers.length];
        assert checkEmpty();
    }

    boolean checkEmpty() {
        assert mappingFrom.size() == 0 && mappingFromOpr.size() == 0 && mappingTo.size() == 0 : "list must be empty before and after processing";
        for (int i = 0; i < allocator.registers.length; i++) {
            assert registerBlocked(i) == 0 : "register map must be empty before and after processing";
        }
        assert !multipleReadsAllowed : "must have default value";
        return true;
    }

    private boolean verifyBeforeResolve() {
        assert mappingFrom.size() == mappingFromOpr.size() : "length must be equal";
        assert mappingFrom.size() == mappingTo.size() : "length must be equal";
        assert insertIdx != -1 : "insert position not set";

        int i;
        int j;
        if (!multipleReadsAllowed) {
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
        if (!multipleReadsAllowed) {
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

        usedRegs.clear();
        for (i = 0; i < mappingFrom.size(); i++) {
            Interval interval = mappingFrom.get(i);
            if (interval != null && !isRegister(interval.location())) {
                usedRegs.add(interval.location());
            }
        }
        for (i = 0; i < mappingTo.size(); i++) {
            Interval interval = mappingTo.get(i);
            assert !usedRegs.contains(interval.location()) || interval.location().equals(mappingFrom.get(i).location()) : "stack slots used in mappingFrom must be disjoint to mappingTo";
        }

        return true;
    }

    // mark assignedReg and assignedRegHi of the interval as blocked
    private void blockRegisters(Interval interval) {
        Value location = interval.location();
        if (isRegister(location)) {
            int reg = asRegister(location).number;
            assert multipleReadsAllowed || registerBlocked(reg) == 0 : "register already marked as used";
            setRegisterBlocked(reg, 1);
        }
    }

    // mark assignedReg and assignedRegHi of the interval as unblocked
    private void unblockRegisters(Interval interval) {
        Value location = interval.location();
        if (isRegister(location)) {
            int reg = asRegister(location).number;
            assert registerBlocked(reg) > 0 : "register already marked as unused";
            setRegisterBlocked(reg, -1);
        }
    }

    /**
     * Checks if the {@linkplain Interval#location() location} of {@code to} is not blocked or is
     * only blocked by {@code from}.
     */
    private boolean safeToProcessMove(Interval from, Interval to) {
        Value fromReg = from != null ? from.location() : null;

        Value reg = to.location();
        if (isRegister(reg)) {
            if (registerBlocked(asRegister(reg).number) > 1 || (registerBlocked(asRegister(reg).number) == 1 && !reg.equals(fromReg))) {
                return false;
            }
        }

        return true;
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
        assert fromInterval.kind().equals(toInterval.kind()) : "move between different types";
        assert insertIdx != -1 : "must setup insert position first";

        AllocatableValue fromOpr = fromInterval.operand;
        AllocatableValue toOpr = toInterval.operand;

        insertionBuffer.append(insertIdx, allocator.ir.getSpillMoveFactory().createMove(toOpr, fromOpr));

        Debug.log("insert move from %s to %s at %d", fromInterval, toInterval, insertIdx);
    }

    private void insertMove(Value fromOpr, Interval toInterval) {
        assert fromOpr.getLIRKind().equals(toInterval.kind()) : "move between different types";
        assert insertIdx != -1 : "must setup insert position first";

        AllocatableValue toOpr = toInterval.operand;
        insertionBuffer.append(insertIdx, allocator.ir.getSpillMoveFactory().createMove(toOpr, fromOpr));

        Debug.log("insert move from value %s to %s at %d", fromOpr, toInterval, insertIdx);
    }

    private void resolveMappings() {
        assert verifyBeforeResolve();

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
                // no move could be processed because there is a cycle in the move list
                // (e.g. r1 . r2, r2 . r1), so one interval must be spilled to memory
                assert spillCandidate != -1 : "no interval in register for spilling found";

                // create a new spill interval and assign a stack slot to it
                Interval fromInterval = mappingFrom.get(spillCandidate);
                Interval spillInterval = allocator.createDerivedInterval(fromInterval);
                spillInterval.setKind(fromInterval.kind());

                // add a dummy range because real position is difficult to calculate
                // Note: this range is a special case when the integrity of the allocation is
                // checked
                spillInterval.addRange(1, 2);

                // do not allocate a new spill slot for temporary interval, but
                // use spill slot assigned to fromInterval. Otherwise moves from
                // one stack slot to another can happen (not allowed by LIRAssembler
                StackSlot spillSlot = fromInterval.spillSlot();
                if (spillSlot == null) {
                    spillSlot = allocator.frameMap.allocateSpillSlot(spillInterval.kind());
                    fromInterval.setSpillSlot(spillSlot);
                }
                spillInterval.assignLocation(spillSlot);

                Debug.log("created new Interval for spilling: %s", spillInterval);

                // insert a move from register to stack and update the mapping
                insertMove(fromInterval, spillInterval);
                mappingFrom.set(spillCandidate, spillInterval);
                unblockRegisters(fromInterval);
            }
        }

        // reset to default value
        multipleReadsAllowed = false;

        // check that all intervals have been processed
        assert checkEmpty();
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

    void addMapping(Interval fromInterval, Interval toInterval) {

        if (isIllegal(toInterval.location()) && toInterval.canMaterialize()) {
            Debug.log("no store to rematerializable interval %s needed", toInterval);
            return;
        }
        if (isIllegal(fromInterval.location()) && fromInterval.canMaterialize()) {
            // Instead of a reload, re-materialize the value
            Value rematValue = fromInterval.getMaterializedValue();
            addMapping(rematValue, toInterval);
            return;
        }
        Debug.log("add move mapping from %s to %s", fromInterval, toInterval);

        assert !fromInterval.operand.equals(toInterval.operand) : "from and to interval equal: " + fromInterval;
        assert fromInterval.kind().equals(toInterval.kind());
        mappingFrom.add(fromInterval);
        mappingFromOpr.add(Value.ILLEGAL);
        mappingTo.add(toInterval);
    }

    void addMapping(Value fromOpr, Interval toInterval) {
        Debug.log("add move mapping from %s to %s", fromOpr, toInterval);

        assert isConstant(fromOpr) : "only for constants";

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
