/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.MASK;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.lir.amd64.AMD64FrameMap;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;

/**
 * AMD64 HotSpot specific frame map.
 * <p>
 * The layout is basically the same as {@link AMD64FrameMap} except that space for rbp is reserved
 * at the standard location if {@link #preserveFramePointer} is false and a
 * {@link #deoptimizationRescueSlot} is always allocated. This is done to be consistent with
 * assumptions on HotSpot about frame layout. In particular, the nmethod entry barrier deoptimize
 * function in barrierSetNMethod_x86.cpp will manually tear down this frame so it needs to know the
 * location of the saved rbp. The extra spill slot for rbp is only written to if rbp is actually
 * used by the register allocator.
 *
 * <pre>
 *   Base       Contents
 *
 *            :                                :  -----
 *   caller   | incoming overflow argument n   |    ^
 *   frame    :     ...                        :    | positive
 *            | incoming overflow argument 0   |    | offsets
 *   ---------+--------------------------------+---------------------
 *   current  | return address                 |    |            ^
 *   frame    +--------------------------------+    |            |
 *            | preserved rbp                  |    |            |
 *            | iff preserveFramePointer       |    |            |
 *            +--------------------------------+    |            |    -----
 *            | preserved rbp                  |    |            |      ^
 *            | iff not preserveFramePointer   |    |            |      |
 *            +--------------------------------+    |            |      |
 *            | deopt rescue slot              |    |            |      |
 *            +--------------------------------+    |            |      |
 *            |                                |    |            |      |
 *            : callee save area               :    |            |      |
 *            |                                |    |            |      |
 *            +--------------------------------+    |            |      |
 *            | spill slot 0                   |    | negative   |      |
 *            :     ...                        :    v offsets    |      |
 *            | spill slot n                   |  -----        total  frame
 *            +--------------------------------+               frame  size
 *            | alignment padding              |               size     |
 *            +--------------------------------+  -----          |      |
 *            | outgoing overflow argument n   |    ^            |      |
 *            :     ...                        :    | positive   |      |
 *            | outgoing overflow argument 0   |    | offsets    v      v
 *    %sp--&gt;  +--------------------------------+---------------------------
 *
 * </pre>
 */
public class AMD64HotSpotFrameMap extends AMD64FrameMap {
    /**
     * The spill slot for rbp if {@link #preserveFramePointer} )is false.
     */
    private StackSlot rbpSpillSlot;

    /**
     * The deoptimization rescue slot.
     */
    private StackSlot deoptimizationRescueSlot;

    @SuppressWarnings("this-escape")
    public AMD64HotSpotFrameMap(CodeCacheProvider codeCache, RegisterConfig registerConfig, ReferenceMapBuilderFactory referenceMapFactory, boolean preserveFramePointer) {
        super(codeCache, registerConfig, referenceMapFactory, preserveFramePointer);
        // HotSpot is picky about the frame layout in the presence of nmethod entry barriers, so
        // always allocate the space for rbp and the deoptimization rescue slot. If we don't
        // allocate rbp the rbp spill slot will never be written.
        if (!preserveFramePointer()) {
            assert spillSize == initialSpillSize : "RBP spill slot must be the first allocated stack slots";
            rbpSpillSlot = allocateSpillSlot(LIRKind.value(AMD64Kind.QWORD));
            assert asStackSlot(rbpSpillSlot).getRawOffset() == -16 : asStackSlot(rbpSpillSlot).getRawOffset();
        }
        deoptimizationRescueSlot = allocateSpillSlot(LIRKind.value(AMD64Kind.QWORD));
    }

    @Override
    public int offsetForStackSlot(StackSlot slot) {
        int offset = super.offsetForStackSlot(slot);
        // rbp is always saved in the standard location if it is saved
        assert !slot.equals(rbpSpillSlot) || offset - totalFrameSize() == -16 : Assertions.errorMessage(slot, offset);
        return offset;
    }

    public StackSlot getRBPSpillSlot() {
        assert rbpSpillSlot != null;
        return rbpSpillSlot;
    }

    public StackSlot getDeoptimizationRescueSlot() {
        assert deoptimizationRescueSlot != null;
        return deoptimizationRescueSlot;
    }

    @Override
    protected Register[] filterSavedRegisters(Register[] savedRegisters) {
        Register[] filtered = null;
        for (int i = 0; i < savedRegisters.length; i++) {
            Register reg = savedRegisters[i];
            if (reg == null) {
                continue;
            }
            if (reg.getRegisterCategory().equals(MASK)) {
                // These can't appear in HotSpot debug info
                if (filtered == null) {
                    filtered = savedRegisters.clone();
                }
                filtered[i] = null;
            }
        }
        return filtered != null ? filtered : savedRegisters;
    }
}
