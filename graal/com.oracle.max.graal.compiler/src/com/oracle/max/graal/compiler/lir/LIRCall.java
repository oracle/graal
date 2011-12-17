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
package com.oracle.max.graal.compiler.lir;

import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.Mark;
import com.sun.cri.ci.CiValue.Formatter;
import com.sun.cri.ri.*;
import com.sun.cri.xir.CiXirAssembler.XirMark;

/**
 * This class represents a call instruction; either to a {@linkplain CiRuntimeCall runtime method},
 * a {@linkplain RiMethod Java method}, a native function or a global stub.
 */
public abstract class LIRCall extends LIRInstruction {

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

    private static CiValue[] toArray(List<CiValue> arguments, CiValue targetAddress) {
        CiValue[] result = new CiValue[arguments.size() + (targetAddress != null ? 1 : 0)];
        arguments.toArray(result);
        if (targetAddress != null) {
            result[arguments.size()] = targetAddress;
        }
        return result;
    }

    public LIRCall(LIROpcode opcode,
                   Object target,
                   CiValue result,
                   List<CiValue> arguments,
                   CiValue targetAddress,
                   LIRDebugInfo info,
                   Map<XirMark, Mark> marks) {
        super(opcode, result, info, toArray(arguments, targetAddress), LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        this.marks = marks;
        if (targetAddress == null) {
            this.targetAddressIndex = -1;
        } else {
            // The last argument is the operand holding the address for the indirect call
            assert inputs.length - 1 == arguments.size();
            this.targetAddressIndex = arguments.size();
        }
        this.target = target;
    }

    public RiMethod method() {
        return (RiMethod) target;
    }

    public CiRuntimeCall runtimeCall() {
        return (CiRuntimeCall) target;
    }

    public CiValue targetAddress() {
        if (targetAddressIndex >= 0) {
            return input(targetAddressIndex);
        }
        return null;
    }

    @Override
    public String operationString(Formatter operandFmt) {
        StringBuilder buf = new StringBuilder();
        if (result.isLegal()) {
            buf.append(operandFmt.format(result)).append(" = ");
        }
        if (targetAddressIndex >= 0) {
            buf.append(operandFmt.format(targetAddress()));
        }
        if (inputs.length + alives.length > 1) {
            buf.append("(");
        }
        String sep = "";
        for (CiValue input : inputs) {
            if (input != targetAddress()) {
                buf.append(sep).append(operandFmt.format(input));
                sep = ", ";
            }
        }
        for (CiValue input : alives) {
            if (input != targetAddress()) {
                buf.append(sep).append(operandFmt.format(input)).append(" ~");
                sep = ", ";
            }
        }
        if (inputs.length + alives.length > 1) {
            buf.append(")");
        }
        return buf.toString();
    }
}
