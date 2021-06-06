/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.descriptors.ByteSequence.EMPTY;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Symbol.Descriptor;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

/**
 * Manages creation and parsing of type descriptors ("field descriptors" in the JVMS).
 *
 * @see "https://docs.oracle.com/javase/specs/jvms/se10/html/jvms-4.html#jvms-4.3.2"
 */
public final class Types {

    private final Symbols symbols;

    public Types(Symbols symbols) {
        this.symbols = symbols;
    }

    @SuppressWarnings("unchecked")
    public static Symbol<Type> fromDescriptor(Symbol<? extends Descriptor> descriptor) {
        Symbol<Type> type = (Symbol<Type>) descriptor;
        // TODO(peterssen): Turn check into assert, maybe?
        if (!isValid(type)) {
            return null;
        }
        return type;
    }

    public Symbol<Type> getOrCreate(String name) {
        return getOrCreate(ByteSequence.create(name));
    }

    public Symbol<Type> getOrCreate(ByteSequence name) {
        if (!Validation.validTypeDescriptor(name, false)) {
            return null;
        }
        return symbols.symbolify(name);
    }

    public static String internalFromClassName(String className) {
        if (className.startsWith("[") || className.endsWith(";")) {
            return className.replace('.', '/');
        }
        // FIXME(peterssen): Remove "" string concat.
        switch (className) {
            case "boolean":
                return JavaKind.Boolean.getTypeChar() + "";
            case "byte":
                return JavaKind.Byte.getTypeChar() + "";
            case "char":
                return JavaKind.Char.getTypeChar() + "";
            case "short":
                return JavaKind.Short.getTypeChar() + "";
            case "int":
                return JavaKind.Int.getTypeChar() + "";
            case "float":
                return JavaKind.Float.getTypeChar() + "";
            case "double":
                return JavaKind.Double.getTypeChar() + "";
            case "long":
                return JavaKind.Long.getTypeChar() + "";
            case "void":
                return JavaKind.Void.getTypeChar() + "";
        }
        // Reference type.
        return "L" + className.replace('.', '/') + ";";
    }

    @TruffleBoundary
    public Symbol<Type> fromClassGetName(String className) {
        return symbols.symbolify(ByteSequence.create(checkType(internalFromClassName(className))));
    }

    public static Symbol<Type> forPrimitive(JavaKind kind) {
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
    Symbol<Type> parse(Symbol<? extends Descriptor> descriptor, int beginIndex, boolean slashes) throws ClassFormatError {
        int endIndex = skipValidTypeDescriptor(descriptor, beginIndex, slashes);
        if (endIndex == beginIndex + 1) {
            try {
                return forPrimitive(JavaKind.fromPrimitiveOrVoidTypeChar((char) descriptor.byteAt(beginIndex)));
            } catch (IllegalArgumentException e) {
                throw new ClassFormatError("invalid descriptor: " + descriptor);
            }
        }
        return symbols.symbolify(descriptor.substring(beginIndex, endIndex));
    }

    /**
     * Verifies that a valid type descriptor is at {@code beginIndex} in {@code type}.
     *
     * @param slashes specifies if package components are separated by {@code '/'} or {@code '.'}
     * @return the index one past the valid type descriptor starting at {@code beginIndex}
     * @throws ClassFormatError if there is no valid type descriptor
     */
    @TruffleBoundary
    static int skipValidTypeDescriptor(Symbol<? extends Descriptor> descriptor, int beginIndex, boolean slashes) throws ClassFormatError {
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
    public Symbol<Type> arrayOf(Symbol<Type> type, int dimensions) {
        assert dimensions > 0;
        if (Types.getArrayDimensions(type) + dimensions > 255) {
            throw new ClassFormatError("Array type with more than 255 dimensions");
        }
        // Prepend #dimensions '[' to type descriptor.
        byte[] bytes = new byte[type.length() + dimensions];
        Arrays.fill(bytes, 0, dimensions, (byte) '[');
        Symbol.copyBytes(type, 0, bytes, dimensions, type.length());
        return symbols.symbolify(ByteSequence.wrap(bytes));
    }

    public Symbol<Type> arrayOf(Symbol<Type> type) {
        return arrayOf(type, 1);
    }

    private static int skipClassName(Symbol<? extends Descriptor> descriptor, int from, final char separator) throws ClassFormatError {
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

    public static boolean isReference(Symbol<Type> type) {
        return type.length() > 1;
    }

    public static boolean isPrimitive(ByteSequence type) {
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
    public static JavaKind getJavaKind(Symbol<Type> type) {
        if (type.length() == 1) {
            return JavaKind.fromPrimitiveOrVoidTypeChar((char) type.byteAt(0));
        }
        return JavaKind.Object;
    }

    /**
     * Gets the number of array dimensions in this type descriptor.
     */
    public static int getArrayDimensions(ByteSequence type) {
        int dims = 0;
        while (dims < type.length() && type.byteAt(dims) == '[') {
            dims++;
        }
        return dims;
    }

    private static boolean isValid(Symbol<Type> type) {
        if (type.length() == 0) {
            return false;
        }
        if (type.length() == 1) {
            return isPrimitive(type);
        }
        char first = (char) type.byteAt(0);
        int beginIndex;
        if (first == '[') {
            beginIndex = 0;
            while (beginIndex < type.length() && type.byteAt(beginIndex) == '[') {
                ++beginIndex;
            }
        } else if (first == 'L') {
            beginIndex = 0;
        } else {
            return false;
        }
        int endIndex = skipValidTypeDescriptor(type, beginIndex, true);
        return endIndex == type.length();
    }

    public static boolean isArray(Symbol<Type> type) {
        assert type.length() > 0 : "empty symbol";
        return type.length() > 0 && type.byteAt(0) == '[';
    }

    public Symbol<Type> getComponentType(Symbol<Type> type) {
        if (isArray(type)) {
            return symbols.symbolify(type.substring(1));
        }
        return null;
    }

    public Symbol<Type> getElementalType(Symbol<Type> type) {
        if (isArray(type)) {
            return symbols.symbolify(type.substring(getArrayDimensions(type)));
        }
        return type; // null?
    }

    public Symbol<Type> fromClass(Class<?> clazz) {
        // TODO(peterssen): checkType is not needed here, just testing Class to Symbol<Type>
        // conversion.
        return symbols.symbolify(ByteSequence.create(checkType(internalFromClassName(clazz.getName()))));
    }

    static ByteSequence checkType(ByteSequence sequence) {
        // FIXME(peterssen): Do check.
        return Validation.validTypeDescriptor(sequence, true) ? sequence : null;
    }

    public static String checkType(String type) {
        // FIXME(peterssen): Do check.
        if (type.length() <= 0) {
            throw EspressoError.unimplemented("boom");
        }
        return type;
        // throw EspressoError.unimplemented();
    }

    public static String binaryName(Symbol<Type> type) {
        if (isArray(type)) {
            return type.toString().replace('/', '.');
        }
        if (isPrimitive(type)) {
            return getJavaKind(type).getJavaName();
        }
        return type.substring(1, type.length() - 1).toString().replace('/', '.');
    }

    @SuppressWarnings("unchecked")
    public static Symbol<Type> fromSymbol(Symbol<?> symbol) {
        Symbol<Type> type = (Symbol<Type>) symbol;
        assert isValid(type) : "Type validity should have been checked beforehand";
        return type;
    }

    public Symbol<Type> fromName(Symbol<Name> name) {
        if (name.byteAt(0) == '[') {
            // TODO(peterssen): Verify . or / separators.
            return fromSymbol(name);
        }
        byte[] bytes = new byte[name.length() + 2]; // + L;
        Symbol.copyBytes(name, 0, bytes, 1, name.length());
        bytes[0] = 'L';
        bytes[bytes.length - 1] = ';';
        ByteSequence wrap = ByteSequence.wrap(bytes);
        assert checkType(wrap) != null : "Type validity should have been checked beforehand";
        return symbols.symbolify(wrap);
    }

    public Symbol<Type> lookup(String type) {
        return symbols.lookup(checkType(type));
    }

    public static ByteSequence getRuntimePackage(ByteSequence symbol) {
        if (symbol.byteAt(0) == '[') {
            int arrayDimensions = getArrayDimensions(symbol);
            return getRuntimePackage(symbol.subSequence(arrayDimensions, symbol.length() - arrayDimensions));
        }
        if (symbol.byteAt(0) != 'L') {
            assert isPrimitive(symbol);
            return EMPTY;
        }
        int lastSlash = symbol.lastIndexOf((byte) '/');
        if (lastSlash < 0) {
            return EMPTY;
        }
        return symbol.subSequence(1, lastSlash - 1);
    }
}
