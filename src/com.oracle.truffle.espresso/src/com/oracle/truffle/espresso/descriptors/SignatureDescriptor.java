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
package com.oracle.truffle.espresso.descriptors;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.espresso.impl.ByteString;
import com.oracle.truffle.espresso.impl.ByteString.Descriptor;
import com.oracle.truffle.espresso.impl.ByteString.Signature;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.meta.JavaKind;

/**
 * Represents a method signature provided by the runtime.
 *
 * @see <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">Method
 *      Descriptors</a>
 */
public abstract class SignatureDescriptor {

    private SignatureDescriptor() {
        /* no instances */
    }

//    SignatureDescriptor(TypeDescriptors typeDescriptors, String value) {
//        super(value);
//        components = parse(typeDescriptors, value, 0);
//        int n = 0;
//        for (int i = 0; i != components.length; ++i) {
//            n += components[i].toKind().getSlotCount();
//        }
//        numberOfSlots = n;
//    }

    /**
     * Parses a signature descriptor string into its parameter and return type components.
     *
     * @return the parsed parameter types followed by the return type.
     * @throws ClassFormatError if {@code string} is not well formed
     */
    public static ByteString<Type>[] parse(TypeDescriptors typeDescriptors, ByteString<Signature> signature, int startIndex) throws ClassFormatError {
        if ((startIndex > signature.length() - 3) || signature.byteAt(startIndex) != '(') {
            throw new ClassFormatError("Invalid method signature: " + signature);
        }

        final List<ByteString<Type>> buf = new ArrayList<>();
        int i = startIndex + 1;
        while (signature.byteAt(i) != ')') {
            final ByteString<Type> descriptor = typeDescriptors.parse(signature, i, true);
            buf.add(descriptor);
            i = i + descriptor.toString().length();
            if (i >= signature.length()) {
                throw new ClassFormatError("Invalid method signature: " + string);
            }
        }
        i++;
        final ByteString<Type> descriptor = typeDescriptors.parse(string, i, true);
        if (i + descriptor.toString().length() != string.length()) {
            throw new ClassFormatError("Invalid method signature: " + string);
        }
        final ByteString<Type>[] descriptors = buf.toArray(new ByteString<Type>[buf.size() + 1]);
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

    @SuppressWarnings("unchecked")
    public static ByteString<Signature> check(ByteString<? extends Descriptor> descriptor) {
        assert isValid((ByteString<Signature>) descriptor);
        return (ByteString<Signature>) descriptor;
    }

    public JavaKind resultKind() {
        return TypeDescriptor.getJavaKind(getReturnType());
    }

    /**
     * Gets the type descriptor of the return type in this signature object.
     */
    public static ByteString<Type> getReturnType(ByteString<Type>[] signature) {
        return signature[signature.length - 1];
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
        return numberOfSlots - resultKind().getSlotCount();
    }

    public int getParameterCount(boolean receiver) {
        return components.length - 1 + (receiver ? 1 : 0);
    }

    public static boolean isValid(ByteString<Signature> signature) {
        int endIndex = skipValidSignature(signature, 0);
        return endIndex == signature.length();
    }

    public static ByteString<Signature> verify(ByteString<Signature> signature) {
        int endIndex = skipValidSignature(signature, 0);
        if (endIndex != signature.length()) {
            throw new ClassFormatError("Invalid signature descriptor " + signature);
        }
        return signature;
    }

    public static JavaKind getParameterKind(ByteString<Type>[] signature, int paramIndex) {
        return TypeDescriptor.getJavaKind(signature[paramIndex]);
    }

    public ByteString<Type> getParameterType(ByteString<Type>[] signature, int paramIndex) {
        return signature[paramIndex];
    }
}
