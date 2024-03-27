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

import static java.lang.Math.max;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.descriptors.Symbol.Descriptor;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.JavaKind;

/**
 * Represents a method signature provided by the runtime.
 *
 * Two representations for method signatures :
 * <ul>
 * <li>Raw: {@link Symbol}&lt;{@link Signature}&gt;
 * <li>Parsed: {@link Symbol}&lt;{@link Type}&gt;[] which includes the return type at the end.
 * </ul>
 *
 * @see <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">Method
 *      Descriptors</a>
 */
public final class Signatures {
    private final Types types;
    private final Symbols symbols;

    private final ConcurrentHashMap<Symbol<Signature>, Symbol<Type>[]> parsedSignatures = new ConcurrentHashMap<>();

    public Signatures(Symbols symbols, Types types) {
        this.symbols = symbols;
        this.types = types;
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

    private static Symbol<Type> toBasic(Symbol<Type> t) {
        if (t == Type.java_lang_Object || Types.isArray(t)) {
            return Type.java_lang_Object;
        } else if (t == Type._int || t == Type._short || t == Type._boolean || t == Type._char) {
            return Type._int;
        } else {
            return t;
        }
    }

    public Symbol<Signature> lookupValidSignature(String signatureString) {
        if (!isValid(signatureString)) {
            return null;
        }
        return symbols.lookup(signatureString);
    }

    public Symbol<Signature> symbolifyValidSignature(String signatureString) {
        if (!isValid(signatureString)) {
            return null;
        }
        return symbols.symbolify(ByteSequence.create(signatureString));
    }

    public Types getTypes() {
        return types;
    }

    /**
     * Parses a signature descriptor string into its parameter and return type components.
     *
     * @return the parsed parameter types followed by the return type.
     * @throws ClassFormatError if {@code string} is not well formed
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Symbol<Type>[] parse(Types typeDescriptors, Symbol<Signature> signature, int startIndex) throws ClassFormatError {
        if ((startIndex > signature.length() - 3) || signature.byteAt(startIndex) != '(') {
            throw new ClassFormatError("Invalid method signature: " + signature);
        }
        final List<Symbol<Type>> buf = new ArrayList<>();
        int i = startIndex + 1;
        while (signature.byteAt(i) != ')') {
            final Symbol<Type> descriptor = typeDescriptors.parse(signature, i, true);
            buf.add(descriptor);
            i = i + descriptor.length();
            if (i >= signature.length()) {
                throw new ClassFormatError("Invalid method signature: " + signature);
            }
        }
        i++;
        final Symbol<Type> descriptor = typeDescriptors.parse(signature, i, true);
        if (i + descriptor.length() != signature.length()) {
            throw new ClassFormatError("Invalid method signature: " + signature);
        }
        final Symbol<Type>[] descriptors = buf.toArray(new Symbol[buf.size() + 1]);
        descriptors[buf.size()] = descriptor;
        return descriptors;
    }

    /**
     * Parses a raw signature descriptor into its parameter and return type components.
     *
     * @return the parsed parameter types followed by the return type.
     * @throws ClassFormatError if {@code string} is not well formed
     */
    public static int skipValidSignature(Symbol<Signature> signature, int beginIndex) throws ClassFormatError {
        if ((beginIndex > signature.length() - 3) || signature.byteAt(beginIndex) != '(') {
            throw new ClassFormatError("Invalid method signature: " + signature);
        }
        int i = beginIndex + 1;
        while (signature.byteAt(i) != ')') {
            int endIndex = Types.skipValidTypeDescriptor(signature, i, true);
            if (i >= signature.length()) {
                throw new ClassFormatError("Invalid method signature: " + signature);
            }
            i = endIndex;
        }
        i++;
        return Types.skipValidTypeDescriptor(signature, i, true);
    }

    @SuppressWarnings("unchecked")
    public static Symbol<Signature> check(Symbol<? extends Descriptor> descriptor) {
        assert isValid((Symbol<Signature>) descriptor);
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
        return Types.getJavaKind(returnType(signature));
    }

    /**
     * Gets the number of local variable slots used by the parameters + return type in this
     * signature. Long and double parameters use two slots, all other parameters use one slot.
     */
    public static int getNumberOfSlots(final Symbol<Type>[] signature) {
        int slots = 0;
        for (Symbol<Type> type : signature) {
            slots += Types.getJavaKind(type).getSlotCount();
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
     * Validates a raw signature.
     */
    public static boolean isValid(Symbol<Signature> signature) {
        int endIndex = skipValidSignature(signature, 0);
        return endIndex == signature.length();
    }

    public static boolean isValid(@SuppressWarnings("unused") String signatureString) {
        // TODO(peterssen): Implement FAST validation.
        return true;
    }

    public static Symbol<Signature> verify(Symbol<Signature> signature) {
        int endIndex = skipValidSignature(signature, 0);
        if (endIndex != signature.length()) {
            throw new ClassFormatError("Invalid signature descriptor " + signature);
        }
        return signature;
    }

    /**
     * Gets the kind of the `paramIndex`-th parameter in this (parsed) signature object.
     */
    public static JavaKind parameterKind(final Symbol<Type>[] signature, int paramIndex) {
        return Types.getJavaKind(signature[paramIndex]);
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
    public static final Symbol<Type>[] makeParsedUncached(Symbol<Type> returnType, Symbol<Type>... parameterTypes) {
        final Symbol<Type>[] signature = Arrays.copyOf(parameterTypes, parameterTypes.length + 1);
        signature[signature.length - 1] = returnType;
        return signature;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Symbol<Signature> makeRaw(Class<?> returnClass, Class<?>... parameterClasses) {
        Symbol<Type>[] parameterTypes = new Symbol[parameterClasses.length];
        for (int i = 0; i < parameterClasses.length; ++i) {
            parameterTypes[i] = getTypes().fromClass(parameterClasses[i]);
        }
        return makeRaw(getTypes().fromClass(returnClass), parameterTypes);
    }

    @SafeVarargs
    public final Symbol<Signature> makeRaw(Symbol<Type> returnType, Symbol<Type>... parameterTypes) {
        if (parameterTypes == null || parameterTypes.length == 0) {
            final byte[] bytes = new byte[2 + returnType.length()];
            returnType.writeTo(bytes, 2);
            bytes[0] = '(';
            bytes[1] = ')';
            return symbols.symbolify(ByteSequence.wrap(bytes));
        }

        int totalLength = returnType.length();
        for (Symbol<Type> param : parameterTypes) {
            totalLength += param.length();
        }

        final byte[] bytes = new byte[totalLength + 2]; // + ()

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
        return symbols.symbolify(ByteSequence.wrap(bytes));
    }

    public Symbol<Signature> getOrCreateValidSignature(String signatureString) {
        return symbols.symbolify(ByteSequence.create(checkSignature(signatureString)));
    }

    private static String checkSignature(String signatureString) {
        // FIXME(peterssen): Do check.
        return signatureString;
    }

    @SafeVarargs
    static byte[] buildSignatureBytes(Symbol<Type> returnType, Symbol<Type>... parameterTypes) {
        if (parameterTypes == null || parameterTypes.length == 0) {
            byte[] bytes = new byte[/* () */ 2 + returnType.length()];
            bytes[0] = '(';
            bytes[1] = ')';
            returnType.writeTo(bytes, 2);
            return bytes;
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
        return bytes;
    }

    public Symbol<Type>[] parsed(Symbol<Signature> signature) {
        return parsedSignatures.computeIfAbsent(signature, new Function<Symbol<Signature>, Symbol<Type>[]>() {
            @Override
            public Symbol<Type>[] apply(Symbol<Signature> key) {
                return parse(Signatures.this.getTypes(), signature, 0);
            }
        });
    }
}
