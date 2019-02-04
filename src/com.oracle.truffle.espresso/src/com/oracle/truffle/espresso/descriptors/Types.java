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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.ByteString.Descriptor;
import com.oracle.truffle.espresso.descriptors.ByteString.Type;
import com.oracle.truffle.espresso.meta.JavaKind;
import org.graalvm.collections.EconomicSet;

/**
 * Manages creation and parsing of type descriptors ("field descriptors" in the JVMS).
 *
 * @see "https://docs.oracle.com/javase/specs/jvms/se10/html/jvms-4.html#jvms-4.3.2"
 */
public final class Types extends DescriptorCache<ByteString<Type>, ByteString<Type>> {

    private static final EconomicSet<ByteString<Type>> STATIC_TYPES = EconomicSet.create(256);

    private static ByteString<Type> fromJavaString(String typeString) {
        ByteString<Type> type = ByteString.fromJavaString(typeString);
        assert isValid(type);
        return type;
    }

    public static ByteString<Type> fromConstantPoolName(ByteString<?> cpName) {
        byte[] bytes = new byte[cpName.length() + 2];
        if (cpName.byteAt(0) == '[') {
            return (ByteString<Type>) cpName;
        }
        ByteString.copyBytes(cpName, 0, bytes, 1, cpName.length());
        bytes[0] = 'L';
        bytes[bytes.length - 1] = ';';
        return new ByteString<>(bytes);
    }

    @SuppressWarnings("unchecked")
    public static ByteString<Type> fromDescriptor(ByteString<? extends Descriptor> descriptor) {
        ByteString<Type> type = (ByteString<Type>) descriptor;
        assert isValid(type);
        return type;
    }

    public ByteString<Type> fromClassGetName(String className) {
        if (className.startsWith("[") || className.endsWith(";") || className.length() == 1) {
            return make(Types.fromJavaString(className.replace('.', '/')));
        }
        return make(Types.fromJavaString("L" + className.replace('.', '/') + ";"));
    }

    @Override
    protected ByteString<Type> create(ByteString<Type> key) {
        return key;
    }

    @Override
    public ByteString<Type> lookup(ByteString<Type> type) {
        if (Types.isPrimitive(type)) {
            return Types.getJavaKind(type).getType();
        }
        return super.lookup(type);
    }

    public static ByteString<Type> forPrimitive(JavaKind kind) {
        assert kind.isPrimitive();
        return kind.getType();
    }

    /**
     * Parses a valid Java type descriptor.
     *
     * @param descriptor the raw signature from which to create a Java type descriptor
     * @param beginIndex the index within the string from which to start parsing
     * @param slashes specifies if package components in {@code string} are separated by {@code '/'}
     *            or {@code '.'}
     * @throws ClassFormatError if the type descriptor is not valid
     */
    ByteString<Type> parse(ByteString<? extends Descriptor> descriptor, int beginIndex, boolean slashes) throws ClassFormatError {
        int endIndex = skipValidTypeDescriptor(descriptor, beginIndex, slashes);
        if (endIndex == beginIndex + 1) {
            return forPrimitive(JavaKind.fromPrimitiveOrVoidTypeChar((char) descriptor.byteAt(beginIndex)));
        }
        return make((ByteString<Type>) descriptor.substring(beginIndex, endIndex));
    }

    /**
     * Verifies that a valid type descriptor is at {@code beginIndex} in {@code type}.
     *
     * @param slashes specifies if package components are separated by {@code '/'} or {@code '.'}
     * @return the index one past the valid type descriptor starting at {@code beginIndex}
     * @throws ClassFormatError if there is no valid type descriptor
     */
    @TruffleBoundary
    static int skipValidTypeDescriptor(ByteString<? extends Descriptor> descriptor, int beginIndex, boolean slashes) throws ClassFormatError {
        if (beginIndex >= descriptor.length()) {
            throw new ClassFormatError("invalid type descriptor: " + descriptor);
        }
        char ch = (char) descriptor.byteAt(beginIndex);
        if (ch != '[' && ch != 'L') {
            return beginIndex + 1;
        }
        switch (ch) {
            case 'L': {
                final int endIndex = skipClassName(descriptor, beginIndex + 1, slashes ? '/' : '.');
                if (endIndex > beginIndex + 1 && endIndex < descriptor.length() && descriptor.byteAt(endIndex) == ';') {
                    return endIndex + 1;
                }
                throw new ClassFormatError("Invalid Java name " + descriptor.substring(beginIndex));
            }
            case '[': {
                // compute the number of dimensions
                int index = beginIndex;
                while (index < descriptor.length() && descriptor.byteAt(index) == '[') {
                    index++;
                }
                final int dimensions = index - beginIndex;
                if (dimensions > 255) {
                    throw new ClassFormatError("Array with more than 255 dimensions " + descriptor.substring(beginIndex));
                }
                return skipValidTypeDescriptor(descriptor, index, slashes);
            }
        }
        throw new ClassFormatError("Invalid type descriptor " + descriptor.substring(beginIndex));
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
    public ByteString<Type> arrayOf(ByteString<Type> type, int dimensions) {
        assert dimensions > 0;
        if (Types.getArrayDimensions(type) + dimensions > 255) {
            throw new ClassFormatError("Array type with more than 255 dimensions");
        }
        // Prepend #dimensions '[' to type descriptor.
        byte[] bytes = new byte[type.length() + dimensions];
        Arrays.fill(bytes, 0, dimensions, (byte) '[');
        ByteString.copyBytes(type, 0, bytes, dimensions, type.length());
        return make(new ByteString<>(bytes));
    }

    public ByteString<Type> arrayOf(ByteString<Type> type) {
        return arrayOf(type, 1);
    }

    private static int skipClassName(ByteString<? extends Descriptor> descriptor, int from, final char separator) throws ClassFormatError {
        assert separator == '.' || separator == '/';
        int index = from;
        final int length = descriptor.length();
        while (index < length) {
            // Safe cast, method returns only for ./;[ characters.
            char ch = (char) descriptor.byteAt(index);
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

    public static String fromCanonicalClassName(String javaName) {
        if (javaName.endsWith("[]")) {
            return "[" + fromCanonicalClassName(javaName.substring(0, javaName.length() - 2));
        }
        switch (javaName) {
            case "byte":
                return "B";
            case "char":
                return "C";
            case "double":
                return "D";
            case "float":
                return "F";
            case "int":
                return "I";
            case "long":
                return "J";
            case "short":
                return "S";
            case "void":
                return "V";
            case "boolean":
                return "Z";
            default:
                // Reference descriptor.
                return "L" + javaName.replace(".", "/") + ";";
        }
    }

    public static boolean isPrimitive(ByteString<Type> type) {
        if (type.length() != 1) {
            return false;
        }
        switch (type.byteAt(0)) {
            case 'B': // byte
            case 'C': // char
            case 'D': // double
            case 'F': // float
            case 'I': // int
            case 'J': // long
            case 'S': // short
            case 'V': // void
            case 'Z': // boolean
                return true;
            default:
                return false;
        }
    }

    /**
     * Gets the kind denoted by this type descriptor.
     *
     * @return the kind denoted by this type descriptor
     */
    public static JavaKind getJavaKind(ByteString<Type> type) {
        if (type.length() == 1) {
            return JavaKind.fromPrimitiveOrVoidTypeChar((char) type.byteAt(0));
        }
        return JavaKind.Object;
    }

    /**
     * Gets the number of array dimensions in this type descriptor.
     */
    public static int getArrayDimensions(ByteString<Type> type) {
        int dims = 0;
        while (dims < type.length() && type.byteAt(dims) == '[') {
            dims++;
        }
        return dims;
    }

    public static void verify(ByteString<Type> type) throws ClassFormatError {
        if (!isValid(type)) {
            throw new ClassFormatError("Invalid type descriptor " + type);
        }
    }

    private static boolean isValid(ByteString<Type> type) {
        int endIndex = Types.skipValidTypeDescriptor(type, 0, true);
        return endIndex == type.length();
    }

    public static boolean isArray(ByteString<Type> type) {
        return type.byteAt(0) == '[';
    }

    public ByteString<Type> getComponentType(ByteString<Type> type) {
        if (isArray(type)) {
            return type.substring(1);
        }
        return null;
    }

    public ByteString<Type> getElementalType(ByteString<Type> type) {
        if (isArray(type)) {
            return type.substring(getArrayDimensions(type));
        }
        return type;
    }

    public ByteString<Type> fromClass(Class<?> clazz) {
        return ByteString.fromJavaString(fromCanonicalClassName(clazz.getCanonicalName()));
    }

    public static String binaryName(ByteString<Type> type) {
        if (isArray(type)) {
            return type.toString();
        }
        if (isPrimitive(type)) {
            return getJavaKind(type).getJavaName();
        }
        return type.substring(1, type.length() - 1).toString().replace('/', '.');
    }
}
