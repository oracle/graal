/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.types;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.JavaKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a method signature provided by the runtime.
 *
 * @see <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">Method
 *      Descriptors</a>
 */
public final class SignatureDescriptor extends Descriptor {

    SignatureDescriptor(TypeDescriptors typeDescriptors, String value) {
        super(value);
        components = parse(typeDescriptors, value, 0);
        int n = 0;
        for (int i = 0; i != components.length; ++i) {
            n += components[i].toKind().getSlotCount();
        }
        numberOfSlots = n;
    }

    /**
     * The parameter types in this signature followed by the return type.
     */
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final TypeDescriptor[] components;
    private final int numberOfSlots;

    /**
     * Parses a signature descriptor string into its parameter and return type components.
     *
     * @return the parsed parameter types followed by the return type.
     * @throws ClassFormatError if {@code string} is not well formed
     */
    public static TypeDescriptor[] parse(TypeDescriptors typeDescriptors, String string, int startIndex) throws ClassFormatError {
        if ((startIndex > string.length() - 3) || string.charAt(startIndex) != '(') {
            throw new ClassFormatError("Invalid method signature: " + string);
        }
        final List<TypeDescriptor> buf = new ArrayList<>();
        int i = startIndex + 1;
        while (string.charAt(i) != ')') {
            final TypeDescriptor descriptor = typeDescriptors.parse(string, i, true);
            buf.add(descriptor);
            i = i + descriptor.toString().length();
            if (i >= string.length()) {
                throw new ClassFormatError("Invalid method signature: " + string);
            }
        }
        i++;
        final TypeDescriptor descriptor = typeDescriptors.parse(string, i, true);
        if (i + descriptor.toString().length() != string.length()) {
            throw new ClassFormatError("Invalid method signature: " + string);
        }
        final TypeDescriptor[] descriptors = buf.toArray(new TypeDescriptor[buf.size() + 1]);
        descriptors[buf.size()] = descriptor;
        return descriptors;
    }

    /**
     * Parses a signature descriptor string into its parameter and return type components.
     *
     * @return the parsed parameter types followed by the return type.
     * @throws ClassFormatError if {@code string} is not well formed
     */
    public static int skipValidSignature(String value, int beginIndex) throws ClassFormatError {
        if ((beginIndex > value.length() - 3) || value.charAt(beginIndex) != '(') {
            throw new ClassFormatError("Invalid method signature: " + value);
        }
        int i = beginIndex + 1;
        while (value.charAt(i) != ')') {
            int endIndex = TypeDescriptors.skipValidTypeDescriptor(value, i, true);
            if (i >= value.length()) {
                throw new ClassFormatError("Invalid method signature: " + value);
            }
            i = endIndex;
        }
        i++;
        return TypeDescriptors.skipValidTypeDescriptor(value, i, true);
    }

    public JavaKind resultKind() {
        return getReturnTypeDescriptor().toKind();
    }

    /**
     * Gets the type descriptor of the return type in this signature object.
     */
    public TypeDescriptor getReturnTypeDescriptor() {
        return components[components.length - 1];
    }

    /**
     * Gets the number of local variable slots used by the parameters + return type in this
     * signature. Long and double parameters use two slots, all other parameters use one slot.
     */
    public int getNumberOfSlots() {
        return numberOfSlots;
    }

    /**
     * Gets the number of local variable slots used by the parameters only in this signature. Long
     * and double parameters use two slots, all other parameters use one slot.
     */
    public int getNumberOfSlotsForParameters() {
        return numberOfSlots - components[components.length - 1].toKind().getSlotCount();
    }

    public int getParameterCount(boolean receiver) {
        return components.length - 1 + (receiver ? 1 : 0);
    }

    @Override
    public void verify() {
        int endIndex = skipValidSignature(value, 0);
        if (endIndex != value.length()) {
            throw new ClassFormatError("Invalid method descriptor " + value);
        }
    }

    public JavaKind getParameterKind(int paramIndex) {
        return components[paramIndex].toKind();
    }

    public TypeDescriptor getParameterType(int paramIndex) {
        return components[paramIndex];
    }
}
