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
package com.sun.c1x.alloc;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.util.*;
import com.sun.cri.ci.*;

/**
 *
 * @author Thomas Wuerthinger
 */
final class MoveResolver {

    private final LinearScan allocator;

    private LIRList insertList;
    private int insertIdx;
    private LIRInsertionBuffer insertionBuffer; // buffer where moves are inserted

    private final List<Interval> mappingFrom;
    private final List<CiValue> mappingFromOpr;
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
        this.mappingFrom = new ArrayList<Interval>(8);
        this.mappingFromOpr = new ArrayList<CiValue>(8);
        this.mappingTo = new ArrayList<Interval>(8);
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
        assert insertList != null && insertIdx != -1 : "insert position not set";

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

        HashSet<CiValue> usedRegs = new HashSet<CiValue>();
        if (!multipleReadsAllowed) {
            for (i = 0; i < mappingFrom.size(); i++) {
                Interval interval = mappingFrom.get(i);
                if (interval != null) {
                    boolean unique = usedRegs.add(interval.location());
                    assert unique : "cannot read from same register twice";
                }
            }
        }

        usedRegs.clear();
        for (i = 0; i < mappingTo.size(); i++) {
            Interval interval = mappingTo.get(i);
            boolean unique = usedRegs.add(interval.location());
            assert unique : "cannot write to same register twice";
        }

        usedRegs.clear();
        for (i = 0; i < mappingFrom.size(); i++) {
            Interval interval = mappingFrom.get(i);
            if (interval != null && !interval.location().isRegister()) {
                usedRegs.add(interval.location());
            }
        }
        for (i = 0; i < mappingTo.size(); i++) {
            Interval interval = mappingTo.get(i);
            assert !usedRegs.contains(interval.location()) || interval.location() == mappingFrom.get(i).location() : "stack slots used in mappingFrom must be disjoint to mappingTo";
        }

        return true;
    }

    // mark assignedReg and assignedRegHi of the interval as blocked
    private void blockRegisters(Interval interval) {
        CiValue location = interval.location();
        if (location.isRegister()) {
            int reg = location.asRegister().number;
            assert multipleReadsAllowed || registerBlocked(reg) == 0 : "register already marked as used";
            setRegisterBlocked(reg, 1);
        }
    }

    // mark assignedReg and assignedRegHi of the interval as unblocked
    private void unblockRegisters(Interval interval) {
        CiValue location = interval.location();
        if (location.isRegister()) {
            int reg = location.asRegister().number;
            assert registerBlocked(reg) > 0 : "register already marked as unused";
            setRegisterBlocked(reg, -1);
        }
    }

    /**
     * Checks if the {@linkplain Interval#location() location} of {@code to} is not blocked
     * or is only blocked by {@code from}.
     */
    private boolean safeToProcessMove(Interval from, Interval to) {
        CiValue fromReg = from != null ? from.location() : null;

        CiValue reg = to.location();
        if (reg.isRegister()) {
            if (registerBlocked(reg.asRegister().number) > 1 || (registerBlocked(reg.asRegister().number) == 1 && reg != fromReg)) {
                return false;
            }
        }

        return true;
    }

    private void createInsertionBuffer(LIRList list) {
        assert !insertionBuffer.initialized() : "overwriting existing buffer";
        insertionBuffer.init(list);
    }

    private void appendInsertionBuffer() {
        if (insertionBuffer.initialized()) {
            insertionBuffer.lirList().append(insertionBuffer);
        }
        assert !insertionBuffer.initialized() : "must be uninitialized now";

        insertList = null;
        insertIdx = -1;
    }

    private void insertMove(Interval fromInterval, Interval toInterval) {
        assert fromInterval.operand != toInterval.operand : "from and to interval equal: " + fromInterval;
        assert Util.archKindsEqual(fromInterval.kind(), toInterval.kind()) : "move between different types";
        assert insertList != null && insertIdx != -1 : "must setup insert position first";
        assert insertionBuffer.lirList() == insertList : "wrong insertion buffer";

        CiValue fromOpr = fromInterval.operand;
        CiValue toOpr = toInterval.operand;

        insertionBuffer.move(insertIdx, fromOpr, toOpr, null);

        if (C1XOptions.TraceLinearScanLevel >= 4) {
            TTY.println("MoveResolver: inserted move from %d (%s) to %d (%s)", fromInterval.operandNumber, fromInterval.location(), toInterval.operandNumber, toInterval.location());
        }
    }

    private void insertMove(CiValue fromOpr, Interval toInterval) {
        assert Util.archKindsEqual(fromOpr.kind, toInterval.kind()) : "move between different types";
        assert insertList != null && insertIdx != -1 : "must setup insert position first";
        assert insertionBuffer.lirList() == insertList : "wrong insertion buffer";

        CiValue toOpr = toInterval.operand;
        insertionBuffer.move(insertIdx, fromOpr, toOpr, null);

        if (C1XOptions.TraceLinearScanLevel >= 4) {
            TTY.print("MoveResolver: inserted move from constant %s to %d (%s)", fromOpr, toInterval.operandNumber, toInterval.location());
        }
    }

    private void resolveMappings() {
        //if (C1XOptions.TraceLinearScanLevel >= 4) TTY.println("MoveResolver: resolving mappings for Block B%d, index %d", insertList.block() != null ? insertList.block().blockID : -1, insertIdx);
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
                } else if (fromInterval != null && fromInterval.location().isRegister()) {
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
                // Note: this range is a special case when the integrity of the allocation is checked
                spillInterval.addRange(1, 2);

                // do not allocate a new spill slot for temporary interval, but
                // use spill slot assigned to fromInterval. Otherwise moves from
                // one stack slot to another can happen (not allowed by LIRAssembler
                CiStackSlot spillSlot = fromInterval.spillSlot();
                if (spillSlot == null) {
                    spillSlot = allocator.allocateSpillSlot(spillInterval.kind());
                    fromInterval.setSpillSlot(spillSlot);
                }
                spillInterval.assignLocation(spillSlot);

                if (C1XOptions.TraceLinearScanLevel >= 4) {
                    TTY.println("created new Interval %s for spilling", spillInterval.operand);
                }

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

    void setInsertPosition(LIRList insertList, int insertIdx) {
        //if (C1XOptions.TraceLinearScanLevel >= 4) TTY.println("MoveResolver: setting insert position to Block B%d, index %d", insertList.block() != null ? insertList.block().blockID : -1, insertIdx);
        assert this.insertList == null && this.insertIdx == -1 : "use moveInsertPosition instead of setInsertPosition when data already set";

        createInsertionBuffer(insertList);
        this.insertList = insertList;
        this.insertIdx = insertIdx;
    }

    void moveInsertPosition(LIRList insertList, int insertIdx) {
        //if (C1XOptions.TraceLinearScanLevel >= 4) TTY.println("MoveResolver: moving insert position to Block B%d, index %d", (insertList != null && insertList.block() != null) ? insertList.block().blockID : -1, insertIdx);

        if (this.insertList != null && (this.insertList != insertList || this.insertIdx != insertIdx)) {
            // insert position changed . resolve current mappings
            resolveMappings();
        }

        if (this.insertList != insertList) {
            // block changed . append insertionBuffer because it is
            // bound to a specific block and create a new insertionBuffer
            appendInsertionBuffer();
            createInsertionBuffer(insertList);
        }

        this.insertList = insertList;
        this.insertIdx = insertIdx;
    }

    void addMapping(Interval fromInterval, Interval toInterval) {
        if (C1XOptions.TraceLinearScanLevel >= 4) {
            TTY.println("MoveResolver: adding mapping from interval %d (%s) to interval %d (%s)", fromInterval.operandNumber, fromInterval.location(), toInterval.operandNumber, toInterval.location());
        }

        assert fromInterval.operand != toInterval.operand : "from and to interval equal: " + fromInterval;
        assert Util.archKindsEqual(fromInterval.kind(), toInterval.kind());
        mappingFrom.add(fromInterval);
        mappingFromOpr.add(CiValue.IllegalValue);
        mappingTo.add(toInterval);
    }

    void addMapping(CiValue fromOpr, Interval toInterval) {
        if (C1XOptions.TraceLinearScanLevel >= 4) {
            TTY.println("MoveResolver: adding mapping from %s to %d (%s)", fromOpr, toInterval.operandNumber, toInterval.location());
        }
        assert fromOpr.isConstant() : "only for constants";

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
