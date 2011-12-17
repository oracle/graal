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

import java.lang.reflect.*;
import java.util.*;

import com.sun.max.*;
import com.sun.max.asm.*;
import com.sun.max.program.*;

/**
 * An internal representation of an assembler method.
 */
public abstract class Template implements Cloneable, Comparable<Template> {

    private int serial = -1;
    private InstructionDescription instructionDescription;
    private int labelParameterIndex = -1;

    protected Template(InstructionDescription instructionDescription) {
        this.instructionDescription = instructionDescription;
    }

    protected Template(InstructionDescription instructionDescription, int serial) {
        this.instructionDescription = instructionDescription;
        this.serial = serial;
    }

    public int serial() {
        return serial;
    }

    public void setSerial(int serial) {
        this.serial = serial;
    }

    public InstructionDescription instructionDescription() {
        return instructionDescription;
    }

    /**
     * Gets the index of this template's parameter that can be represented as a {@linkplain Label label}.
     * A template is guaranteed to at most one such parameter.
     *
     * @return the index of this template's label parameter or -1 if it does not have one
     */
    public int labelParameterIndex() {
        return labelParameterIndex;
    }

    /**
     * Call this right before adding a parameter that may be represented by a label.
     */
    protected void setLabelParameterIndex() {
        if (labelParameterIndex != -1) {
            throw ProgramError.unexpected("a template can have at most one label parameter");
        }
        labelParameterIndex = parameters().size();
    }

    public abstract String assemblerMethodName();

    protected Method assemblerMethod;

    /**
     * Determines if this template is redundant with respect to another
     * {@linkplain #canonicalRepresentative() canonical} template.
     * Two templates are redundant if they both have the same name and operands.
     * Redundant pairs of instructions are assumed to implement the same machine
     * instruction semantics but may have different encodings.
     *
     * @return whether this template is redundant with respect some other template
     */
    public final boolean isRedundant() {
        return canonicalRepresentative() != null;
    }

    /**
     * @see #isRedundant()
     */
    public abstract Template canonicalRepresentative();

    /**
     * The name of the Java method that will be created from this template.
     */
    private String internalName;

    public String internalName() {
        return internalName;
    }

    protected void setInternalName(String name) {
        this.internalName = name;
    }

    public String externalName() {
        if (instructionDescription.externalName() != null) {
            return instructionDescription.externalName();
        }
        return internalName();
    }

    public boolean isDisassemblable() {
        return instructionDescription.isDisassemblable();
    }

    public boolean isExternallyTestable() {
        return instructionDescription.isExternallyTestable();
    }

    public abstract List<? extends Operand> operands();

    public abstract List<? extends Parameter> parameters();

    /**
     * Gets the argument from a given list of arguments corresponding to a parameter of this template.
     *
     * @return the argument at index {@code i} in {@code arguments} where {@code parameter == parameters().get(i)}
     */
    public Argument bindingFor(Parameter parameter, List<Argument> arguments) {
        final List< ? extends Parameter> parameters = parameters();
        assert arguments.size() == parameters.size();
        final int index = Utils.indexOfIdentical(parameters, parameter);
        ProgramError.check(index != -1, parameter + " is not a parameter of " + externalName());
        return arguments.get(index);
    }

    public Class[] parameterTypes() {
        final Class[] parameterTypes = new Class[parameters().size()];
        for (int i = 0; i < parameters().size(); i++) {
            parameterTypes[i] = parameters().get(i).type();
        }
        return parameterTypes;
    }

    @Override
    public Template clone() {
        try {
            final Template result = (Template) super.clone();
            result.instructionDescription = instructionDescription.clone();
            return result;
        } catch (CloneNotSupportedException cloneNotSupportedException) {
            throw ProgramError.unexpected(cloneNotSupportedException);
        }
    }

    public int compareTo(Template other) {
        int result = internalName.compareTo(other.internalName);
        if (result != 0) {
            return result;
        }
        final List<? extends Parameter> myParameters = parameters();
        final List<? extends Parameter> otherParameters = other.parameters();
        final int n = Math.min(myParameters.size(), otherParameters.size());
        for (int i = 0; i < n; i++) {
            result = myParameters.get(i).compareTo(otherParameters.get(i));
            if (result != 0) {
                return result;
            }
        }
        return new Integer(myParameters.size()).compareTo(otherParameters.size());
    }

    public final boolean isEquivalentTo(Template other) {
        if (this == other) {
            return true;
        }
        Template a = this;
        if (a.canonicalRepresentative() != null) {
            a = a.canonicalRepresentative();
        }
        Template b = other;
        if (b.canonicalRepresentative() != null) {
            b = b.canonicalRepresentative();
        }
        return a == b;
    }
}
