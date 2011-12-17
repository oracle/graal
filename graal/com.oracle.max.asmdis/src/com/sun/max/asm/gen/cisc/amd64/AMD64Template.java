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
package com.sun.max.asm.gen.cisc.amd64;

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.amd64.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.cisc.*;
import com.sun.max.asm.gen.cisc.x86.*;
import com.sun.max.asm.x86.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 */
public class AMD64Template extends X86Template {

    public AMD64Template(X86InstructionDescription instructionDescription, int serial, InstructionAssessment instructionFamily, X86TemplateContext context) {
        super(instructionDescription, serial, instructionFamily, context);
    }

    private void addSib(X86Operand.Designation designation) throws TemplateNotNeededException {
        assert context().addressSizeAttribute() != WordWidth.BITS_16;
        haveSibByte();
        switch (context().sibBaseCase()) {
            case GENERAL_REGISTER: { // base register
                switch (context().addressSizeAttribute()) {
                    case BITS_32:
                        setExternalCodeSizeAttribute(context().addressSizeAttribute());
                        addEnumerableParameter(designation, ParameterPlace.SIB_BASE, AMD64BaseRegister32.ENUMERATOR);
                        break;
                    case BITS_64:
                        addEnumerableParameter(designation, ParameterPlace.SIB_BASE_REXB, AMD64BaseRegister64.ENUMERATOR);
                        break;
                    default:
                        throw ProgramError.unexpected();
                }
                break;
            }
            case SPECIAL: { // displacement based specified
                switch (context().modCase()) {
                    case MOD_0: {
                        setExternalCodeSizeAttribute(context().addressSizeAttribute());
                        /* [disp32 + index]
                         * Although this could be a label parameter, we disable it for now due to complications in the assembler and
                         * the fact that this instruction may not be very useful. This 32-bit displacement base is an absolute pointer to
                         * memory, therefore it cannot address memory above 4GB. However, the assembler currently only supports
                         * constraints for user-specified parameters and not constraints on label parameters that need to be checked at
                         * assemble time.
                         */
                        //setLabelParameterIndex();
                        addParameter(new X86AddressParameter(designation, WordWidth.BITS_32));
                        break;
                    }
                    default: {
                        throw ProgramError.unexpected("no special SIB base for mod != 0");
                    }
                }
                break;
            }
        }
        switch (context().sibIndexCase()) {
            case GENERAL_REGISTER:
                switch (context().addressSizeAttribute()) {
                    case BITS_32:
                        setExternalCodeSizeAttribute(context().addressSizeAttribute());
                        addEnumerableParameter(designation, ParameterPlace.SIB_INDEX, AMD64IndexRegister32.ENUMERATOR);
                        break;
                    case BITS_64:
                        addEnumerableParameter(designation, ParameterPlace.SIB_INDEX_REXX, AMD64IndexRegister64.ENUMERATOR);
                        break;
                    default:
                        ProgramError.unexpected();
                }
                break;
            case NONE:
                // Our external assembler (gas) cannot generate these cases and they seem redundant anyway,
                // so for now we do not produce them:
                TemplateNotNeededException.raise();
        }
        addParameter(new X86EnumerableParameter<Scale>(designation, ParameterPlace.SIB_SCALE, Scale.ENUMERATOR));
    }

    /**
     * Populate templates for ModRM Memory Reference operands. See "Table A-13. ModRM Memory References, 16-Bit Addressing" and
     * "Table A-15. ModRM Memory References, 32-Bit and 64-Bit Addressing".
     */
    @Override
    protected void organize_M(X86Operand.Designation designation) throws TemplateNotNeededException {
        switch (context().modCase()) {
            case MOD_0: {
                switch (context().rmCase()) {
                    case NORMAL:
                        switch (context().addressSizeAttribute()) {
                            case BITS_32:
                                setExternalCodeSizeAttribute(context().addressSizeAttribute());
                                addEnumerableParameter(designation, ParameterPlace.MOD_RM_REXB, AMD64IndirectRegister32.ENUMERATOR);
                                break;
                            case BITS_64:
                                addEnumerableParameter(designation, ParameterPlace.MOD_RM_REXB, AMD64IndirectRegister64.ENUMERATOR);
                                break;
                            default:
                                throw ProgramError.unexpected();
                        }
                        break;
                    case SWORD:
                        switch (context().addressSizeAttribute()) {
                            case BITS_16:
                                addParameter(new X86AddressParameter(designation, WordWidth.BITS_16));
                                break;
                            default:
                                TemplateNotNeededException.raise();
                        }
                        break;
                    case SDWORD:
                        switch (context().addressSizeAttribute()) {
                            case BITS_64:
                                useNamePrefix("rip_");
                                setLabelParameterIndex();
                                addParameter(new X86OffsetParameter(designation, WordWidth.BITS_32));
                                break;
                            case BITS_32:
                                setExternalCodeSizeAttribute(context().addressSizeAttribute());
                                addParameter(new X86AddressParameter(designation, WordWidth.BITS_32));
                                break;
                            default:
                                TemplateNotNeededException.raise();
                        }
                        break;
                    case SIB:
                        addSib(designation);
                        break;
                }
                break;
            }
            case MOD_1: {
                addParameter(new X86DisplacementParameter(designation, WordWidth.BITS_8));
                switch (context().rmCase()) {
                    case NORMAL:
                        switch (context().addressSizeAttribute()) {
                            case BITS_32:
                                setExternalCodeSizeAttribute(context().addressSizeAttribute());
                                addEnumerableParameter(designation, ParameterPlace.MOD_RM_REXB, AMD64IndirectRegister32.ENUMERATOR);
                                break;
                            case BITS_64:
                                addEnumerableParameter(designation, ParameterPlace.MOD_RM_REXB, AMD64IndirectRegister64.ENUMERATOR);
                                break;
                            default:
                                throw ProgramError.unexpected();
                        }
                        break;
                    case SIB:
                        addSib(designation);
                        break;
                    default:
                        TemplateNotNeededException.raise();
                }
                break;
            }
            case MOD_2: {
                switch (context().addressSizeAttribute()) {
                    case BITS_16:
                        addParameter(new X86DisplacementParameter(designation, WordWidth.BITS_16));
                        break;
                    case BITS_32:
                    case BITS_64:
                        addParameter(new X86DisplacementParameter(designation, WordWidth.BITS_32));
                        break;
                    default:
                        throw ProgramError.unexpected();
                }
                switch (context().rmCase()) {
                    case NORMAL:
                        switch (context().addressSizeAttribute()) {
                            case BITS_32:
                                setExternalCodeSizeAttribute(context().addressSizeAttribute());
                                addEnumerableParameter(designation, ParameterPlace.MOD_RM_REXB, AMD64IndirectRegister32.ENUMERATOR);
                                break;
                            case BITS_64:
                                addEnumerableParameter(designation, ParameterPlace.MOD_RM_REXB, AMD64IndirectRegister64.ENUMERATOR);
                                break;
                            default:
                                throw ProgramError.unexpected();
                        }
                        break;
                    case SIB:
                        addSib(designation);
                        break;
                    default:
                        TemplateNotNeededException.raise();
                }
                break;
            }
            case MOD_3: {
                TemplateNotNeededException.raise();
            }
        }
    }

    public void visitOperandCode(OperandCode operandCode, X86Operand.Designation designation, ArgumentRange argumentRange, TestArgumentExclusion testArgumentExclusion)
        throws TemplateNotNeededException {
        switch (operandCode) {
            case Cq: {
                addParameter(new X86EnumerableParameter<ControlRegister>(designation, ParameterPlace.MOD_REG, ControlRegister.ENUMERATOR));
                break;
            }
            case Dq: {
                addParameter(new X86EnumerableParameter<DebugRegister>(designation, ParameterPlace.MOD_REG, DebugRegister.ENUMERATOR));
                break;
            }
            case Eb: {
                organize_E(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister8.ENUMERATOR, testArgumentExclusion);
                break;
            }
            case Ed: {
                organize_E(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister32.ENUMERATOR, testArgumentExclusion);
                break;
            }
            case Ed_q: {
                visitOperandTypeCode(operandCode.operandTypeCode());
                switch (context().operandSizeAttribute()) {
                    case BITS_32:
                        organize_E(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister32.ENUMERATOR, testArgumentExclusion);
                        break;
                    case BITS_64:
                        organize_E(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister64.ENUMERATOR, testArgumentExclusion);
                        break;
                    default:
                        TemplateNotNeededException.raise();
                }
                break;
            }
            case Ev: {
                switch (context().operandSizeAttribute()) {
                    case BITS_16:
                        organize_E(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister16.ENUMERATOR, testArgumentExclusion);
                        break;
                    case BITS_32:
                        organize_E(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister32.ENUMERATOR, testArgumentExclusion);
                        break;
                    case BITS_64:
                        organize_E(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister64.ENUMERATOR, testArgumentExclusion);
                        break;
                    default:
                        throw ProgramError.unexpected();
                }
                break;
            }
            case Ew: {
                organize_E(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister16.ENUMERATOR, testArgumentExclusion);
                break;
            }
            case Fv: {
                break;
            }
            case Gb: {
                addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64GeneralRegister8.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case Gd: {
                addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64GeneralRegister32.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case Gd_q: {
                switch (context().operandSizeAttribute()) {
                    case BITS_32:
                        addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64GeneralRegister32.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    case BITS_64:
                        addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64GeneralRegister64.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    default:
                        TemplateNotNeededException.raise();
                }
                break;
            }
            case Gq: {
                addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64GeneralRegister64.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case Gv: {
                switch (context().operandSizeAttribute()) {
                    case BITS_16:
                        addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64GeneralRegister16.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    case BITS_32:
                        addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64GeneralRegister32.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    case BITS_64:
                        addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64GeneralRegister64.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    default:
                        ProgramError.unexpected();
                }
                break;
            }
            case Gw: {
                addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64GeneralRegister16.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case Gz: {
                switch (context().operandSizeAttribute()) {
                    case BITS_16:
                        addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64GeneralRegister16.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    case BITS_32:
                    case BITS_64:
                        addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64GeneralRegister32.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    default:
                        ProgramError.unexpected();
                }
                break;
            }
            case Ib: {
                addParameter(new X86ImmediateParameter(designation, WordWidth.BITS_8), argumentRange, testArgumentExclusion);
                break;
            }
            case ICb: {
                addEnumerableParameter(designation, ParameterPlace.APPEND, AMD64XMMComparison.ENUMERATOR);
                break;
            }
            case Iv: {
                addParameter(new X86ImmediateParameter(designation, context().operandSizeAttribute()), argumentRange, testArgumentExclusion);
                break;
            }
            case Iw: {
                addParameter(new X86ImmediateParameter(designation, WordWidth.BITS_16), argumentRange, testArgumentExclusion);
                break;
            }
            case Iz: {
                WordWidth operandSizeAttribute = context().operandSizeAttribute();
                if (operandSizeAttribute.greaterThan(WordWidth.BITS_32)) {
                    operandSizeAttribute = WordWidth.BITS_32;
                }
                addParameter(new X86ImmediateParameter(designation, operandSizeAttribute), argumentRange, testArgumentExclusion);
                break;
            }
            case Jb: {
                setLabelParameterIndex();
                addParameter(new X86OffsetParameter(designation, WordWidth.BITS_8));
                break;
            }
            case Jv: {
                switch (context().operandSizeAttribute()) {
                    case BITS_16:
                        setLabelParameterIndex();
                        addParameter(new X86OffsetParameter(designation, WordWidth.BITS_16));
                        break;
                    case BITS_32:
                        setLabelParameterIndex();
                        addParameter(new X86OffsetParameter(designation, WordWidth.BITS_32));
                        break;
                    case BITS_64:
                        setLabelParameterIndex();
                        addParameter(new X86OffsetParameter(designation, WordWidth.BITS_64));
                        break;
                    default:
                        ProgramError.unexpected();
                }
                break;
            }
            case Jz: {
                switch (context().operandSizeAttribute()) {
                    case BITS_16:
                        setExternalCodeSizeAttribute(context().operandSizeAttribute());
                        setLabelParameterIndex();
                        addParameter(new X86OffsetParameter(designation, WordWidth.BITS_16));
                        break;
                    case BITS_32:
                    case BITS_64:
                        setLabelParameterIndex();
                        addParameter(new X86OffsetParameter(designation, WordWidth.BITS_32));
                        break;
                    default:
                        ProgramError.unexpected();
                }
                break;
            }
            case Md_q: {
                if (operandSizeAttribute() == WordWidth.BITS_16) {
                    TemplateNotNeededException.raise();
                }
                organize_M(designation);
                break;
            }
            case Mb:
            case Md:
            case Mq:
            case Mdq:
            case Ms:
            case Mv:
            case Mw: {
                organize_M(designation);
                break;
            }
            case Nb: {
                addEnumerableParameter(designation, ParameterPlace.OPCODE1_REXB, AMD64GeneralRegister8.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case Nd_q: {
                final ParameterPlace place = (opcode2() != null) ? ParameterPlace.OPCODE2_REXB : ParameterPlace.OPCODE1_REXB;
                switch (context().operandSizeAttribute()) {
                    case BITS_32:
                        addEnumerableParameter(designation, place, AMD64GeneralRegister32.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    case BITS_64:
                        addEnumerableParameter(designation, place, AMD64GeneralRegister64.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    default:
                        TemplateNotNeededException.raise();
                }
                break;
            }
            case Nv:
                final ParameterPlace place = (opcode2() != null) ? ParameterPlace.OPCODE2_REXB : ParameterPlace.OPCODE1_REXB;
                switch (context().operandSizeAttribute()) {
                    case BITS_16:
                        addEnumerableParameter(designation, place, AMD64GeneralRegister16.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    case BITS_32:
                        addEnumerableParameter(designation, place, AMD64GeneralRegister32.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    case BITS_64:
                        addEnumerableParameter(designation, place, AMD64GeneralRegister64.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    default:
                        ProgramError.unexpected();
                }
                break;
            case Ob:
            case Ov: {
                switch (context().addressSizeAttribute()) {
                    case BITS_32:
                        setExternalCodeSizeAttribute(context().addressSizeAttribute());
                        addParameter(new X86AddressParameter(designation, WordWidth.BITS_32), argumentRange);
                        break;
                    case BITS_64:
                        setLabelParameterIndex();
                        addParameter(new X86AddressParameter(designation, WordWidth.BITS_64), argumentRange);
                        break;
                    default:
                        ProgramError.unexpected();
                }
                break;
            }
            case Pdq: {
                addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64XMMRegister.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case Pq: {
                addEnumerableParameter(designation, ParameterPlace.MOD_REG, MMXRegister.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case PRq: {
                if (context().modCase() != X86TemplateContext.ModCase.MOD_3) {
                    TemplateNotNeededException.raise();
                }
                addEnumerableParameter(designation, ParameterPlace.MOD_RM, MMXRegister.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case Qd:
            case Qq: {
                organize_E(designation, ParameterPlace.MOD_RM, MMXRegister.ENUMERATOR, testArgumentExclusion);
                break;
            }
            case Rq: {
                if (context().modCase() != X86TemplateContext.ModCase.MOD_3) {
                    TemplateNotNeededException.raise();
                }
                addEnumerableParameter(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister64.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case Rv:
                if (context().modCase() != X86TemplateContext.ModCase.MOD_3) {
                    TemplateNotNeededException.raise();
                }
                switch (context().operandSizeAttribute()) {
                    case BITS_16:
                        addEnumerableParameter(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister16.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    case BITS_32:
                        addEnumerableParameter(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister32.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    case BITS_64:
                        addEnumerableParameter(designation, ParameterPlace.MOD_RM_REXB, AMD64GeneralRegister64.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                        break;
                    default:
                        ProgramError.unexpected();
                }
                break;
            case Sw: {
                addEnumerableParameter(designation, ParameterPlace.MOD_REG, SegmentRegister.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case Vpd:
            case Vps:
            case Vq:
            case Vdq:
            case Vsd:
            case Vss: {
                addEnumerableParameter(designation, ParameterPlace.MOD_REG_REXR, AMD64XMMRegister.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case VRq:
            case VRdq:
            case VRpd:
            case VRps: {
                if (context().modCase() != X86TemplateContext.ModCase.MOD_3) {
                    TemplateNotNeededException.raise();
                }
                addEnumerableParameter(designation, ParameterPlace.MOD_RM_REXB, AMD64XMMRegister.ENUMERATOR).excludeTestArguments(testArgumentExclusion);
                break;
            }
            case Wdq:
            case Wpd:
            case Wps:
            case Wq:
            case Wsd:
            case Wss: {
                switch (context().operandSizeAttribute()) {
                    case BITS_32:
                    case BITS_64:
                        organize_E(designation, ParameterPlace.MOD_RM_REXB, AMD64XMMRegister.ENUMERATOR, testArgumentExclusion);
                        break;
                    default:
                        TemplateNotNeededException.raise();
                }
                break;
            }
            case Xz:
            case Yz: {
                switch (operandSizeAttribute()) {
                    case BITS_16:
                        setOperandTypeSuffix("w");
                        break;
                    case BITS_32:
                        setOperandTypeSuffix("l");
                        break;
                    default:
                        TemplateNotNeededException.raise();
                }
                break;
            }
            case Xb:
            case Xv:
            case Yb:
            case Yv: {
                visitOperandTypeCode(operandCode.operandTypeCode());
                break;
            }
            default:
                throw ProgramError.unexpected("undefined operand code: " + operandCode);
        }
    }

    public void visitRegisterOperandCode(RegisterOperandCode registerOperandCode, X86Operand.Designation designation, ImplicitOperand.ExternalPresence externalPresence) {
        switch (operandSizeAttribute()) {
            case BITS_16:
                addImplicitOperand(new X86ImplicitOperand(designation, externalPresence, AMD64GeneralRegister16.ENUMERATOR.get(registerOperandCode.id())));
                break;
            case BITS_32:
                addImplicitOperand(new X86ImplicitOperand(designation, externalPresence, AMD64GeneralRegister32.ENUMERATOR.get(registerOperandCode.id())));
                break;
            case BITS_64:
                addImplicitOperand(new X86ImplicitOperand(designation, externalPresence, AMD64GeneralRegister64.ENUMERATOR.get(registerOperandCode.id())));
                break;
            default:
                throw ProgramError.unexpected();
        }
    }

    public boolean hasRexPrefix(List<Argument> arguments) {
        if (instructionDescription().defaultOperandSize() == WordWidth.BITS_64) {
            return false;
        }
        if (operandSizeAttribute() == WordWidth.BITS_64) {
            return true;
        }
        for (Argument argument : arguments) {
            if (argument instanceof GeneralRegister) {
                if (argument instanceof AMD64GeneralRegister8) {
                    final AMD64GeneralRegister8 generalRegister8 = (AMD64GeneralRegister8) argument;
                    if (generalRegister8.requiresRexPrefix()) {
                        return true;
                    }
                } else {
                    final GeneralRegister generalRegister = (GeneralRegister) argument;
                    if (generalRegister.value() >= 8) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
