/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserTypes;

/**
 * An immutable byte string (modified-UTF8) representing internal metadata in Espresso. Symbols are
 * unique during their lifetime and can be compared for equality using reference comparison (==).
 * They are used to store various types of metadata including names, signatures, type descriptors,
 * and constant pool entries.
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li><b>Guest-Host Separation:</b> Provides clear separation between guest JVM and host JVM
 * strings</li>
 * <li><b>Memory Efficiency:</b> Uses a compact byte array representation</li>
 * <li><b>Performance:</b> Enables fast reference equality comparison (==) due to global
 * uniqueness</li>
 * <li><b>Deduplication:</b> Automatically handles string deduplication to save memory</li>
 * <li><b>Zero-Cost Type Safety:</b> Uses generic type parameters for compile-time type
 * checking</li>
 * <li><b>Copy Optimization:</b> Data is copied only once during symbol creation</li>
 * <li><b>Compilation Benefits:</b> Contents are {@link CompilationFinal} for optimal partial
 * evaluation</li>
 * </ul>
 *
 * <h2>Type Safety Tags</h2> The Symbol class uses generic type parameters for enhanced type safety
 * with no runtime overhead:
 * <ul>
 * <li>{@code Symbol<Name>} - For identifiers (field, class, or method names)
 *
 * <pre>
 * Example: Symbol&lt;Name&gt; methodName = ...
 * </pre>
 *
 * </li>
 * <li>{@code Symbol<ModifiedUTF8>} - For constant pool UTF-8 strings
 *
 * <pre>
 * Example: Symbol&lt;ModifiedUTF8&gt; constString = ...
 * </pre>
 *
 * </li>
 * <li>{@code Symbol<? extends Descriptor>} - For type or signature descriptors
 *
 * <pre>
 * Example: Symbol&lt;? extends Descriptor&gt; desc = ...
 * </pre>
 *
 * </li>
 * <li>{@code Symbol<Signature>} - For method/field signature descriptors
 *
 * <pre>
 * Example: Symbol&lt;Signature&gt; methodSignature = ...
 * </pre>
 *
 * </li>
 * <li>{@code Symbol<Type>} - For type descriptors in internal form
 *
 * <pre>
 * Example: Symbol&lt;Type&gt; typeDescriptor = ...
 * </pre>
 *
 * </li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * // Creating a method name symbol
 * Symbol&lt;Name&gt; methodName = ...;
 *
 * // Creating a method signature symbol
 * Symbol&lt;Signature&gt; signature = ...;
 *
 * // Fast equality comparison
 * if (methodName == otherMethodName) {
 *     // Symbols are identical
 * }
 * </pre>
 *
 * <h2>Important Notes</h2>
 * <ul>
 * <li><b>Thread Safety Warning:</b> Do not synchronize on symbols. Class loading operations rely on
 * exclusive ownership of symbol monitors, and synchronizing on symbols may cause deadlocks.</li>
 * <li><b>Immutability:</b> Symbols are immutable once created. Any modification attempts will
 * result in a new Symbol instance.</li>
 * <li><b>Memory Management:</b> Symbols are managed by the symbol table and should not be created
 * directly using constructors.</li>
 * </ul>
 *
 * @param <T> Generic type parameter for compile-time type safety checking. The type parameter
 *            should be one of: {@link Name}, {@link ModifiedUTF8}, {@link Descriptor},
 *            {@link Signature}, or {@link Type}.
 *
 * @see ByteSequence
 * @see Name
 * @see ModifiedUTF8
 * @see Descriptor
 * @see Signature
 * @see Type
 */
public final class Symbol<T> extends ByteSequence {

    @SuppressWarnings("rawtypes") public static final Symbol[] EMPTY_ARRAY = new Symbol[0];

    private byte validationCache;

    /**
     * Creates a new Symbol with the specified byte array and pre-computed hash code.
     *
     * @param bytes The byte array containing the symbol data
     * @param hashCode Pre-computed hash code for the symbol
     */
    Symbol(byte[] bytes, int hashCode) {
        super(bytes, hashCode);
    }

    /**
     * Creates a new Symbol with the specified byte array, computing its hash code.
     *
     * @param value The byte array containing the symbol data
     */
    Symbol(byte[] value) {
        this(value, ByteSequence.hashOfRange(value, 0, value.length));
    }

    /**
     * Returns an empty array of symbols with the specified type parameter.
     *
     * @param <S> The type parameter for the empty array
     * @return An empty array of symbols
     */
    @SuppressWarnings("unchecked")
    public static <S> Symbol<S>[] emptyArray() {
        return EMPTY_ARRAY;
    }

    /**
     * Returns the byte at the specified index in this symbol.
     *
     * @param index The index of the byte to retrieve
     * @return The byte value at the specified index
     */
    @Override
    public byte byteAt(int index) {
        return value[index];
    }

    /**
     * Returns the length of this symbol in bytes.
     *
     * @return The number of bytes in this symbol
     */
    @Override
    public int length() {
        return value.length;
    }

    /**
     * Returns the offset of this symbol's data in its backing array. Always returns 0 as Symbol
     * instances use their entire backing array.
     *
     * @return Always returns 0
     */
    @Override
    int offset() {
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Symbol<?> that) {
            assert this == that || !super.equals(that);
            return this == that;
        }
        return super.equals(other);
    }

    // Validation

    private static final byte VALID_CLASS_NAME = 0x01;
    private static final byte VALID_METHOD_NAME = 0x02;
    private static final byte VALID_FIELD_NAME = 0x04;
    private static final byte VALID_SIGNATURE = 0x08;
    private static final byte VALID_TYPE = 0x10;
    private static final byte VALID_UTF8 = 0x20;

    public Symbol<ModifiedUTF8> validate() throws ValidationException {
        return validateUTF8().unsafeCast();
    }

    public Symbol<? extends ModifiedUTF8> validateUTF8() throws ValidationException {
        if ((validationCache & VALID_UTF8) == 0) {
            if (!Validation.validModifiedUTF8(this)) {
                throw ValidationException.raise("Ill-formed modified-UTF8 entry");
            }
            validationCache |= VALID_UTF8;
        }
        return unsafeCast();
    }

    @SuppressWarnings("unchecked")
    private <R> Symbol<R> unsafeCast() {
        return (Symbol<R>) this;
    }

    public Symbol<Name> validateClassName() throws ValidationException {
        validateUTF8();
        if ((validationCache & VALID_CLASS_NAME) == 0) {
            if (!Validation.validClassNameEntry(this)) {
                throw ValidationException.raise("Invalid class name entry: " + this);
            }
            validationCache |= VALID_CLASS_NAME;
        }
        return unsafeCast();
    }

    public Symbol<Type> validateType(boolean allowVoid) throws ValidationException {
        validateUTF8();
        if ((validationCache & VALID_TYPE) == 0) {
            if (!Validation.validTypeDescriptor(this, true)) {
                throw ValidationException.raise("Invalid type descriptor: " + this);
            }
            validationCache |= VALID_TYPE;
        }
        if (!allowVoid && (this == ParserTypes._void)) {
            throw ValidationException.raise("Invalid type descriptor (void not allowed)");
        }
        return unsafeCast();
    }

    public Symbol<Name> validateMethodName() throws ValidationException {
        validateUTF8();
        if ((validationCache & VALID_METHOD_NAME) == 0) {
            if (!Validation.validMethodName(this)) {
                throw ValidationException.raise("Invalid method name: " + this);
            }
            validationCache |= VALID_METHOD_NAME;
        }
        return unsafeCast();
    }

    public Symbol<Name> validateFieldName() throws ValidationException {
        validateUTF8();
        if ((validationCache & VALID_FIELD_NAME) == 0) {
            if (!Validation.validUnqualifiedName(this)) {
                throw ValidationException.raise("Invalid field name: " + this);
            }
            validationCache |= VALID_FIELD_NAME;
        }
        return unsafeCast();
    }

    public Symbol<Signature> validateSignature() throws ValidationException {
        validateUTF8();
        if ((validationCache & VALID_SIGNATURE) == 0) {
            if (!Validation.validSignatureDescriptor(this)) {
                throw ValidationException.raise("Invalid signature descriptor: " + this);
            }
            validationCache |= VALID_SIGNATURE;
        }
        return unsafeCast();
    }

    public int validateSignatureGetSlots() throws ValidationException {
        validateUTF8();
        int slots = Validation.validSignatureDescriptorGetSlots(this);
        if (slots < 0) {
            throw ValidationException.raise("Invalid signature descriptor: " + this);
        }
        if ((validationCache & VALID_CLASS_NAME) == 0) {
            validationCache |= VALID_SIGNATURE;
        }
        return slots;
    }
}
