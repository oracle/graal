/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl.jvmci.external;

import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.KeysArray;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.jvmci.JVMCIConstantPoolUtils.BootstrapMethodInvocationBuilder;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * Interop object used to build {@code BootstrapMethodInvocation} objects in
 * {@code EspressoExternalConstantPool}.
 * <p>
 * Bootstrap static arguments are encoded into the {@linkplain InteropLibrary#hasArrayElements array
 * trait} of this object. Other implementations may signal an unresolved entry by producing a raw
 * {@link Integer} corresponding to the unresolved entry's constant pool index. Since it is
 * difficult to distinguish a host {@link Integer} from a guest {@link Integer} over the interop
 * boundary, we need another way to distinguish an unresolved entry from a resolved {@code Integer}
 * entry. The following scheme is used:
 * <ul>
 * <li>If static argument {@code i} is resolved, it will be available as array element {@code 2 * i}
 * and array element {@code 2 * i + 1} will be {@linkplain InteropLibrary#isNull null}.</li>
 * <li>Otherwise the constant pool index of that static argument will be available as array element
 * {@code 2 * i + 1} and array element {@code 2 * i} will be {@linkplain InteropLibrary#isNull
 * null}.</li>
 * </ul>
 * Note that {@linkplain InteropLibrary#isNull null} might be a valid, resolved, static argument
 * value (e.g., as a constant-dynamic result). As a result the only safe way to distinguish a
 * resolved and an unresolved case is to check whether {@code 2 * i + 1} is
 * {@linkplain InteropLibrary#isNull null}.
 */
@ExportLibrary(InteropLibrary.class)
public final class InteropBootstrapMethodInvocation implements TruffleObject, BootstrapMethodInvocationBuilder {
    private static final KeysArray<String> ALL_MEMBERS;
    private static final Set<String> ALL_MEMBERS_SET;

    static {
        String[] members = {
                        ReadMember.TYPE,
                        ReadMember.IS_INDY,
                        ReadMember.NAME,
                        ReadMember.BOOTSTRAP_METHOD,
                        ReadMember.CPI,
                        ReadMember.METHOD_HANDLE_INDEX,
                        ReadMember.STATIC_ARGUMENT_INDICES,
        };
        ALL_MEMBERS = new KeysArray<>(members);
        ALL_MEMBERS_SET = Set.of(members);
    }

    private final int entryCpi;
    private int methodHandleIndex;
    private int[] staticArgumentIndices;
    private Object[] staticArguments;
    private boolean isIndy;
    private Method bootstrapMethod;
    private Symbol<Name> name;
    private StaticObject type;

    InteropBootstrapMethodInvocation(int entryCpi) {
        this.entryCpi = entryCpi;
    }

    @Override
    public void setupStaticArguments(int length) {
        assert staticArguments == null;
        staticArguments = new Object[length * 2];
    }

    @Override
    public void staticArgument(int i, StaticObject value) {
        assert staticArguments[i * 2] == null && staticArguments[i * 2 + 1] == null : "staticArguments@%d = [%s, %s]".formatted(i * 2, staticArguments[i * 2], staticArguments[i * 2 + 1]);
        staticArguments[i * 2] = value;
        staticArguments[i * 2 + 1] = StaticObject.NULL;
    }

    @Override
    public void staticArgumentUnresolvedDynamic(int i, int cpi) {
        assert staticArguments[i * 2] == null && staticArguments[i * 2 + 1] == null : "staticArguments@%d = [%s, %s]".formatted(i * 2, staticArguments[i * 2], staticArguments[i * 2 + 1]);
        staticArguments[i * 2] = StaticObject.NULL;
        staticArguments[i * 2 + 1] = cpi;
    }

    @Override
    public void finalize(boolean finalIsIndy, Method finalBootstrapMethod, Symbol<Name> finalName, StaticObject finalType, int finalMethodHandleIndex, int[] finalStaticArgumentIndices) {
        assert !this.isIndy;
        assert this.bootstrapMethod == null;
        assert this.name == null;
        assert this.type == null;
        this.isIndy = finalIsIndy;
        this.bootstrapMethod = finalBootstrapMethod;
        this.name = finalName;
        this.type = finalType;
        this.methodHandleIndex = finalMethodHandleIndex;
        this.staticArgumentIndices = finalStaticArgumentIndices;
    }

    boolean isInitialised() {
        return this.staticArgumentIndices != null;
    }

    @ExportMessage
    abstract static class ReadMember {
        static final String TYPE = "type";
        static final String IS_INDY = "isIndy";
        static final String NAME = "name";
        static final String BOOTSTRAP_METHOD = "bootstrapMethod";
        static final String CPI = "cpi";
        static final String METHOD_HANDLE_INDEX = "methodHandleIndex";
        static final String STATIC_ARGUMENT_INDICES = "staticArgumentIndices";

        @Specialization(guards = "METHOD_HANDLE_INDEX.equals(member)")
        static int methodHandleIndex(InteropBootstrapMethodInvocation receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            return receiver.methodHandleIndex;
        }

        @Specialization(guards = "STATIC_ARGUMENT_INDICES.equals(member)")
        static Object staticArgumentIndices(InteropBootstrapMethodInvocation receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            return new StaticArgumentIndices(receiver.staticArgumentIndices);
        }

        @Specialization(guards = "TYPE.equals(member)")
        static StaticObject type(InteropBootstrapMethodInvocation receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            return receiver.type;
        }

        @Specialization(guards = "IS_INDY.equals(member)")
        static boolean isIndy(InteropBootstrapMethodInvocation receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            return receiver.isIndy;
        }

        @Specialization(guards = "NAME.equals(member)")
        static String name(InteropBootstrapMethodInvocation receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            return receiver.name.toString();
        }

        @Specialization(guards = "BOOTSTRAP_METHOD.equals(member)")
        static Object bootstrapMethod(InteropBootstrapMethodInvocation receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            return receiver.bootstrapMethod == null ? StaticObject.NULL : receiver.bootstrapMethod;
        }

        @Specialization(guards = "CPI.equals(member)")
        static int cpi(InteropBootstrapMethodInvocation receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            return receiver.entryCpi;
        }

        @Fallback
        public static Object doUnknown(@SuppressWarnings("unused") InteropBootstrapMethodInvocation receiver, String member) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class StaticArgumentIndices implements TruffleObject {
        private final int[] indices;

        StaticArgumentIndices(int[] indices) {
            this.indices = indices;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return indices.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < indices.length;
        }

        @ExportMessage
        int readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return indices[(int) index];
        }
    }

    @ExportMessage
    long getArraySize() {
        return staticArguments == null ? 0 : staticArguments.length;
    }

    @ExportMessage
    boolean isArrayElementReadable(long idx) {
        return 0 <= idx && idx < getArraySize();
    }

    @ExportMessage
    Object readArrayElement(long idx,
                    @Bind Node node,
                    @Cached InlinedBranchProfile exception) throws InvalidArrayIndexException {
        if (!isArrayElementReadable(idx)) {
            exception.enter(node);
            throw InvalidArrayIndexException.create(idx);
        }
        return staticArguments[(int) idx];
    }

    @ExportMessage
    public boolean hasArrayElements() {
        return staticArguments != null;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return ALL_MEMBERS;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @TruffleBoundary
    public boolean isMemberReadable(String member) {
        return ALL_MEMBERS_SET.contains(member);
    }
}
