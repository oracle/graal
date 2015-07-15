/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.framemap.*;

/**
 * Manages allocation and re-use of lock slots in a scoped manner. The slots are used in HotSpot's
 * lightweight locking mechanism to store the mark word of an object being locked.
 */
public class HotSpotLockStack extends LIRInstruction {
    public static final LIRInstructionClass<HotSpotLockStack> TYPE = LIRInstructionClass.create(HotSpotLockStack.class);
    private static final StackSlotValue[] EMPTY = new StackSlotValue[0];

    @Def({STACK}) private StackSlotValue[] locks;
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
    public StackSlotValue makeLockSlot(int lockDepth) {
        if (locks == EMPTY) {
            locks = new StackSlotValue[lockDepth + 1];
        } else if (locks.length < lockDepth + 1) {
            locks = Arrays.copyOf(locks, lockDepth + 1);
        }
        if (locks[lockDepth] == null) {
            locks[lockDepth] = frameMapBuilder.allocateSpillSlot(slotKind);
        }
        return locks[lockDepth];
    }

    @Override
    public void emitCode(CompilationResultBuilder crb) {
        // do nothing
    }
}
