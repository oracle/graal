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

/**
 * Some information about a family of instructions that have the same basic opcode.
 *
 * @see OpcodeAssessor
 */
public class InstructionAssessment {

    private boolean hasAddressSizeVariants;
    private boolean hasOperandSizeVariants;
    private boolean hasModRMByte;
    private ModRMGroup modRMGroup;
    private boolean isJump;

    public InstructionAssessment() {
    }

    public void haveAddressSizeVariants() {
        hasAddressSizeVariants = true;
    }

    public boolean hasAddressSizeVariants() {
        return hasAddressSizeVariants;
    }

    public void haveOperandSizeVariants() {
        hasOperandSizeVariants = true;
    }

    public boolean hasOperandSizeVariants() {
        return hasOperandSizeVariants;
    }

    public void haveModRMByte() {
        hasModRMByte = true;
    }

    public boolean hasModRMByte() {
        return hasModRMByte;
    }

    public void setModRMGroup(ModRMGroup modRMGroup) {
        this.modRMGroup = modRMGroup;
        this.hasModRMByte = modRMGroup != null;
    }

    public ModRMGroup modRMGroup() {
        return modRMGroup;
    }

    public void beJump() {
        isJump = true;
    }

    public boolean isJump() {
        return isJump;
    }
}
