/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen.cisc.x86;

import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.cisc.*;

/**
 * Used when pre-scanning instruction descriptions to assess variants within the respective instruction family.
 *
 * @see InstructionAssessment
 */
public class OpcodeAssessor extends X86InstructionDescriptionAdapter {

    private final InstructionAssessment instructionFamily;

    public OpcodeAssessor(InstructionAssessment instructionFamily) {
        this.instructionFamily = instructionFamily;
    }

    @Override
    public void visitOperandCode(OperandCode operandCode, X86Operand.Designation designation, ArgumentRange argumentRange, TestArgumentExclusion testArgumentExclusion) {
        switch (operandCode.operandTypeCode()) {
            case a:
            case d_q:
            case p:
            case s:
            case v:
            case z:
                instructionFamily.haveOperandSizeVariants();
                break;
            default:
                break;
        }
        switch (operandCode.addressingMethodCode()) {
            case A:
            case E:
            case M:
            case O:
            case Q:
            case W:
                instructionFamily.haveAddressSizeVariants();
                break;
            default:
                break;
        }
        switch (operandCode.addressingMethodCode()) {
            case C:
            case D:
            case E:
            case G:
            case M:
            case P:
            case PR:
            case Q:
            case R:
            case S:
            case V:
            case VR:
            case T:
            case W:
                instructionFamily.haveModRMByte();
                break;
            default:
                break;
        }
    }

    @Override
    public void visitRegisterOperandCode(RegisterOperandCode registerOperandCode, X86Operand.Designation position, ImplicitOperand.ExternalPresence externalPresence) {
        instructionFamily.haveOperandSizeVariants();
    }

    @Override
    public void visitModRMGroup(ModRMGroup modRMGroup) {
        instructionFamily.setModRMGroup(modRMGroup);
    }

    @Override
    public void visitModCase(X86TemplateContext.ModCase modCase) throws TemplateNotNeededException {
        instructionFamily.haveModRMByte();
    }

    @Override
    public void visitString(String s) {
        if (s.startsWith("J") || s.startsWith("j")) {
            instructionFamily.beJump();
        }
    }
}
