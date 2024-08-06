/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.hotspot;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.STACK;

import java.util.Arrays;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.framemap.FrameMapBuilder;

import jdk.vm.ci.meta.AllocatableValue;

/**
 * Manages allocation and re-use of lock slots in a scoped manner. The slots are used in HotSpot's
 * lightweight locking mechanism to store the mark word of an object being locked.
 */
public class HotSpotLockStack extends LIRInstruction {
    public static final LIRInstructionClass<HotSpotLockStack> TYPE = LIRInstructionClass.create(HotSpotLockStack.class);
    private static final AllocatableValue[] EMPTY = new AllocatableValue[0];

    @Def({STACK}) private AllocatableValue[] locks;
    private final FrameMapBuilder frameMapBuilder;
    private final LIRKind slotKind;

    public HotSpotLockStack(FrameMapBuilder frameMapBuilder, LIRKind slotKind) {
        super(TYPE);
        this.frameMapBuilder = frameMapBuilder;
        this.slotKind = slotKind;
        this.locks = EMPTY;
    }

    /**
     * Gets a stack slot for a lock at a given lock nesting depth, allocating it first if necessary.
     */
    public VirtualStackSlot makeLockSlot(int lockDepth) {
        int oldLength = locks.length;
        if (locks == EMPTY) {
            locks = new AllocatableValue[lockDepth + 1];
        } else if (locks.length < lockDepth + 1) {
            locks = Arrays.copyOf(locks, lockDepth + 1);
        }
        /*
         * After optimizations that eliminate locking, lock depths are not necessarily contiguous.
         * For example, a method may contain locks at depths 0, 1, and 3, with locks at depth 2
         * optimized out. The locks array must not contain holes because null values are not allowed
         * in LIR. When we see a new lock depth, we must therefore ensure allocation of slots for
         * all lower depths as well.
         *
         * Non-contiguous lock depths are very rare, so this will almost never create superfluous
         * lock slots. In addition, any superfluous virtual slots are not used in the rest of the
         * compilation unit, so stack slot allocation can share their physical location with other
         * slots.
         */
        for (int newLockIndex = oldLength; newLockIndex < lockDepth + 1; newLockIndex++) {
            locks[newLockIndex] = frameMapBuilder.allocateSpillSlot(slotKind);
        }
        return (VirtualStackSlot) locks[lockDepth];
    }

    @Override
    public void emitCode(CompilationResultBuilder crb) {
        // do nothing
    }
}
