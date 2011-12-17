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
package com.sun.max.asm.gen;

import java.util.*;

import com.sun.max.asm.gen.cisc.x86.*;
import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 * A sequence of objects that describe group of closely related instructions. An
 * {@link #Template instruction template} is created for each instruction in the
 * group.
 * <p>
 * The types of objects that an instruction description contains
 * depend on the whether the underlying platform is CISC or RISC.
 * The types for these two instruction categories are enumerated by
 * the {@code visit...} methods in the {@link RiscInstructionDescriptionVisitor}
 * and {@link X86InstructionDescriptionVisitor} classes.
 */
public abstract class InstructionDescription implements Iterable<Object>, Cloneable {

    private static int nextSerial;

    private int serial;

    /**
     * The components of the description.
     */
    private final List<Object> specifications;

    public InstructionDescription(List<Object> specifications) {
        this.specifications = specifications;
        this.serial = nextSerial++;
    }

    public int serial() {
        return serial;
    }

    /**
     * @return the objects from which this description is composed
     */
    public List<Object> specifications() {
        return specifications;
    }

    private List<InstructionConstraint> constraints;

    /**
     * @return the {@link InstructionConstraint} instances (if any) within this description
     */
    public List<InstructionConstraint> constraints() {
        if (constraints == null) {
            constraints = new ArrayList<InstructionConstraint>(specifications.size());
            for (Object s : specifications) {
                if (s instanceof InstructionConstraint) {
                    constraints.add((InstructionConstraint) s);
                }
            }
        }
        return constraints;
    }

    private String architectureManualSection;

    public InstructionDescription setArchitectureManualSection(String section) {
        architectureManualSection = section;
        return this;
    }

    public String architectureManualSection() {
        return architectureManualSection;
    }

    private String externalName;

    public String externalName() {
        return externalName;
    }

    public InstructionDescription setExternalName(String name) {
        this.externalName = name;
        return this;
    }

    private boolean isDisassemblable = true;

    /**
     * Determines if the templates created from the description can be recovered from an assembled instruction.
     * This is almost always possible. One example where it isn't is an instruction description that
     * has a parameter that is not correlated one-to-one with some bits in the encoded instruction.
     * In RISC architectures, this will be any instruction that has at least one {@link InputOperandField}
     * parameter.
     */
    public boolean isDisassemblable() {
        return isDisassemblable;
    }

    public InstructionDescription beNotDisassemblable() {
        isDisassemblable = false;
        return this;
    }

    public boolean isSynthetic() {
        return false;
    }

    private boolean isExternallyTestable = true;

    public boolean isExternallyTestable() {
        return isExternallyTestable;
    }

    public InstructionDescription beNotExternallyTestable() {
        isExternallyTestable = false;
        return this;
    }

    private WordWidth requiredAddressSize;

    public WordWidth requiredAddressSize() {
        return requiredAddressSize;
    }

    public InstructionDescription requireAddressSize(WordWidth addressSize) {
        this.requiredAddressSize = addressSize;
        return this;
    }

    private WordWidth requiredOperandSize;

    public WordWidth requiredOperandSize() {
        return requiredOperandSize;
    }

    public InstructionDescription requireOperandSize(WordWidth operandSize) {
        this.requiredOperandSize = operandSize;
        return this;
    }

    public Iterator<Object> iterator() {
        return specifications.iterator();
    }

    @Override
    public InstructionDescription clone() {
        try {
            final InstructionDescription clone = (InstructionDescription) super.clone();
            clone.serial = ++nextSerial;
            return clone;
        } catch (CloneNotSupportedException cloneNotSupportedException) {
            throw ProgramError.unexpected(cloneNotSupportedException);
        }
    }

    @Override
    public final int hashCode() {
        return serial;
    }

    @Override
    public final boolean equals(Object object) {
        if (object instanceof InstructionDescription) {
            return serial == ((InstructionDescription) object).serial;
        }
        return false;
    }

}
