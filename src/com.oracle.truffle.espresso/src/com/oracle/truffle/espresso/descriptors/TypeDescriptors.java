/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.impl.ByteString;
import com.oracle.truffle.espresso.impl.ByteString.Interned;
import com.oracle.truffle.espresso.impl.ByteString.Signature;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.meta.JavaKind;

/**
 * Manages creation and parsing of type descriptors ("field descriptors" in the JVMS).
 *
 * @see "https://docs.oracle.com/javase/specs/jvms/se10/html/jvms-4.html#jvms-4.3.2"
 */
public final class TypeDescriptors extends DescriptorCache<Type, ByteString<Type>, ByteString<Type>> {

    @Override
    protected ByteString<Type> create(ByteString<Type> key) {
        return new ByteString<Type>(key);
    }

    //    private static final EnumMap<JavaKind, ByteString<Type>> primitives = new EnumMap<>(JavaKind.class);
//
//    static {
//        for (JavaKind kind : JavaKind.values()) {
//            if (kind.isPrimitive()) {
//                String key = String.valueOf(kind.getTypeChar());
//                TypeDescriptor descriptor = new TypeDescriptor(key);
//                primitives.put(kind, descriptor);
//            }
//        }
//    }

//    @Override
//    public synchronized TypeDescriptor lookup(String key) {
//        if (key.length() == 1) {
//            JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeChar(key.charAt(0));
//            TypeDescriptor value = primitives.get(kind);
//            if (value != null) {
//                return value;
//            }
//        }
//        return super.lookup(key);
//    }
//
//    public static ByteString<Type> forPrimitive(JavaKind kind) {
//        assert kind.isPrimitive();
//        return primitives.get(kind);
//    }

    /**
     * Parses a valid Java type descriptor.
     *
     * @param signature the string from which to create a Java type descriptor
     * @param beginIndex the index within the string from which to start parsing
     * @param slashes specifies if package components in {@code string} are separated by {@code '/'}
     *            or {@code '.'}
     * @throws ClassFormatError if the type descriptor is not valid
     */
    public ByteString<Type> parse(ByteString<Signature> signature, int beginIndex, boolean slashes) throws ClassFormatError {
        int endIndex = skipValidTypeDescriptor(signature, beginIndex, slashes);
        if (endIndex == beginIndex + 1) {
            return forPrimitive(JavaKind.fromPrimitiveOrVoidTypeChar((char) signature.byteAt(beginIndex)));
        }
        return make(signature.substring(beginIndex, endIndex));
    }

    /**
     * Verifies that a valid type descriptor is at {@code beginIndex} in {@code string}.
     *
     * @param slashes specifies if package components are separated by {@code '/'} or {@code '.'}
     * @return the index one past the valid type descriptor starting at {@code beginIndex}
     * @throws ClassFormatError if there is no valid type descriptor
     */
    @CompilerDirectives.TruffleBoundary
    public static int skipValidTypeDescriptor(String string, int beginIndex, boolean slashes) throws ClassFormatError {
        if (beginIndex >= string.length()) {
            throw new ClassFormatError("invalid type descriptor: " + string);
        }
        char ch = string.charAt(beginIndex);
        if (ch != '[' && ch != 'L') {
            return beginIndex + 1;
        }
        switch (ch) {
            case 'L': {
                if (slashes) {
                    // parse a slashified Java class name
                    final int endIndex = skipClassName(string, beginIndex + 1, '/');
                    if (endIndex > beginIndex + 1 && endIndex < string.length() && string.charAt(endIndex) == ';') {
                        return endIndex + 1;
                    }
                } else {
                    // parse a dottified Java class name and convert to slashes
                    final int endIndex = skipClassName(string, beginIndex + 1, '.');
                    if (endIndex > beginIndex + 1 && endIndex < string.length() && string.charAt(endIndex) == ';') {
                        return endIndex + 1;
                    }
                }
                throw new ClassFormatError("Invalid Java name " + string.substring(beginIndex));
            }
            case '[': {
                // compute the number of dimensions
                int index = beginIndex;
                while (index < string.length() && string.charAt(index) == '[') {
                    index++;
                }
                final int dimensions = index - beginIndex;
                if (dimensions > 255) {
                    throw new ClassFormatError("Array with more than 255 dimensions " + string.substring(beginIndex));
                }
                return skipValidTypeDescriptor(string, index, slashes);
            }
        }
        throw new ClassFormatError("Invalid type descriptor " + string.substring(beginIndex));
    }

    /**
     * Gets the type descriptor for the specified component type descriptor with the specified
     * number of dimensions. For example if the number of dimensions is 1, then this method will
     * return a descriptor for an array of the component type; if the number of dimensions is 2, it
     * will return a descriptor for an array of an array of the component type, etc.
     *
     * @param type the type descriptor for the component type of the array
     * @param dimensions the number of array dimensions
     * @return the canonical type descriptor for the specified component type and dimensions
     */
    public ByteString<Type> getArrayDescriptorForDescriptor(ByteString<Type> type, int dimensions) {
        assert dimensions > 0;
        if (TypeDescriptor.getArrayDimensions(type) + dimensions > 255) {
            throw new ClassFormatError("Array type with more than 255 dimensions");
        }
        // prepend dimensions '[' to descriptor
        return make(value);
    }

    private static int skipClassName(String string, int from, final char separator) throws ClassFormatError {
        int index = from;
        final int length = string.length();
        while (index < length) {
            char ch = string.charAt(index);
            if (ch == '.' || ch == '/') {
                if (separator != ch) {
                    return index;
                }
            } else if (ch == ';' || ch == '[') {
                return index;
            }
            index++;
        }
        return index;
    }
}