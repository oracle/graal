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

import static java.lang.Math.max;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.ParserException;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserTypes;

/**
 * Represents and manages method signature symbols for the Java runtime.
 *
 * Method signatures can be represented in two forms:
 * <ul>
 * <li>Raw: {@link Symbol}&lt;{@link Signature}&gt; - The original signature string
 * <li>Parsed: {@link Symbol}&lt;{@link Type}&gt;[] - Array of parameter types with return type at
 * the end
 * </ul>
 *
 * The signature format follows the JVM specification for method descriptors.
 *
 * @see <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">Method
 *      Descriptors</a>
 */
public final class SignatureSymbols {
    private final TypeSymbols typeSymbols;
    private final Symbols symbols;

    /**
     * Creates a new SignatureSymbols instance.
     *
     * @param symbols The Symbols instance for symbol management
     * @param typeSymbols The TypeSymbols instance for handling type descriptors
     */
    public SignatureSymbols(Symbols symbols, TypeSymbols typeSymbols) {
        this.symbols = symbols;
        this.typeSymbols = typeSymbols;
    }

    /**
     * Looks up an existing valid method signature from a string.
     *
     * @param signatureString The method signature string to look up
     * @return The signature Symbol if valid and exists, null otherwise
     *
     * @see Validation#validSignatureDescriptor(ByteSequence)
     */
    public Symbol<Signature> lookupValidSignature(String signatureString) {
        return lookupValidSignature(ByteSequence.create(signatureString));
    }

    /**
     * Looks up an existing valid method signature from a byte sequence.
     *
     * @param signatureBytes The method signature bytes to look up
     * @return The signature Symbol if valid and exists, null otherwise
     *
     * @see Validation#validSignatureDescriptor(ByteSequence)
     */
    public Symbol<Signature> lookupValidSignature(ByteSequence signatureBytes) {
        if (!Validation.validSignatureDescriptor(signatureBytes)) {
            return null;
        }
        return symbols.lookup(signatureBytes);
    }

    /**
     * Creates or retrieves a valid method signature symbol from a byte sequence.
     *
     * @param signatureBytes The method signature bytes to create or retrieve
     * @return The signature Symbol if valid, null otherwise
     *
     * @see Validation#validSignatureDescriptor(ByteSequence)
     */
    public Symbol<Signature> getOrCreateValidSignature(ByteSequence signatureBytes) {
        return getOrCreateValidSignature(signatureBytes, false);
    }

    /**
     * Creates or retrieves a valid method signature symbol from a String.
     *
     * @return The signature Symbol if valid, null otherwise
     *
     * @see Validation#validSignatureDescriptor(ByteSequence)
     */
    public Symbol<Signature> getOrCreateValidSignature(String signatureString) {
        return getOrCreateValidSignature(ByteSequence.create(signatureString), false);
    }

    /**
     * Creates or retrieves a valid method signature symbol from a byte sequence.
     *
     * @param ensureStrongReference if {@code true}, the returned symbol is guaranteed to be
     *            strongly referenced by the symbol table
     * @param signatureBytes The method signature bytes to create or retrieve
     * @return The signature Symbol if valid, null otherwise
     *
     * @see Validation#validSignatureDescriptor(ByteSequence)
     */
    public Symbol<Signature> getOrCreateValidSignature(ByteSequence signatureBytes, boolean ensureStrongReference) {
        if (!Validation.validSignatureDescriptor(signatureBytes)) {
            return null;
        }
        return symbols.getOrCreate(signatureBytes, ensureStrongReference);
    }

    /**
     * Returns the TypeSymbols instance used by this SignatureSymbols.
     *
     * @return The TypeSymbols instance for handling type descriptors
     */
    public TypeSymbols getTypes() {
        return typeSymbols;
    }

    /**
     * Gets or computes the parsed form of a method signature. The parsed form is an array of Type
     * symbols where the last element is the return type and all preceding elements are parameter
     * types.
     *
     * @param signature The method signature to parse
     * @return Array of Type symbols representing parameter types followed by return type
     */
    public Symbol<Type>[] parsed(Symbol<Signature> signature) {
        return parse(SignatureSymbols.this.getTypes(), signature);
    }

    /**
     * Converts a regular signature to a basic one.
     *
     * @param raw Signature to convert
     * @param keepLastArg Whether or not to erase the last parameter.
     * @return A basic signature corresponding to @sig
     */
    @SuppressWarnings({"unchecked"})
    public Symbol<Signature> toBasic(Symbol<Signature> raw, boolean keepLastArg) {
        Symbol<Type>[] sig = parsed(raw);
        int pcount = parameterCount(sig);
        int params = max(pcount - (keepLastArg ? 0 : 1), 0);
        List<Symbol<Type>> buf = new ArrayList<>();
        for (int i = 0; i < params; i++) {
            Symbol<Type> t = parameterType(sig, i);
            if (i == params - 1 && keepLastArg) {
                buf.add(t);
            } else {
                buf.add(toBasic(t));
            }
        }

        Symbol<Type> rtype = toBasic(returnType(sig));
        return makeRaw(rtype, buf.toArray(Symbol.EMPTY_ARRAY));
    }

    private static Symbol<Type> toBasic(Symbol<Type> type) {
        if (type == ParserTypes.java_lang_Object || TypeSymbols.isArray(type)) {
            return ParserTypes.java_lang_Object;
        } else if (type == ParserTypes._int || type == ParserTypes._short || type == ParserTypes._boolean || type == ParserTypes._char) {
            return ParserTypes._int;
        } else {
            return type;
        }
    }

    /**
     * Parses a signature descriptor string into its parameter and return type components.
     *
     * @return the parsed parameter types followed by the return type.
     * @throws ParserException.ClassFormatError if {the signature is not well-formed
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Symbol<Type>[] parse(TypeSymbols typeSymbols, Symbol<Signature> signature) throws ParserException.ClassFormatError {
        if ((signature.length() < 3) || signature.byteAt(0) != '(') {
            throw new ParserException.ClassFormatError("Invalid method signature: " + signature);
        }
        final List<Symbol<Type>> buf = new ArrayList<>();
        int i = 1;
        while (signature.byteAt(i) != ')') {
            final Symbol<Type> descriptor = typeSymbols.parse(signature, i, true);
            buf.add(descriptor);
            i = i + descriptor.length();
            if (i >= signature.length()) {
                throw new ParserException.ClassFormatError("Invalid method signature: " + signature);
            }
        }
        i++;
        final Symbol<Type> descriptor = typeSymbols.parse(signature, i, true);
        if (i + descriptor.length() != signature.length()) {
            throw new ParserException.ClassFormatError("Invalid method signature: " + signature);
        }
        final Symbol<Type>[] descriptors = buf.toArray(new Symbol[buf.size() + 1]);
        descriptors[buf.size()] = descriptor;
        return descriptors;
    }

    @SuppressWarnings("unchecked")
    public static Symbol<Signature> fromDescriptor(Symbol<? extends Descriptor> descriptor) {
        ErrorUtil.guarantee(Validation.validSignatureDescriptor(descriptor), "invalid signature");
        return (Symbol<Signature>) descriptor;
    }

    @SuppressWarnings("unchecked")
    public static Symbol<Signature> fromDescriptorUnsafe(Symbol<? extends Descriptor> descriptor) {
        assert Validation.validSignatureDescriptor(descriptor) : "invalid signature " + descriptor;
        return (Symbol<Signature>) descriptor;
    }

    /**
     * Gets the type descriptor of the return type in this (parsed) signature object.
     */
    public static Symbol<Type> returnType(final Symbol<Type>[] signature) {
        return signature[signature.length - 1];
    }

    /**
     * Gets the kind of the return type in this (parsed) signature object.
     */
    public static JavaKind returnKind(final Symbol<Type>[] signature) {
        return TypeSymbols.getJavaKind(returnType(signature));
    }

    /**
     * Gets the number of local variable slots used by the parameters + return type in this
     * signature. Long and double parameters use two slots, all other parameters use one slot.
     */
    public static int getNumberOfSlots(final Symbol<Type>[] signature) {
        int slots = 0;
        for (Symbol<Type> type : signature) {
            slots += TypeSymbols.getJavaKind(type).getSlotCount();
        }
        return slots;
    }

    /**
     * Gets the number of local variable slots used by the parameters only in this parsed signature.
     * Long and double parameters use two slots, all other parameters use one slot.
     */
    @ExplodeLoop
    public static int slotsForParameters(final Symbol<Type>[] signature) {
        int slots = 0;
        int count = parameterCount(signature);
        for (int i = 0; i < count; ++i) {
            slots += parameterKind(signature, i).getSlotCount();
        }
        return slots;
    }

    /**
     * Gets the number of parameters in this (parsed) signature object.
     */
    public static int parameterCount(final Symbol<Type>[] signature) {
        return signature.length - 1;
    }

    /**
     * Gives an iterable over the signatures. Can be parameterized to iterate in reverse order, or
     * whether to include the return type.
     */
    public static Iterable<Symbol<Type>> iterable(final Symbol<Type>[] signature, boolean reverse, boolean withReturnType) {
        return new SignatureIter(signature, reverse, withReturnType);
    }

    private static final class SignatureIter implements Iterable<Symbol<Type>>, Iterator<Symbol<Type>> {
        private final Symbol<Type>[] signature;
        private final int start;
        private final int end;
        private final int step;

        private int pos;

        private SignatureIter(final Symbol<Type>[] signature, boolean reverse, boolean withReturnType) {
            this.signature = signature;
            int tmpStart = 0;
            int tmpEnd = SignatureSymbols.parameterCount(signature);
            int tmpStep = 1;
            if (withReturnType) {
                tmpEnd = signature.length;
            }
            this.pos = tmpStart;
            if (reverse) {
                tmpStep = -1;
                this.pos = tmpEnd;
            }
            this.start = tmpStart;
            this.end = tmpEnd;
            this.step = tmpStep;
        }

        @Override
        public Iterator<Symbol<Type>> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            int next = nextIdx();
            return next >= start && next < end;
        }

        @Override
        public Symbol<Type> next() {
            assert hasNext();
            int nextIdx = nextIdx();
            Symbol<Type> next = signature[nextIdx];
            this.pos = nextIdx;
            return next;
        }

        private int nextIdx() {
            return pos + step;
        }
    }

    /**
     * Gets the kind of the `paramIndex`-th parameter in this (parsed) signature object.
     */
    public static JavaKind parameterKind(final Symbol<Type>[] signature, int paramIndex) {
        return TypeSymbols.getJavaKind(signature[paramIndex]);
    }

    /**
     * Gets the type of the `paramIndex`-th parameter in this (parsed) signature object.
     */
    public static Symbol<Type> parameterType(final Symbol<Type>[] signature, int paramIndex) {
        assert paramIndex + 1 < signature.length;
        return signature[paramIndex];
    }

    @SuppressWarnings({"varargs", "rawtypes"})
    @SafeVarargs
    public static Symbol<Type>[] makeParsedUncached(Symbol<Type> returnType, Symbol<Type>... parameterTypes) {
        final Symbol<Type>[] signature = Arrays.copyOf(parameterTypes, parameterTypes.length + 1);
        signature[signature.length - 1] = returnType;
        return signature;
    }

    @SafeVarargs
    public final Symbol<Signature> makeRaw(Symbol<Type> returnType, Symbol<Type>... parameterTypes) {
        return symbols.getOrCreate(createSignature(returnType, parameterTypes));
    }

    @SafeVarargs
    static ByteSequence createSignature(Symbol<Type> returnType, Symbol<Type>... parameterTypes) {
        if (parameterTypes == null || parameterTypes.length == 0) {
            byte[] bytes = new byte[/* () */ 2 + returnType.length()];
            bytes[0] = '(';
            bytes[1] = ')';
            returnType.writeTo(bytes, 2);
            return ByteSequence.wrap(bytes);
        }

        int totalLength = returnType.length();
        for (Symbol<Type> param : parameterTypes) {
            totalLength += param.length();
        }

        byte[] bytes = new byte[totalLength + 2]; // + ()

        int pos = 0;
        bytes[pos++] = '(';
        for (Symbol<Type> param : parameterTypes) {
            param.writeTo(bytes, pos);
            pos += param.length();
        }
        bytes[pos++] = ')';
        returnType.writeTo(bytes, pos);
        pos += returnType.length();
        assert pos == totalLength + 2;
        return ByteSequence.wrap(bytes);
    }

    public static boolean returnsVoid(ByteSequence signature) {
        return signature.length() > 2 && signature.byteAt(signature.length() - 2) == ')' && signature.byteAt(signature.length() - 1) == 'V';
    }
}
