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

import java.util.*;
import java.util.Arrays;

import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 * A bundle of choices one can make when creating a template (addressing modes and operand sizes).
 */
public class X86TemplateContext implements Cloneable {

    /**
     * ModRM  mod Field. See mod field in "A.3.1 ModRM Operand References"
     */
    public enum ModCase {
        MOD_0,
        MOD_1,
        MOD_2,
        MOD_3;

        public static final List<ModCase> VALUES = Arrays.asList(values());

        public int value() {
            return ordinal();
        }
    }

    /**
     * Addressing mode variants. See r/m field in "A.3.1 ModRM Operand References"
     */
    public enum RMCase {
        NORMAL(0), // all other addressing modes, e.g. registers
        SIB(4),  // Scale-Index-Base addressing mode, e.g. [SIB]; see "Table A-15. ModRM Memory References, 32-Bit and 64-Bit Addressing"
        SWORD(6), // indirect signed 16-bit displacement, e.g. [disp16]; see "Table A-13. ModRM Memory References, 16-Bit Addressing"
        SDWORD(5); // indirect signed 32-bit displacement, e.g. [disp32] or [rIP+disp32]; see "Table A-15. ModRM Memory References, 32-Bit and 64-Bit Addressing"

        public static final List<RMCase> VALUES = Arrays.asList(values());

        private final int rmFieldValue;

        private RMCase(int rmFieldValue) {
            this.rmFieldValue = rmFieldValue;
        }

        public int value() {
            return rmFieldValue;
        }
    }

    /**
     * Classes of "index" fields for SIB. See "Table A-17. SIB Memory References".
     */
    public enum SibIndexCase {
        GENERAL_REGISTER, // index register specified
        NONE; // SIB index = 100b and REX.X = 0 - no index register specified

        public static final List<SibIndexCase> VALUES = Arrays.asList(values());
    }

    /**
     * Classes of "base" fields for SIB. See "Table A-16. SIB base Field References".
     */
    public enum SibBaseCase {
        GENERAL_REGISTER, // general purpose register base
        SPECIAL; // /5 - immediate displacement base / rBP / r13)

        public static final List<SibBaseCase> VALUES = Arrays.asList(values());
    }

    public X86TemplateContext() {
    }

    private WordWidth addressSizeAttribute;

    public WordWidth addressSizeAttribute() {
        return addressSizeAttribute;
    }

    public void setAddressSizeAttribute(WordWidth addressSizeAttribute) {
        this.addressSizeAttribute = addressSizeAttribute;
    }

    private WordWidth operandSizeAttribute;

    public WordWidth operandSizeAttribute() {
        return operandSizeAttribute;
    }

    public void setOperandSizeAttribute(WordWidth operandSizeAttribute) {
        this.operandSizeAttribute = operandSizeAttribute;
    }

    private ModRMGroup.Opcode modRMGroupOpcode;

    public ModRMGroup.Opcode modRMGroupOpcode() {
        return modRMGroupOpcode;
    }

    public void setModRMGroupOpcode(ModRMGroup.Opcode modRMGroupOpcode) {
        this.modRMGroupOpcode = modRMGroupOpcode;
    }

    private ModCase modCase;

    public ModCase modCase() {
        return modCase;
    }

    public void setModCase(ModCase modCase) {
        this.modCase = modCase;
    }

    private RMCase rmCase;

    public RMCase rmCase() {
        return rmCase;
    }

    public void setRMCase(RMCase value) {
        this.rmCase = value;
    }

    private SibIndexCase sibIndexCase;

    public SibIndexCase sibIndexCase() {
        return sibIndexCase;
    }

    public void setSibIndexCase(SibIndexCase sibIndexCase) {
        this.sibIndexCase = sibIndexCase;
    }

    protected SibBaseCase sibBaseCase;

    public SibBaseCase sibBaseCase() {
        return sibBaseCase;
    }

    public void setSibBaseCase(SibBaseCase sibBaseCase) {
        this.sibBaseCase = sibBaseCase;
    }

    @Override
    public X86TemplateContext clone() {
        try {
            return (X86TemplateContext) super.clone();
        } catch (CloneNotSupportedException cloneNotSupportedException) {
            throw ProgramError.unexpected("clone() failed", cloneNotSupportedException);
        }
    }

    @Override
    public String toString() {
        return "<Context: " + addressSizeAttribute + ", " + operandSizeAttribute + ", " + modRMGroupOpcode + ", " + modCase + ", " + rmCase + ", " + sibIndexCase + ", " + sibBaseCase + ">";
    }
}
