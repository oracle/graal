/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.alloc.Interval.RegisterBinding;
import com.oracle.graal.compiler.alloc.Interval.RegisterBindingLists;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.options.*;

public class OptimizingLinearScanWalker extends LinearScanWalker {

    public static class Options {
        // @formatter:off
        @Option(help = "Enable LSRA optimization")
        public static final OptionValue<Boolean> LSRAOptimization = new OptionValue<>(false);
        // @formatter:on
    }

    OptimizingLinearScanWalker(LinearScan allocator, Interval unhandledFixedFirst, Interval unhandledAnyFirst) {
        super(allocator, unhandledFixedFirst, unhandledAnyFirst);
    }

    @Override
    protected void handleSpillSlot(Interval interval) {
        assert interval.location() != null : "interval  not assigned " + interval;
        if (interval.canMaterialize()) {
            assert !isStackSlot(interval.location()) : "interval can materialize but assigned to a stack slot " + interval;
            return;
        }
        assert isStackSlot(interval.location()) : "interval not assigned to a stack slot " + interval;
        try (Scope s1 = Debug.scope("LSRAOptimization")) {
            Debug.log("adding stack to unhandled list %s", interval);
            unhandledLists.addToListSortedByStartAndUsePositions(RegisterBinding.Stack, interval);
        }
    }

    @SuppressWarnings("unused")
    private static void printRegisterBindingList(RegisterBindingLists list, RegisterBinding binding) {
        for (Interval interval = list.get(binding); interval != Interval.EndMarker; interval = interval.next) {
            Debug.log("%s", interval);
        }
    }

    @Override
    void walk() {
        try (Scope s = Debug.scope("OptimizingLinearScanWalker")) {
            for (AbstractBlock<?> block : allocator.sortedBlocks) {
                int nextBlock = allocator.getFirstLirInstructionId(block);
                try (Scope s1 = Debug.scope("LSRAOptimization")) {
                    Debug.log("next block: %s (%d)", block, nextBlock);
                }
                try (Indent indent0 = Debug.indent()) {
                    walkTo(nextBlock);

                    try (Scope s1 = Debug.scope("LSRAOptimization")) {
                        try (Indent indent1 = Debug.logAndIndent("Active intervals: (block %s [%d])", block, nextBlock)) {
                            for (Interval active = activeLists.get(RegisterBinding.Any); active != Interval.EndMarker; active = active.next) {
                                Debug.log("active   (any): %s", active);
                                optimize(nextBlock, block, active, RegisterBinding.Any);
                            }
                            for (Interval active = activeLists.get(RegisterBinding.Stack); active != Interval.EndMarker; active = active.next) {
                                Debug.log("active (stack): %s", active);
                                optimize(nextBlock, block, active, RegisterBinding.Stack);
                            }

                        }
                    }
                }
            }
        }
        super.walk();
    }

    private void optimize(int currentPos, AbstractBlock<?> currentBlock, Interval currentInterval, RegisterBinding binding) {
        // BEGIN initialize and sanity checks
        assert currentBlock != null : "block must not be null";
        assert currentInterval != null : "interval must not be null";

        if (currentBlock.getPredecessorCount() != 1) {
            // more than one predecessors -> optimization not possible
            return;
        }
        if (!currentInterval.isSplitChild()) {
            // interval is not a split child -> no need for optimization
            return;
        }

        if (currentInterval.from() == currentPos) {
            // the interval starts at the current position so no need for splitting
            return;
        }

        // get current location
        AllocatableValue currentLocation = currentInterval.location();
        assert currentLocation != null : "active intervals must have a location assigned!";

        // get predecessor stuff
        AbstractBlock<?> predecessorBlock = currentBlock.getPredecessors().get(0);
        int predEndId = allocator.getLastLirInstructionId(predecessorBlock);
        Interval predecessorInterval = currentInterval.getIntervalCoveringOpId(predEndId);
        assert predecessorInterval != null : "variable not live at the end of the only predecessor! " + predecessorBlock + " -> " + currentBlock + " interval: " + currentInterval;
        AllocatableValue predecessorLocation = predecessorInterval.location();
        assert predecessorLocation != null : "handled intervals must have a location assigned!";

        // END initialize and sanity checks

        if (currentLocation.equals(predecessorLocation)) {
            // locations are already equal -> nothing to optimize
            return;
        }

        if (!isStackSlot(predecessorLocation) && !isRegister(predecessorLocation)) {
            assert predecessorInterval.canMaterialize();
            // value is materialized -> no need for optimization
            return;
        }

        assert isStackSlot(currentLocation) || isRegister(currentLocation) : "current location not a register or stack slot " + currentLocation;

        try (Indent indent = Debug.logAndIndent("location differs: %s vs. %s", predecessorLocation, currentLocation)) {
            // split current interval at current position
            Debug.log("splitting at position %d", currentPos);

            assert allocator.isBlockBegin(currentPos) && ((currentPos & 1) == 0) : "split pos must be even when on block boundary";

            Interval splitPart = currentInterval.split(currentPos, allocator);

            assert splitPart.from() >= currentPosition : "cannot append new interval before current walk position";
            unhandledLists.addToListSortedByStartAndUsePositions(RegisterBinding.Any, splitPart);
            activeLists.remove(binding, currentInterval);

            // the currentSplitChild is needed later when moves are inserted for reloading
            assert splitPart.currentSplitChild() == currentInterval : "overwriting wrong currentSplitChild";
            splitPart.makeCurrentSplitChild();

            if (Debug.isLogEnabled()) {
                Debug.log("left interval  : %s", currentInterval.logString(allocator));
                Debug.log("right interval : %s", splitPart.logString(allocator));
            }
        }

    }
}
