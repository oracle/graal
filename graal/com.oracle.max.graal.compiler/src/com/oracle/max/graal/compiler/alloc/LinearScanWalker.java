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
package com.oracle.max.graal.compiler.alloc;

import static com.sun.cri.ci.CiUtil.*;

import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.alloc.Interval.RegisterBinding;
import com.oracle.max.graal.compiler.alloc.Interval.RegisterPriority;
import com.oracle.max.graal.compiler.alloc.Interval.SpillState;
import com.oracle.max.graal.compiler.alloc.Interval.State;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiRegister.RegisterFlag;

/**
 */
final class LinearScanWalker extends IntervalWalker {

    private CiRegister[] availableRegs;

    private final int[] usePos;
    private final int[] blockPos;

    private List<Interval>[] spillIntervals;

    private MoveResolver moveResolver; // for ordering spill moves

    // accessors mapped to same functions in class LinearScan
    int blockCount() {
        return allocator.blockCount();
    }

    LIRBlock blockAt(int idx) {
        return allocator.blockAt(idx);
    }

    LIRBlock blockOfOpWithId(int opId) {
        return allocator.blockForId(opId);
    }

    LinearScanWalker(LinearScan allocator, Interval unhandledFixedFirst, Interval unhandledAnyFirst) {
        super(allocator, unhandledFixedFirst, unhandledAnyFirst);
        moveResolver = new MoveResolver(allocator);
        spillIntervals = Util.uncheckedCast(new List[allocator.registers.length]);
        for (int i = 0; i < allocator.registers.length; i++) {
            spillIntervals[i] = new ArrayList<Interval>(2);
        }
        usePos = new int[allocator.registers.length];
        blockPos = new int[allocator.registers.length];
    }

    void initUseLists(boolean onlyProcessUsePos) {
        for (CiRegister register : availableRegs) {
            int i = register.number;
            usePos[i] = Integer.MAX_VALUE;

            if (!onlyProcessUsePos) {
                blockPos[i] = Integer.MAX_VALUE;
                spillIntervals[i].clear();
            }
        }
    }

    void excludeFromUse(Interval i) {
        CiValue location = i.location();
        int i1 = location.asRegister().number;
        if (i1 >= availableRegs[0].number && i1 <= availableRegs[availableRegs.length - 1].number) {
            usePos[i1] = 0;
        }
    }

    void setUsePos(Interval interval, int usePos, boolean onlyProcessUsePos) {
        if (usePos != -1) {
            assert usePos != 0 : "must use excludeFromUse to set usePos to 0";
            int i = interval.location().asRegister().number;
            if (i >= availableRegs[0].number && i <= availableRegs[availableRegs.length - 1].number) {
                if (this.usePos[i] > usePos) {
                    this.usePos[i] = usePos;
                }
                if (!onlyProcessUsePos) {
                    spillIntervals[i].add(interval);
                }
            }
        }
    }

    void setBlockPos(Interval i, int blockPos) {
        if (blockPos != -1) {
            int reg = i.location().asRegister().number;
            if (reg >= availableRegs[0].number && reg <= availableRegs[availableRegs.length - 1].number) {
                if (this.blockPos[reg] > blockPos) {
                    this.blockPos[reg] = blockPos;
                }
                if (usePos[reg] > blockPos) {
                    usePos[reg] = blockPos;
                }
            }
        }
    }

    void freeExcludeActiveFixed() {
        Interval interval = activeLists.get(RegisterBinding.Fixed);
        while (interval != Interval.EndMarker) {
            assert interval.location().isRegister() : "active interval must have a register assigned";
            excludeFromUse(interval);
            interval = interval.next;
        }
    }

    void freeExcludeActiveAny() {
        Interval interval = activeLists.get(RegisterBinding.Any);
        while (interval != Interval.EndMarker) {
            assert interval.location().isRegister() : "active interval must have a register assigned";
            excludeFromUse(interval);
            interval = interval.next;
        }
    }

    void freeCollectInactiveFixed(Interval current) {
        Interval interval = inactiveLists.get(RegisterBinding.Fixed);
        while (interval != Interval.EndMarker) {
            if (current.to() <= interval.currentFrom()) {
                assert interval.currentIntersectsAt(current) == -1 : "must not intersect";
                setUsePos(interval, interval.currentFrom(), true);
            } else {
                setUsePos(interval, interval.currentIntersectsAt(current), true);
            }
            interval = interval.next;
        }
    }

    void freeCollectInactiveAny(Interval current) {
        Interval interval = inactiveLists.get(RegisterBinding.Any);
        while (interval != Interval.EndMarker) {
            setUsePos(interval, interval.currentIntersectsAt(current), true);
            interval = interval.next;
        }
    }

    void freeCollectUnhandled(RegisterBinding kind, Interval current) {
        Interval interval = unhandledLists.get(kind);
        while (interval != Interval.EndMarker) {
            setUsePos(interval, interval.intersectsAt(current), true);
            if (kind == RegisterBinding.Fixed && current.to() <= interval.from()) {
                setUsePos(interval, interval.from(), true);
            }
            interval = interval.next;
        }
    }

    void spillExcludeActiveFixed() {
        Interval interval = activeLists.get(RegisterBinding.Fixed);
        while (interval != Interval.EndMarker) {
            excludeFromUse(interval);
            interval = interval.next;
        }
    }

    void spillBlockUnhandledFixed(Interval current) {
        Interval interval = unhandledLists.get(RegisterBinding.Fixed);
        while (interval != Interval.EndMarker) {
            setBlockPos(interval, interval.intersectsAt(current));
            interval = interval.next;
        }
    }

    void spillBlockInactiveFixed(Interval current) {
        Interval interval = inactiveLists.get(RegisterBinding.Fixed);
        while (interval != Interval.EndMarker) {
            if (current.to() > interval.currentFrom()) {
                setBlockPos(interval, interval.currentIntersectsAt(current));
            } else {
                assert interval.currentIntersectsAt(current) == -1 : "invalid optimization: intervals intersect";
            }

            interval = interval.next;
        }
    }

    void spillCollectActiveAny() {
        Interval interval = activeLists.get(RegisterBinding.Any);
        while (interval != Interval.EndMarker) {
            setUsePos(interval, Math.min(interval.nextUsage(RegisterPriority.LiveAtLoopEnd, currentPosition), interval.to()), false);
            interval = interval.next;
        }
    }

    void spillCollectInactiveAny(Interval current) {
        Interval interval = inactiveLists.get(RegisterBinding.Any);
        while (interval != Interval.EndMarker) {
            if (interval.currentIntersects(current)) {
                setUsePos(interval, Math.min(interval.nextUsage(RegisterPriority.LiveAtLoopEnd, currentPosition), interval.to()), false);
            }
            interval = interval.next;
        }
    }

    void insertMove(int opId, Interval srcIt, Interval dstIt) {
        // output all moves here. When source and target are equal, the move is
        // optimized away later in assignRegNums

        opId = (opId + 1) & ~1;
        LIRBlock opBlock = allocator.blockForId(opId);
        assert opId > 0 && allocator.blockForId(opId - 2) == opBlock : "cannot insert move at block boundary";

        // calculate index of instruction inside instruction list of current block
        // the minimal index (for a block with no spill moves) can be calculated because the
        // numbering of instructions is known.
        // When the block already contains spill moves, the index must be increased until the
        // correct index is reached.
        List<LIRInstruction> list = opBlock.lir();
        int index = (opId - list.get(0).id()) >> 1;
        assert list.get(index).id() <= opId : "error in calculation";

        while (list.get(index).id() != opId) {
            index++;
            assert 0 <= index && index < list.size() : "index out of bounds";
        }
        assert 1 <= index && index < list.size() : "index out of bounds";
        assert list.get(index).id() == opId : "error in calculation";

        // insert new instruction before instruction at position index
        moveResolver.moveInsertPosition(opBlock.lir(), index - 1);
        moveResolver.addMapping(srcIt, dstIt);
    }

    int findOptimalSplitPos(LIRBlock minBlock, LIRBlock maxBlock, int maxSplitPos) {
        int fromBlockNr = minBlock.linearScanNumber();
        int toBlockNr = maxBlock.linearScanNumber();

        assert 0 <= fromBlockNr && fromBlockNr < blockCount() : "out of range";
        assert 0 <= toBlockNr && toBlockNr < blockCount() : "out of range";
        assert fromBlockNr < toBlockNr : "must cross block boundary";

        // Try to split at end of maxBlock. If this would be after
        // maxSplitPos, then use the begin of maxBlock
        int optimalSplitPos = maxBlock.lastLirInstructionId() + 2;
        if (optimalSplitPos > maxSplitPos) {
            optimalSplitPos = maxBlock.firstLirInstructionId();
        }

        int minLoopDepth = maxBlock.loopDepth();
        for (int i = toBlockNr - 1; i >= fromBlockNr; i--) {
            LIRBlock cur = blockAt(i);

            if (cur.loopDepth() < minLoopDepth) {
                // block with lower loop-depth found . split at the end of this block
                minLoopDepth = cur.loopDepth();
                optimalSplitPos = cur.lastLirInstructionId() + 2;
            }
        }
        assert optimalSplitPos > allocator.maxOpId() || allocator.isBlockBegin(optimalSplitPos) : "algorithm must move split pos to block boundary";

        return optimalSplitPos;
    }

    int findOptimalSplitPos(Interval interval, int minSplitPos, int maxSplitPos, boolean doLoopOptimization) {
        int optimalSplitPos = -1;
        if (minSplitPos == maxSplitPos) {
            // trivial case, no optimization of split position possible
            if (GraalOptions.TraceLinearScanLevel >= 4) {
                TTY.println("      min-pos and max-pos are equal, no optimization possible");
            }
            optimalSplitPos = minSplitPos;

        } else {
            assert minSplitPos < maxSplitPos : "must be true then";
            assert minSplitPos > 0 : "cannot access minSplitPos - 1 otherwise";

            // reason for using minSplitPos - 1: when the minimal split pos is exactly at the
            // beginning of a block, then minSplitPos is also a possible split position.
            // Use the block before as minBlock, because then minBlock.lastLirInstructionId() + 2 == minSplitPos
            LIRBlock minBlock = allocator.blockForId(minSplitPos - 1);

            // reason for using maxSplitPos - 1: otherwise there would be an assert on failure
            // when an interval ends at the end of the last block of the method
            // (in this case, maxSplitPos == allocator().maxLirOpId() + 2, and there is no
            // block at this opId)
            LIRBlock maxBlock = allocator.blockForId(maxSplitPos - 1);

            assert minBlock.linearScanNumber() <= maxBlock.linearScanNumber() : "invalid order";
            if (minBlock == maxBlock) {
                // split position cannot be moved to block boundary : so split as late as possible
                if (GraalOptions.TraceLinearScanLevel >= 4) {
                    TTY.println("      cannot move split pos to block boundary because minPos and maxPos are in same block");
                }
                optimalSplitPos = maxSplitPos;

            } else {
                if (interval.hasHoleBetween(maxSplitPos - 1, maxSplitPos) && !allocator.isBlockBegin(maxSplitPos)) {
                    // Do not move split position if the interval has a hole before maxSplitPos.
                    // Intervals resulting from Phi-Functions have more than one definition (marked
                    // as mustHaveRegister) with a hole before each definition. When the register is needed
                    // for the second definition : an earlier reloading is unnecessary.
                    if (GraalOptions.TraceLinearScanLevel >= 4) {
                        TTY.println("      interval has hole just before maxSplitPos, so splitting at maxSplitPos");
                    }
                    optimalSplitPos = maxSplitPos;

                } else {
                    // seach optimal block boundary between minSplitPos and maxSplitPos
                    if (GraalOptions.TraceLinearScanLevel >= 4) {
                        TTY.println("      moving split pos to optimal block boundary between block B%d and B%d", minBlock.blockID(), maxBlock.blockID());
                    }

                    if (doLoopOptimization) {
                        // Loop optimization: if a loop-end marker is found between min- and max-position :
                        // then split before this loop
                        int loopEndPos = interval.nextUsageExact(RegisterPriority.LiveAtLoopEnd, minBlock.lastLirInstructionId() + 2);
                        if (GraalOptions.TraceLinearScanLevel >= 4) {
                            TTY.println("      loop optimization: loop end found at pos %d", loopEndPos);
                        }

                        assert loopEndPos > minSplitPos : "invalid order";
                        if (loopEndPos < maxSplitPos) {
                            // loop-end marker found between min- and max-position
                            // if it is not the end marker for the same loop as the min-position : then move
                            // the max-position to this loop block.
                            // Desired result: uses tagged as shouldHaveRegister inside a loop cause a reloading
                            // of the interval (normally, only mustHaveRegister causes a reloading)
                            LIRBlock loopBlock = allocator.blockForId(loopEndPos);

                            if (GraalOptions.TraceLinearScanLevel >= 4) {
                                TTY.println("      interval is used in loop that ends in block B%d, so trying to move maxBlock back from B%d to B%d", loopBlock.blockID(), maxBlock.blockID(), loopBlock.blockID());
                            }
                            assert loopBlock != minBlock : "loopBlock and minBlock must be different because block boundary is needed between";

                            optimalSplitPos = findOptimalSplitPos(minBlock, loopBlock, loopBlock.lastLirInstructionId() + 2);
                            if (optimalSplitPos == loopBlock.lastLirInstructionId() + 2) {
                                optimalSplitPos = -1;
                                if (GraalOptions.TraceLinearScanLevel >= 4) {
                                    TTY.println("      loop optimization not necessary");
                                }
                            } else {
                                if (GraalOptions.TraceLinearScanLevel >= 4) {
                                    TTY.println("      loop optimization successful");
                                }
                            }
                        }
                    }

                    if (optimalSplitPos == -1) {
                        // not calculated by loop optimization
                        optimalSplitPos = findOptimalSplitPos(minBlock, maxBlock, maxSplitPos);
                    }
                }
            }
        }
        if (GraalOptions.TraceLinearScanLevel >= 4) {
            TTY.println("      optimal split position: %d", optimalSplitPos);
        }

        return optimalSplitPos;
    }

    // split an interval at the optimal position between minSplitPos and
    // maxSplitPos in two parts:
    // 1) the left part has already a location assigned
    // 2) the right part is sorted into to the unhandled-list
    void splitBeforeUsage(Interval interval, int minSplitPos, int maxSplitPos) {
        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.println("----- splitting interval: ");
        }
        if (GraalOptions.TraceLinearScanLevel >= 4) {
            TTY.println(interval.logString(allocator));
        }
        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.println("      between %d and %d", minSplitPos, maxSplitPos);
        }

        assert interval.from() < minSplitPos : "cannot split at start of interval";
        assert currentPosition < minSplitPos : "cannot split before current position";
        assert minSplitPos <= maxSplitPos : "invalid order";
        assert maxSplitPos <= interval.to() : "cannot split after end of interval";

        int optimalSplitPos = findOptimalSplitPos(interval, minSplitPos, maxSplitPos, true);

        assert minSplitPos <= optimalSplitPos && optimalSplitPos <= maxSplitPos : "out of range";
        assert optimalSplitPos <= interval.to() : "cannot split after end of interval";
        assert optimalSplitPos > interval.from() : "cannot split at start of interval";

        if (optimalSplitPos == interval.to() && interval.nextUsage(RegisterPriority.MustHaveRegister, minSplitPos) == Integer.MAX_VALUE) {
            // the split position would be just before the end of the interval
            // . no split at all necessary
            if (GraalOptions.TraceLinearScanLevel >= 4) {
                TTY.println("      no split necessary because optimal split position is at end of interval");
            }
            return;
        }

        // must calculate this before the actual split is performed and before split position is moved to odd opId
        boolean moveNecessary = !allocator.isBlockBegin(optimalSplitPos) && !interval.hasHoleBetween(optimalSplitPos - 1, optimalSplitPos);

        if (!allocator.isBlockBegin(optimalSplitPos)) {
            // move position before actual instruction (odd opId)
            optimalSplitPos = (optimalSplitPos - 1) | 1;
        }

        if (GraalOptions.TraceLinearScanLevel >= 4) {
            TTY.println("      splitting at position %d", optimalSplitPos);
        }
        assert allocator.isBlockBegin(optimalSplitPos) || (optimalSplitPos % 2 == 1) : "split pos must be odd when not on block boundary";
        assert !allocator.isBlockBegin(optimalSplitPos) || (optimalSplitPos % 2 == 0) : "split pos must be even on block boundary";

        Interval splitPart = interval.split(optimalSplitPos, allocator);

        allocator.copyRegisterFlags(interval, splitPart);
        splitPart.setInsertMoveWhenActivated(moveNecessary);

        assert splitPart.from() >= current.currentFrom() : "cannot append new interval before current walk position";
        unhandledLists.addToListSortedByStartAndUsePositions(RegisterBinding.Any, splitPart);

        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.println("      split interval in two parts (insertMoveWhenActivated: %b)", moveNecessary);
        }
        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.print("      ");
            TTY.println(interval.logString(allocator));
            TTY.print("      ");
            TTY.println(splitPart.logString(allocator));
        }
    }

// split an interval at the optimal position between minSplitPos and
// maxSplitPos in two parts:
// 1) the left part has already a location assigned
// 2) the right part is always on the stack and therefore ignored in further processing

    void splitForSpilling(Interval interval) {
        // calculate allowed range of splitting position
        int maxSplitPos = currentPosition;
        int minSplitPos = Math.max(interval.previousUsage(RegisterPriority.ShouldHaveRegister, maxSplitPos) + 1, interval.from());

        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.print("----- splitting and spilling interval: ");
            TTY.println(interval.logString(allocator));
            TTY.println("      between %d and %d", minSplitPos, maxSplitPos);
        }

        assert interval.state == State.Active : "why spill interval that is not active?";
        assert interval.from() <= minSplitPos : "cannot split before start of interval";
        assert minSplitPos <= maxSplitPos : "invalid order";
        assert maxSplitPos < interval.to() : "cannot split at end end of interval";
        assert currentPosition < interval.to() : "interval must not end before current position";

        if (minSplitPos == interval.from()) {
            // the whole interval is never used, so spill it entirely to memory
            if (GraalOptions.TraceLinearScanLevel >= 2) {
                TTY.println("      spilling entire interval because split pos is at beginning of interval");
                TTY.println("      use positions: " + interval.usePosList().size());
            }
            assert interval.firstUsage(RegisterPriority.ShouldHaveRegister) > currentPosition : "interval must not have use position before currentPosition";

            allocator.assignSpillSlot(interval);
            allocator.changeSpillState(interval, minSplitPos);

            // Also kick parent intervals out of register to memory when they have no use
            // position. This avoids short interval in register surrounded by intervals in
            // memory . avoid useless moves from memory to register and back
            Interval parent = interval;
            while (parent != null && parent.isSplitChild()) {
                parent = parent.getSplitChildBeforeOpId(parent.from());

                if (parent.location().isRegister()) {
                    if (parent.firstUsage(RegisterPriority.ShouldHaveRegister) == Integer.MAX_VALUE) {
                        // parent is never used, so kick it out of its assigned register
                        if (GraalOptions.TraceLinearScanLevel >= 4) {
                            TTY.println("      kicking out interval %d out of its register because it is never used", parent.operandNumber);
                        }
                        allocator.assignSpillSlot(parent);
                    } else {
                        // do not go further back because the register is actually used by the interval
                        parent = null;
                    }
                }
            }

        } else {
            // search optimal split pos, split interval and spill only the right hand part
            int optimalSplitPos = findOptimalSplitPos(interval, minSplitPos, maxSplitPos, false);

            assert minSplitPos <= optimalSplitPos && optimalSplitPos <= maxSplitPos : "out of range";
            assert optimalSplitPos < interval.to() : "cannot split at end of interval";
            assert optimalSplitPos >= interval.from() : "cannot split before start of interval";

            if (!allocator.isBlockBegin(optimalSplitPos)) {
                // move position before actual instruction (odd opId)
                optimalSplitPos = (optimalSplitPos - 1) | 1;
            }

            if (GraalOptions.TraceLinearScanLevel >= 4) {
                TTY.println("      splitting at position %d", optimalSplitPos);
            }
            assert allocator.isBlockBegin(optimalSplitPos) || (optimalSplitPos % 2 == 1) : "split pos must be odd when not on block boundary";
            assert !allocator.isBlockBegin(optimalSplitPos) || (optimalSplitPos % 2 == 0) : "split pos must be even on block boundary";

            Interval spilledPart = interval.split(optimalSplitPos, allocator);
            allocator.assignSpillSlot(spilledPart);
            allocator.changeSpillState(spilledPart, optimalSplitPos);

            if (!allocator.isBlockBegin(optimalSplitPos)) {
                if (GraalOptions.TraceLinearScanLevel >= 4) {
                    TTY.println("      inserting move from interval %d to %d", interval.operandNumber, spilledPart.operandNumber);
                }
                insertMove(optimalSplitPos, interval, spilledPart);
            }

            // the currentSplitChild is needed later when moves are inserted for reloading
            assert spilledPart.currentSplitChild() == interval : "overwriting wrong currentSplitChild";
            spilledPart.makeCurrentSplitChild();

            if (GraalOptions.TraceLinearScanLevel >= 2) {
                TTY.println("      split interval in two parts");
                TTY.print("      ");
                TTY.println(interval.logString(allocator));
                TTY.print("      ");
                TTY.println(spilledPart.logString(allocator));
            }
        }
    }

    void splitStackInterval(Interval interval) {
        int minSplitPos = currentPosition + 1;
        int maxSplitPos = Math.min(interval.firstUsage(RegisterPriority.ShouldHaveRegister), interval.to());

        splitBeforeUsage(interval, minSplitPos, maxSplitPos);
    }

    void splitWhenPartialRegisterAvailable(Interval interval, int registerAvailableUntil) {
        int minSplitPos = Math.max(interval.previousUsage(RegisterPriority.ShouldHaveRegister, registerAvailableUntil), interval.from() + 1);
        splitBeforeUsage(interval, minSplitPos, registerAvailableUntil);
    }

    void splitAndSpillInterval(Interval interval) {
        assert interval.state == State.Active || interval.state == State.Inactive : "other states not allowed";

        int currentPos = currentPosition;
        if (interval.state == State.Inactive) {
            // the interval is currently inactive, so no spill slot is needed for now.
            // when the split part is activated, the interval has a new chance to get a register,
            // so in the best case no stack slot is necessary
            assert interval.hasHoleBetween(currentPos - 1, currentPos + 1) : "interval can not be inactive otherwise";
            splitBeforeUsage(interval, currentPos + 1, currentPos + 1);

        } else {
            // search the position where the interval must have a register and split
            // at the optimal position before.
            // The new created part is added to the unhandled list and will get a register
            // when it is activated
            int minSplitPos = currentPos + 1;
            int maxSplitPos = Math.min(interval.nextUsage(RegisterPriority.MustHaveRegister, minSplitPos), interval.to());

            splitBeforeUsage(interval, minSplitPos, maxSplitPos);

            assert interval.nextUsage(RegisterPriority.MustHaveRegister, currentPos) == Integer.MAX_VALUE : "the remaining part is spilled to stack and therefore has no register";
            splitForSpilling(interval);
        }
    }

    boolean allocFreeRegister(Interval interval) {
        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.println("trying to find free register for " + interval.logString(allocator));
        }

        initUseLists(true);
        freeExcludeActiveFixed();
        freeExcludeActiveAny();
        freeCollectInactiveFixed(interval);
        freeCollectInactiveAny(interval);
        // freeCollectUnhandled(fixedKind, cur);
        assert unhandledLists.get(RegisterBinding.Fixed) == Interval.EndMarker : "must not have unhandled fixed intervals because all fixed intervals have a use at position 0";

        // usePos contains the start of the next interval that has this register assigned
        // (either as a fixed register or a normal allocated register in the past)
        // only intervals overlapping with cur are processed, non-overlapping invervals can be ignored safely
        if (GraalOptions.TraceLinearScanLevel >= 4) {
            TTY.println("      state of registers:");
            for (CiRegister register : availableRegs) {
                int i = register.number;
                TTY.println("      reg %d: usePos: %d", register.number, usePos[i]);
            }
        }

        CiRegister hint = null;
        Interval locationHint = interval.locationHint(true, allocator);
        if (locationHint != null && locationHint.location() != null && locationHint.location().isRegister()) {
            hint = locationHint.location().asRegister();
            if (GraalOptions.TraceLinearScanLevel >= 4) {
                TTY.println("      hint register %d from interval %s", hint.number, locationHint.logString(allocator));
            }
        }
        assert interval.location() == null : "register already assigned to interval";

        // the register must be free at least until this position
        int regNeededUntil = interval.from() + 1;
        int intervalTo = interval.to();

        boolean needSplit = false;
        int splitPos = -1;

        CiRegister reg = null;
        CiRegister minFullReg = null;
        CiRegister maxPartialReg = null;

        for (int i = 0; i < availableRegs.length; ++i) {
            CiRegister availableReg = availableRegs[i];
            int number = availableReg.number;
            if (usePos[number] >= intervalTo) {
                // this register is free for the full interval
                if (minFullReg == null || availableReg == hint || (usePos[number] < usePos[minFullReg.number] && minFullReg != hint)) {
                    minFullReg = availableReg;
                }
            } else if (usePos[number] > regNeededUntil) {
                // this register is at least free until regNeededUntil
                if (maxPartialReg == null || availableReg == hint || (usePos[number] > usePos[maxPartialReg.number] && maxPartialReg != hint)) {
                    maxPartialReg = availableReg;
                }
            }
        }

        if (minFullReg != null) {
            reg = minFullReg;
        } else if (maxPartialReg != null) {
            needSplit = true;
            reg = maxPartialReg;
        } else {
            return false;
        }

        splitPos = usePos[reg.number];
        interval.assignLocation(reg.asValue(interval.kind()));
        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.println("selected register %d", reg.number);
        }

        assert splitPos > 0 : "invalid splitPos";
        if (needSplit) {
            // register not available for full interval, so split it
            splitWhenPartialRegisterAvailable(interval, splitPos);
        }

        // only return true if interval is completely assigned
        return true;
    }

    CiRegister findLockedRegister(int regNeededUntil, int intervalTo, CiValue ignoreReg, boolean[] needSplit) {
        int maxReg = -1;
        CiRegister ignore = ignoreReg.isRegister() ? ignoreReg.asRegister() : null;

        for (CiRegister reg : availableRegs) {
            int i = reg.number;
            if (reg == ignore) {
                // this register must be ignored

            } else if (usePos[i] > regNeededUntil) {
                if (maxReg == -1 || (usePos[i] > usePos[maxReg])) {
                    maxReg = i;
                }
            }
        }

        if (maxReg != -1) {
            if (blockPos[maxReg] <= intervalTo) {
                needSplit[0] = true;
            }
            return availableRegs[maxReg];
        }

        return null;
    }

    void splitAndSpillIntersectingIntervals(CiRegister reg) {
        assert reg != null : "no register assigned";

        for (int i = 0; i < spillIntervals[reg.number].size(); i++) {
            Interval interval = spillIntervals[reg.number].get(i);
            removeFromList(interval);
            splitAndSpillInterval(interval);
        }
    }

    // Split an Interval and spill it to memory so that cur can be placed in a register
    void allocLockedRegister(Interval interval) {
        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.println("need to split and spill to get register for " + interval.logString(allocator));
        }

        // collect current usage of registers
        initUseLists(false);
        spillExcludeActiveFixed();
        //  spillBlockUnhandledFixed(cur);
        assert unhandledLists.get(RegisterBinding.Fixed) == Interval.EndMarker : "must not have unhandled fixed intervals because all fixed intervals have a use at position 0";
        spillBlockInactiveFixed(interval);
        spillCollectActiveAny();
        spillCollectInactiveAny(interval);

        if (GraalOptions.TraceLinearScanLevel >= 4) {
            TTY.println("      state of registers:");
            for (CiRegister reg : availableRegs) {
                int i = reg.number;
                TTY.print("      reg %d: usePos: %d, blockPos: %d, intervals: ", i, usePos[i], blockPos[i]);
                for (int j = 0; j < spillIntervals[i].size(); j++) {
                    TTY.print("%d ", spillIntervals[i].get(j).operandNumber);
                }
                TTY.println();
            }
        }

        // the register must be free at least until this position
        int firstUsage = interval.firstUsage(RegisterPriority.MustHaveRegister);
        int regNeededUntil = Math.min(firstUsage, interval.from() + 1);
        int intervalTo = interval.to();
        assert regNeededUntil > 0 && regNeededUntil < Integer.MAX_VALUE : "interval has no use";

        CiRegister reg = null;
        CiRegister ignore = interval.location() != null && interval.location().isRegister() ? interval.location().asRegister() : null;
        for (CiRegister availableReg : availableRegs) {
            int number = availableReg.number;
            if (availableReg == ignore) {
                // this register must be ignored
            } else if (usePos[number] > regNeededUntil) {
                if (reg == null || (usePos[number] > usePos[reg.number])) {
                    reg = availableReg;
                }
            }
        }

        if (reg == null || usePos[reg.number] <= firstUsage) {
            // the first use of cur is later than the spilling position -> spill cur
            if (GraalOptions.TraceLinearScanLevel >= 4) {
                TTY.println("able to spill current interval. firstUsage(register): %d, usePos: %d", firstUsage, reg == null ? 0 : usePos[reg.number]);
            }

            if (firstUsage <= interval.from() + 1) {
                assert false : "cannot spill interval that is used in first instruction (possible reason: no register found) firstUsage=" + firstUsage + ", interval.from()=" + interval.from();
                // assign a reasonable register and do a bailout in product mode to avoid errors
                allocator.assignSpillSlot(interval);
                throw new CiBailout("LinearScan: no register found");
            }

            splitAndSpillInterval(interval);
            return;
        }

        boolean needSplit = blockPos[reg.number] <= intervalTo;

        int splitPos = blockPos[reg.number];

        if (GraalOptions.TraceLinearScanLevel >= 4) {
            TTY.println("decided to use register %d", reg.number);
        }
        assert splitPos > 0 : "invalid splitPos";
        assert needSplit || splitPos > interval.from() : "splitting interval at from";

        interval.assignLocation(reg.asValue(interval.kind()));
        if (needSplit) {
            // register not available for full interval :  so split it
            splitWhenPartialRegisterAvailable(interval, splitPos);
        }

        // perform splitting and spilling for all affected intervals
        splitAndSpillIntersectingIntervals(reg);
    }

    boolean noAllocationPossible(Interval interval) {

        if (compilation.compiler.target.arch.isX86()) {
            // fast calculation of intervals that can never get a register because the
            // the next instruction is a call that blocks all registers
            // Note: this does not work if callee-saved registers are available (e.g. on Sparc)

            // check if this interval is the result of a split operation
            // (an interval got a register until this position)
            int pos = interval.from();
            if (isOdd(pos)) {
                // the current instruction is a call that blocks all registers
                if (pos < allocator.maxOpId() && allocator.hasCall(pos + 1) && interval.to() > pos + 1) {
                    if (GraalOptions.TraceLinearScanLevel >= 4) {
                        TTY.println("      free register cannot be available because all registers blocked by following call");
                    }

                    // safety check that there is really no register available
                    assert !allocFreeRegister(interval) : "found a register for this interval";
                    return true;
                }

            }
        }
        return false;
    }

    void initVarsForAlloc(Interval interval) {
        EnumMap<RegisterFlag, CiRegister[]> categorizedRegs = allocator.compilation.registerConfig.getCategorizedAllocatableRegisters();
        if (allocator.operands.mustBeByteRegister(interval.operand)) {
            assert interval.kind()  != CiKind.Float && interval.kind()  != CiKind.Double : "cpu regs only";
            availableRegs = categorizedRegs.get(RegisterFlag.Byte);
        } else if (interval.kind()  == CiKind.Float || interval.kind()  == CiKind.Double) {
            availableRegs = categorizedRegs.get(RegisterFlag.FPU);
        } else {
            availableRegs = categorizedRegs.get(RegisterFlag.CPU);
        }
    }

    boolean isMove(LIRInstruction op, Interval from, Interval to) {
        if (op.code != StandardOpcode.MOVE) {
            return false;
        }

        CiValue input = op.input(0);
        CiValue result = op.result();
        return input.isVariable() && result.isVariable() && input == from.operand && result == to.operand;
    }

    // optimization (especially for phi functions of nested loops):
    // assign same spill slot to non-intersecting intervals
    void combineSpilledIntervals(Interval interval) {
        if (interval.isSplitChild()) {
            // optimization is only suitable for split parents
            return;
        }

        Interval registerHint = interval.locationHint(false, allocator);
        if (registerHint == null) {
            // cur is not the target of a move : otherwise registerHint would be set
            return;
        }
        assert registerHint.isSplitParent() : "register hint must be split parent";

        if (interval.spillState() != SpillState.NoOptimization || registerHint.spillState() != SpillState.NoOptimization) {
            // combining the stack slots for intervals where spill move optimization is applied
            // is not benefitial and would cause problems
            return;
        }

        int beginPos = interval.from();
        int endPos = interval.to();
        if (endPos > allocator.maxOpId() || isOdd(beginPos) || isOdd(endPos)) {
            // safety check that lirOpWithId is allowed
            return;
        }

        if (!isMove(allocator.instructionForId(beginPos), registerHint, interval) || !isMove(allocator.instructionForId(endPos), interval, registerHint)) {
            // cur and registerHint are not connected with two moves
            return;
        }

        Interval beginHint = registerHint.getSplitChildAtOpId(beginPos, LIRInstruction.OperandMode.Input, allocator);
        Interval endHint = registerHint.getSplitChildAtOpId(endPos, LIRInstruction.OperandMode.Output, allocator);
        if (beginHint == endHint || beginHint.to() != beginPos || endHint.from() != endPos) {
            // registerHint must be split : otherwise the re-writing of use positions does not work
            return;
        }

        assert beginHint.location() != null : "must have register assigned";
        assert endHint.location() == null : "must not have register assigned";
        assert interval.firstUsage(RegisterPriority.MustHaveRegister) == beginPos : "must have use position at begin of interval because of move";
        assert endHint.firstUsage(RegisterPriority.MustHaveRegister) == endPos : "must have use position at begin of interval because of move";

        if (beginHint.location().isRegister()) {
            // registerHint is not spilled at beginPos : so it would not be benefitial to immediately spill cur
            return;
        }
        assert registerHint.spillSlot() != null : "must be set when part of interval was spilled";

        // modify intervals such that cur gets the same stack slot as registerHint
        // delete use positions to prevent the intervals to get a register at beginning
        interval.setSpillSlot(registerHint.spillSlot());
        interval.removeFirstUsePos();
        endHint.removeFirstUsePos();
    }

    // allocate a physical register or memory location to an interval
    @Override
    boolean activateCurrent() {
        Interval interval = current;
        boolean result = true;

        if (GraalOptions.TraceLinearScanLevel >= 2) {
            TTY.println("+++++ activating interval " + interval.logString(allocator));
        }

        if (GraalOptions.TraceLinearScanLevel >= 4) {
            TTY.println("      splitParent: %s, insertMoveWhenActivated: %b", interval.splitParent().operandNumber, interval.insertMoveWhenActivated());
        }

        final CiValue operand = interval.operand;
        if (interval.location() != null && interval.location().isStackSlot()) {
            // activating an interval that has a stack slot assigned . split it at first use position
            // used for method parameters
            if (GraalOptions.TraceLinearScanLevel >= 4) {
                TTY.println("      interval has spill slot assigned (method parameter) . split it before first use");
            }
            splitStackInterval(interval);
            result = false;

        } else {
            if (operand.isVariable() && allocator.operands.mustStartInMemory((CiVariable) operand)) {
                assert interval.location() == null : "register already assigned";
                allocator.assignSpillSlot(interval);

                if (!allocator.operands.mustStayInMemory((CiVariable) operand)) {
                    // activating an interval that must start in a stack slot but may get a register later
                    // used for lirRoundfp: rounding is done by store to stack and reload later
                    if (GraalOptions.TraceLinearScanLevel >= 4) {
                        TTY.println("      interval must start in stack slot . split it before first use");
                    }
                    splitStackInterval(interval);
                }

                result = false;
            } else if (interval.location() == null) {
                // interval has not assigned register . normal allocation
                // (this is the normal case for most intervals)
                if (GraalOptions.TraceLinearScanLevel >= 4) {
                    TTY.println("      normal allocation of register");
                }

                // assign same spill slot to non-intersecting intervals
                combineSpilledIntervals(interval);

                initVarsForAlloc(interval);
                if (noAllocationPossible(interval) || !allocFreeRegister(interval)) {
                    // no empty register available.
                    // split and spill another interval so that this interval gets a register
                    allocLockedRegister(interval);
                }

                // spilled intervals need not be move to active-list
                if (!interval.location().isRegister()) {
                    result = false;
                }
            }
        }

        // load spilled values that become active from stack slot to register
        if (interval.insertMoveWhenActivated()) {
            assert interval.isSplitChild();
            assert interval.currentSplitChild() != null;
            assert interval.currentSplitChild().operand != operand : "cannot insert move between same interval";
            if (GraalOptions.TraceLinearScanLevel >= 4) {
                TTY.println("Inserting move from interval %d to %d because insertMoveWhenActivated is set", interval.currentSplitChild().operandNumber, interval.operandNumber);
            }

            insertMove(interval.from(), interval.currentSplitChild(), interval);
        }
        interval.makeCurrentSplitChild();

        return result; // true = interval is moved to active list
    }

    public void finishAllocation() {
        // must be called when all intervals are allocated
        moveResolver.resolveAndAppendMoves();
    }
}
