/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.target.amd64;

import static com.oracle.graal.hotspot.ri.HotSpotXirGenerator.*;
import static com.oracle.max.asm.target.amd64.AMD64.*;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;

/**
 * Emits a safepoint poll.
 */
public class AMD64SafepointOp extends AMD64LIRInstruction {

    private final HotSpotVMConfig config;

    public AMD64SafepointOp(LIRDebugInfo info, HotSpotVMConfig config) {
        super("SAFEPOINT", LIRInstruction.NO_OPERANDS, info, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        this.config = config;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler asm) {
        CiRegister scratch = tasm.frameMap.registerConfig.getScratchRegister();
        int pos = asm.codeBuffer.position();
        if (config.isPollingPageFar) {
            asm.movq(scratch, config.safepointPollingAddress);
            tasm.recordMark(MARK_POLL_FAR);
            tasm.recordSafepoint(pos, info);
            asm.movq(scratch, new CiAddress(tasm.target.wordKind, scratch.asValue()));
        } else {
            tasm.recordMark(MARK_POLL_NEAR);
            tasm.recordSafepoint(pos, info);
            asm.movq(scratch, new CiAddress(tasm.target.wordKind, rip.asValue()));
        }
    }

    @Override
    protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
        throw GraalInternalError.shouldNotReachHere();
    }
}
