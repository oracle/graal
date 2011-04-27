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
package com.sun.c1x.lir;

import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.Mark;
import com.sun.cri.ri.*;
import com.sun.cri.xir.CiXirAssembler.XirMark;

/**
 * This class represents a call instruction; either to a {@linkplain CiRuntimeCall runtime method},
 * a {@linkplain RiMethod Java method}, a native function or a global stub.
 *
 * @author Marcelo Cintra
 */
public class LIRCall extends LIRInstruction {

    /**
     * The target of the call. This will be a {@link CiRuntimeCall}, {@link RiMethod} or {@link CiValue}
     * object denoting a call to the runtime, a Java method or a native function respectively.
     */
    public final Object target;
    /**
     * The call site needs to be marked if this is non-null.
     */
    public final Map<XirMark, Mark> marks;

    private final int targetAddressIndex;

    public final List<CiValue> pointerSlots;


    private static CiValue[] toArray(List<CiValue> arguments) {
        return arguments.toArray(new CiValue[arguments.size()]);
    }

    public LIRCall(LIROpcode opcode,
                   Object target,
                   CiValue result,
                   List<CiValue> arguments,
                   LIRDebugInfo info,
                   Map<XirMark, Mark> marks,
                   boolean calleeSaved,
                   List<CiValue> pointerSlots) {
        super(opcode, result, info, !calleeSaved, 0, 0, toArray(arguments));
        this.marks = marks;
        this.pointerSlots = pointerSlots;
        if (opcode == LIROpcode.DirectCall) {
            this.targetAddressIndex = -1;
        } else {
            // The last argument is the operand holding the address for the indirect call
            this.targetAddressIndex = arguments.size() - 1;
        }
        this.target = target;
    }

    /**
     * Emits target assembly code for this instruction.
     *
     * @param masm the target assembler
     */
    @Override
    public void emitCode(LIRAssembler masm) {
        masm.emitCall(this);
    }

    /**
     * Returns the receiver for this method call.
     * @return the receiver
     */
    public CiValue receiver() {
        return operand(0);
    }

    public RiMethod method() {
        return (RiMethod) target;
    }

    public CiRuntimeCall runtimeCall() {
        return (CiRuntimeCall) target;
    }

    public CiValue targetAddress() {
        if (targetAddressIndex >= 0) {
            return operand(targetAddressIndex);
        }
        return null;
    }

    @Override
    public String operationString(OperandFormatter operandFmt) {
        StringBuilder buf = new StringBuilder();
        if (result().isLegal()) {
            buf.append(operandFmt.format(result())).append(" = ");
        }
        String targetAddress = null;
        if (code == LIROpcode.RuntimeCall) {
            buf.append(target);
        } else if (code != LIROpcode.DirectCall && code != LIROpcode.ConstDirectCall) {
            if (targetAddressIndex >= 0) {
                targetAddress = operandFmt.format(targetAddress());
                buf.append(targetAddress);
            }
        }
        buf.append('(');
        boolean first = true;
        for (LIROperand operandSlot : operands) {
            String operand = operandFmt.format(operandSlot.value(this));
            if (!operand.isEmpty() && !operand.equals(targetAddress)) {
                if (!first) {
                    buf.append(", ");
                } else {
                    first = false;
                }
                buf.append(operand);
            }
        }
        buf.append(')');
        return buf.toString();
    }
}
