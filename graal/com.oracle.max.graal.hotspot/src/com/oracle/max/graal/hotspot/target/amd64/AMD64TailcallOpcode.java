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
package com.oracle.max.graal.hotspot.target.amd64;

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.target.amd64.*;
import com.oracle.max.graal.compiler.util.*;

/**
 * Performs a hard-coded tail call to the specified target, which normally should be an RiCompiledCode instance.
 */
public enum AMD64TailcallOpcode implements LIROpcode {
    TAILCALL;

    public LIRInstruction create(List<CiValue> parameters, CiValue target, CiValue[] callingConvention) {
        CiValue[] inputs = new CiValue[parameters.size() + 1];
        parameters.toArray(inputs);
        inputs[parameters.size()] = target;
        CiValue[] temps = callingConvention.clone();
        assert inputs.length == temps.length + 1;

        return new AMD64LIRInstruction(this, LIRInstruction.NO_OPERANDS, null, inputs, LIRInstruction.NO_OPERANDS, temps) {
            @Override
            public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                emit(tasm, masm, inputs, temps);
            }
        };
    }

    private void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue[] inputs, CiValue[] temps) {
        switch (this) {
            case TAILCALL: {
                // move all parameters to the correct positions, according to the calling convention
                // TODO: These moves should not be part of the TAILCALL opcode, but emitted as separate MOVE instructions before.
                for (int i = 0; i < inputs.length - 1; i++) {
                    assert inputs[i].kind == CiKind.Object || inputs[i].kind == CiKind.Int || inputs[i].kind == CiKind.Long : "only Object, int and long supported for now";
                    assert isRegister(temps[i]) : "too many parameters";
                    if (isRegister(inputs[i])) {
                        if (inputs[i] != temps[i]) {
                            masm.movq(asRegister(temps[i]), asRegister(inputs[i]));
                        }
                    } else {
                        masm.movq(asRegister(temps[i]), tasm.asAddress(inputs[i]));
                    }
                }
                // destroy the current frame (now the return address is the top of stack)
                masm.leave();

                // jump to the target method
                masm.jmp(asRegister(inputs[inputs.length - 1]));
                masm.ensureUniquePC();
                break;
            }
            default:   throw Util.shouldNotReachHere();
        }
    }
}
