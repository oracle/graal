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
package com.oracle.max.graal.compiler.lir;

import java.util.*;

import com.oracle.max.cri.ci.CiTargetMethod.Mark;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.xir.CiXirAssembler.XirMark;
import com.oracle.max.cri.xir.*;
import com.oracle.max.graal.compiler.asm.*;

public class StandardOpcode {
    // Checkstyle: stop
    public static MoveOpcode MOVE;
    public static NullCheckOpcode NULL_CHECK;
    public static CallOpcode DIRECT_CALL;
    public static CallOpcode INDIRECT_CALL;
    public static LIROpcode LABEL;
    public static LIROpcode JUMP;
    public static ReturnOpcode RETURN;
    public static XirOpcode XIR;
    public static ParametersOpcode PARAMS = ParametersOpcode.PARAMS;
    // Checkstyle: resume

    public interface MoveOpcode extends LIROpcode {
        LIRInstruction create(CiValue result, CiValue input);
    }

    public interface NullCheckOpcode extends LIROpcode {
        LIRInstruction create(Variable input, LIRDebugInfo info);
    }

    public interface CallOpcode extends LIROpcode {
        LIRInstruction create(Object target, CiValue result, List<CiValue> arguments, CiValue targetAddress, LIRDebugInfo info, Map<XirMark, Mark> marks);
    }

    public interface ReturnOpcode extends LIROpcode {
        LIRInstruction create(CiValue input);
    }

    public interface XirOpcode extends LIROpcode {
        LIRInstruction create(XirSnippet snippet, CiValue[] operands, CiValue outputOperand, CiValue[] inputs, CiValue[] temps, int[] inputOperandIndices, int[] tempOperandIndices, int outputOperandIndex,
                        LIRDebugInfo info, LIRDebugInfo infoAfter, RiMethod method);
    }


    public enum ParametersOpcode implements LIROpcode {
        @SuppressWarnings("hiding")
        PARAMS;

        public LIRInstruction create(CiValue[] params) {
            return new LIRInstruction(this, params, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm) {
                    // No code to emit. This is not the actual method prologue, but only a meta-instruction that defines the incoming method parameters.
                }
            };
        }
    }
}
