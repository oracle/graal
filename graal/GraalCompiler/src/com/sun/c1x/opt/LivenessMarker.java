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
package com.sun.c1x.opt;

import static com.sun.c1x.ir.Value.Flag.*;

import com.sun.c1x.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.value.*;

/**
 * The {@code LivenessMarker} class walks over an IR graph and marks instructions
 * whose values are live, either because they are needed to compute the method's result,
 * may produce a side-effect, or are needed for deoptimization.
 *
 * @author Ben L. Titzer
 */
public final class LivenessMarker {

    final IR ir;

    final InstructionMarker deoptMarker = new InstructionMarker(LiveDeopt);
    final InstructionMarker valueMarker = new InstructionMarker(LiveValue);

    int count;

    /**
     * Creates a new liveness marking instance and marks live instructions.
     * @param ir the IR to mark
     */
    public LivenessMarker(IR ir) {
        this.ir = ir;
        markRoots();
    }

    private void markRoots() {
        // first pass: mark root instructions and their inputs
        ir.startBlock.iteratePreOrder(new BlockClosure() {
            public void apply(BlockBegin block) {
                block.stateBefore().valuesDo(deoptMarker);
                if (block.stateAfter() != null) {
                    block.stateAfter().valuesDo(deoptMarker);
                }
                Instruction i = block;
                while ((i = i.next()) != null) {
                    // visit all instructions first, marking control dependent and side-effects
                    markRootInstr(i);
                }
            }
        });

        // propagate liveness flags to inputs of instructions
        valueMarker.markAll();
        deoptMarker.markAll();
    }

    public int liveCount() {
        return count;
    }

    public void removeDeadCode() {
        // second pass: remove dead instructions from blocks
        ir.startBlock.iteratePreOrder(new BlockClosure() {
            public void apply(BlockBegin block) {
                Instruction prev = block;
                Instruction i = block.next();
                while (i != null) {
                    if (i.isLive()) {
                        prev.resetNext(i); // skip any previous dead instructions
                        prev = i;
                    } else {
                        C1XMetrics.DeadCodeEliminated++;
                    }
                    i = i.next();
                }
            }
        });
        // clear all marks on all instructions
        valueMarker.clearAll();
        deoptMarker.clearAll();
    }

    private static class Link {
        final Value value;
        Link next;

        Link(Value v) {
            this.value = v;
        }
    }

    private final class InstructionMarker implements ValueClosure {
        final Value.Flag reason;
        Link head;
        Link tail;

        public InstructionMarker(Value.Flag reason) {
            this.reason = reason;
        }

        public Value apply(Value i) {
            if (!i.checkFlag(reason) && !i.isDeadPhi()) {
                // set the flag and add to the queue
                setFlag(i, reason);
                if (head == null) {
                    head = tail = new Link(i);
                } else {
                    tail.next = new Link(i);
                    tail = tail.next;
                }
            }
            return i;
        }

        private void markAll() {
            Link cursor = head;
            while (cursor != null) {
                markInputs(cursor.value);
                cursor = cursor.next;
            }
        }

        private void clearAll() {
            Link cursor = head;
            while (cursor != null) {
                cursor.value.clearLive();
                cursor = cursor.next;
            }
        }

        private void markInputs(Value i) {
            if (!i.isDeadPhi()) {
                i.inputValuesDo(this);
                if (i instanceof Phi) {
                    // phis are special
                    Phi phi = (Phi) i;
                    int max = phi.inputCount();
                    for (int j = 0; j < max; j++) {
                        apply(phi.inputAt(j));
                    }
                }
            }
        }
    }

    void markRootInstr(Instruction i) {
        FrameState stateBefore = i.stateBefore();
        if (stateBefore != null) {
            // stateBefore != null implies that this instruction may have side effects
            stateBefore.valuesDo(deoptMarker);
            i.inputValuesDo(valueMarker);
            setFlag(i, LiveSideEffect);
        } else if (i.checkFlag(LiveStore)) {
            // instruction is a store that cannot be eliminated
            i.inputValuesDo(valueMarker);
            setFlag(i, LiveSideEffect);
        } else if (i.checkFlag(LiveSideEffect)) {
            // instruction has a side effect
            i.inputValuesDo(valueMarker);
        }
        if (i instanceof BlockEnd) {
            // input values to block ends are control dependencies
            i.inputValuesDo(valueMarker);
            setFlag(i, LiveControl);
        }
        FrameState stateAfter = i.stateAfter();
        if (stateAfter != null) {
            stateAfter.valuesDo(deoptMarker);
        }
    }

    void setFlag(Value i, Value.Flag flag) {
        if (!i.isLive()) {
            count++;
        }
        i.setFlag(flag);
    }
}
