/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.espresso.classfile.descriptors;

import static com.oracle.truffle.espresso.classfile.descriptors.ByteSequence.EMPTY;
import static com.oracle.truffle.espresso.classfile.descriptors.ByteSequence.wrap;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.ParserConstantPool;
import com.oracle.truffle.espresso.classfile.ParserException;

/**
 * Manages creation and parsing of type descriptors ("field descriptors" in the JVMS).
 *
 * @see "https://docs.oracle.com/javase/specs/jvms/se10/html/jvms-4.html#jvms-4.3.2"
 */
public final class TypeSymbols {

    private final Symbols symbols;

    public TypeSymbols(Symbols symbols) {
        this.symbols = symbols;
    }

    /**
     * Creates or retrieves a Type symbol for a valid type descriptor string.
     *
     * @param typeString The type descriptor string to process
     * @return A Symbol representing the type, or null if the descriptor is invalid
     */
    public Symbol<Type> getOrCreateValidType(String typeString) {
        return getOrCreateValidType(ByteSequence.create(typeString));
    }

    /**
     * Creates or retrieves a Type symbol for a valid type descriptor byte sequence.
     *
     * @param typeBytes The type descriptor byte sequence to process
     * @return A Symbol representing the type, or null if the descriptor is invalid
     */
    public Symbol<Type> getOrCreateValidType(ByteSequence typeBytes) {
        return getOrCreateValidType(typeBytes, false);
    }

    /**
     * Creates or retrieves a Type symbol for a valid type descriptor byte sequence.
     *
     * @param ensureStrongReference if {@code true}, the returned symbol is guaranteed to be
     *            strongly referenced by the symbol table
     * @param typeBytes The type descriptor byte sequence to process
     * @return A Symbol representing the type, or null if the descriptor is invalid
     */
    public Symbol<Type> getOrCreateValidType(ByteSequence typeBytes, boolean ensureStrongReference) {
        if (Validation.validTypeDescriptor(typeBytes, true)) {
            return symbols.getOrCreate(typeBytes, ensureStrongReference);
        } else {
            return null;
        }
    }

    /**
     * Looks up an existing Type symbol for a valid type descriptor string.
     *
     * @param typeString The type descriptor string to look up
     * @return The existing Symbol for the type, or null if not found or invalid
     *
     * @see Validation#validTypeDescriptor(ByteSequence, boolean)
     */
    public Symbol<Type> lookupValidType(String typeString) {
        ByteSequence byteSequence = ByteSequence.create(typeString);
        return lookupValidType(byteSequence);
    }

    /**
     * Looks up an existing Type symbol for a valid type descriptor byte sequence.
     *
     * @param typeBytes The type descriptor byte sequence to look up
     * @return The existing Symbol for the type, or null if not found or invalid
     *
     * @see Validation#validTypeDescriptor(ByteSequence, boolean)
     */
    public Symbol<Type> lookupValidType(ByteSequence typeBytes) {
        if (Validation.validTypeDescriptor(typeBytes, true)) {
            return symbols.lookup(typeBytes);
        } else {
            return null;
        }
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

    public static ByteSequence hiddenClassName(Symbol<Type> type, long id) {
        ByteSequence name = typeToName(type);
        int idSize = ByteSequence.positiveLongStringSize(id);
        int length = type.length() - 2 + 1 + idSize;
        byte[] newBytes = new byte[length];
        name.writeTo(newBytes, 0);
        int sepIndex = name.length();
        newBytes[sepIndex] = '+';
        ByteSequence.writePositiveLongString(id, newBytes, sepIndex + 1, idSize);
        ByteSequence result = wrap(newBytes, 0, length);
        assert Validation.validModifiedUTF8(result) : String.format("Not valid anymore: %s + %d -> %s", type.toHexString(), id, result.toHexString());
        return result;
    }

    public static ByteSequence typeToName(Symbol<Type> type) {
        assert type.byteAt(0) == 'L';
        assert type.byteAt(type.length() - 1) == ';';
        return type.subSequence(1, type.length() - 1);
    }

    @TruffleBoundary
    public Symbol<Type> fromDescriptorString(String desc) {
        JavaKind kind = JavaKind.fromTypeString(desc);
        if (kind.isPrimitive()) {
            return TypeSymbols.forPrimitive(kind);
        }
        return fromClassGetName(desc);
    }

    @TruffleBoundary
    public Symbol<Type> fromClassGetName(String className) {
        String internalName = internalFromClassName(className);
        ByteSequence byteSequence = ByteSequence.create(internalName);
        ErrorUtil.guarantee(Validation.validTypeDescriptor(byteSequence, true), "invalid type");
        return symbols.getOrCreate(byteSequence);
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
     * @throws ParserException.ClassFormatError if the type descriptor is not valid
     */
    Symbol<Type> parse(Symbol<? extends Descriptor> descriptor, int beginIndex, boolean slashes) throws ParserException.ClassFormatError {
        int endIndex = skipValidTypeDescriptor(descriptor, beginIndex, slashes);
        if (endIndex == beginIndex + 1) {
            try {
                return forPrimitive(JavaKind.fromPrimitiveOrVoidTypeChar((char) descriptor.byteAt(beginIndex)));
            } catch (IllegalStateException e) {
                throw new ParserException.ClassFormatError("invalid descriptor: " + descriptor);
            }
        }
        return symbols.getOrCreate(descriptor.subSequence(beginIndex, endIndex));
    }

    /**
     * Verifies that a valid type descriptor is at {@code beginIndex} in {@code type}.
     *
     * @param slashes specifies if package components are separated by {@code '/'} or {@code '.'}
     * @return the index one past the valid type descriptor starting at {@code beginIndex}
     * @throws ParserException.ClassFormatError if there is no valid type descriptor
     */
    @TruffleBoundary
    static int skipValidTypeDescriptor(Symbol<? extends Descriptor> descriptor, int beginIndex, boolean slashes) throws ParserException.ClassFormatError {
        if (beginIndex >= descriptor.length()) {
            throw new ParserException.ClassFormatError("invalid type descriptor: " + descriptor);
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
                throw new ParserException.ClassFormatError("Invalid Java name " + descriptor.subSequence(beginIndex));
            }
            case '[': {
                // compute the number of dimensions
                int index = beginIndex;
                while (index < descriptor.length() && descriptor.byteAt(index) == '[') {
                    index++;
                }
                final int dimensions = index - beginIndex;
                if (dimensions > 255) {
                    throw new ParserException.ClassFormatError("Array with more than 255 dimensions " + descriptor.subSequence(beginIndex));
                }
                return skipValidTypeDescriptor(descriptor, index, slashes);
            }
        }
        throw new ParserException.ClassFormatError("Invalid type descriptor " + descriptor.subSequence(beginIndex));
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
        if (TypeSymbols.getArrayDimensions(type) + dimensions > 255) {
            throw new ParserException.ClassFormatError("Array type with more than 255 dimensions");
        }
        // Prepend #dimensions '[' to type descriptor.
        byte[] bytes = new byte[type.length() + dimensions];
        Arrays.fill(bytes, 0, dimensions, (byte) '[');
        type.writeTo(bytes, dimensions);
        return symbols.getOrCreate(ByteSequence.wrap(bytes));
    }

    public Symbol<Type> arrayOf(Symbol<Type> type) {
        return arrayOf(type, 1);
    }

    private static int skipClassName(Symbol<? extends Descriptor> descriptor, int from, final char separator) throws ParserException.ClassFormatError {
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

    public static boolean isReference(ByteSequence type) {
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

    public static boolean isArray(Symbol<Type> type) {
        assert type.length() > 0 : "empty symbol";
        return type.length() > 0 && type.byteAt(0) == '[';
    }

    public Symbol<Type> getComponentType(Symbol<Type> type) {
        if (isArray(type)) {
            return symbols.getOrCreate(type.subSequence(1));
        }
        return null;
    }

    public Symbol<Type> getElementalType(Symbol<Type> type) {
        if (isArray(type)) {
            return symbols.getOrCreate(type.subSequence(getArrayDimensions(type)));
        }
        return type; // null?
    }

    public Symbol<Type> fromClass(Class<?> clazz) {
        ByteSequence descriptor = ByteSequence.create(internalFromClassName(clazz.getName()));
        assert Validation.validTypeDescriptor(descriptor, true) : descriptor;
        return symbols.getOrCreate(descriptor);
    }

    public static String binaryName(Symbol<Type> type) {
        if (isArray(type)) {
            return type.toString().replace('/', '.');
        }
        if (isPrimitive(type)) {
            return getJavaKind(type).getJavaName();
        }
        return type.subSequence(1, type.length() - 1).toString().replace('/', '.');
    }

    @SuppressWarnings("unchecked")
    public static Symbol<Type> fromSymbol(Symbol<?> symbol) {
        ErrorUtil.guarantee(Validation.validTypeDescriptor(symbol, true), "invalid type");
        return (Symbol<Type>) symbol;
    }

    @SuppressWarnings("unchecked")
    public static Symbol<Type> fromSymbolUnsafe(Symbol<?> symbol) {
        assert Validation.validTypeDescriptor(symbol, true) : symbol;
        return (Symbol<Type>) symbol;
    }

    @SuppressWarnings("unchecked")
    public static Symbol<Type> fromDescriptorUnsafe(Symbol<? extends Descriptor> descriptor) {
        assert Validation.validTypeDescriptor(descriptor, true) : descriptor;
        return (Symbol<Type>) descriptor;
    }

    /**
     * Converts a name symbol, as appear in constant pool entries to a type symbol. For historical
     * reasons, class name entries are not in internal form. For classes, the class name entry is
     * {@code java/lang/Thread} instead of {@code Ljava/lang/Thread;}. For arrays, the class name is
     * already in internal form.
     */
    public Symbol<Type> fromClassNameEntry(Symbol<Name> name) {
        ErrorUtil.guarantee(Validation.validClassNameEntry(name), "invalid class name entry");
        // There are no "name" symbols for primitives, only "type" symbols.
        // That's why primitives cannot be represented here.
        // The entry "V" refers to the class "LV;" and not the void.class.
        if (name.byteAt(0) == '[') {
            return fromSymbol(name);
        }
        return symbols.getOrCreate(nameToType(name));
    }

    public Symbol<Type> fromClassNameEntryUnsafe(Symbol<Name> name) {
        assert Validation.validClassNameEntry(name) : name;
        // There are no "name" symbols for primitives, only "type" symbols.
        // That's why primitives cannot be represented here.
        // The entry "V" refers to the class "LV;" and not the void.class.
        if (name.byteAt(0) == '[') {
            return fromSymbolUnsafe(name);
        }
        return symbols.getOrCreate(nameToType(name));
    }

    public static ByteSequence nameToType(ByteSequence name) {
        if (name.byteAt(0) == '[') {
            assert Validation.validTypeDescriptor(name, true) : "Type validity should have been checked beforehand: " + name;
            return name;
        }
        byte[] bytes = new byte[name.length() + 2]; // + L;
        name.writeTo(bytes, 1);
        bytes[0] = 'L';
        bytes[bytes.length - 1] = ';';
        ByteSequence wrap = ByteSequence.wrap(bytes);
        assert Validation.validTypeDescriptor(wrap, true) : wrap;
        return wrap;
    }

    /**
     * Reverse operation of {@link #fromClassNameEntry(Symbol)}. This conversion is <b>NOT</b> valid
     * for primitive types, to avoid ambiguity e.g. LI; vs I
     */
    public static ByteSequence toClassNameEntry(Symbol<Type> type) {
        assert !isPrimitive(type);
        if (isArray(type)) {
            return type;
        }
        assert type.byteAt(0) == 'L';
        assert type.byteAt(type.length() - 1) == ';';
        return type.subSequence(1, type.length() - 1);
    }

    /**
     * Reverse operation of {@link #fromClassNameEntry(Symbol)}. This conversion is <b>NOT</b> valid
     * for primitive types, to avoid ambiguity e.g. LI; vs I
     */
    public static Symbol<Name> toClassNameEntry(Symbol<Type> type, ParserConstantPool.Symbolify<Name> symbolify) {
        ByteSequence className = toClassNameEntry(type);
        return symbolify.apply(className);
    }

    public static ByteSequence getRuntimePackage(ByteSequence symbol) {
        if (symbol.byteAt(0) == '[') {
            int arrayDimensions = getArrayDimensions(symbol);
            return getRuntimePackage(symbol.subSequence(arrayDimensions));
        }
        if (symbol.byteAt(0) != 'L') {
            assert isPrimitive(symbol);
            return EMPTY;
        }
        int lastSlash = symbol.lastIndexOf((byte) '/');
        if (lastSlash < 0) {
            return EMPTY;
        }
        return symbol.subSequence(1, lastSlash);
    }

    /**
     * Returns the number of stack slots used by the specified type.
     */
    public static int slotCount(Symbol<Type> type) {
        switch (type.byteAt(0)) {
            case 'V': // void
                return 0;
            case 'D': // double
            case 'J': // long
                return 2;
            default: // ZBCSIF[L
                return 1;
        }
    }
}
