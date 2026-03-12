/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.KeysArray;

/**
 * {@linkplain InteropLibrary Interop} object used to carry parameter metadata (name and modifiers)
 * across the interop boundary to implement
 * {@code com.oracle.truffle.espresso.vmaccess.EspressoExternalResolvedJavaMethod#getParameters()}.
 */
@ExportLibrary(InteropLibrary.class)
public class ParameterInteropWrapper implements TruffleObject {
    private final Symbol<?> name;
    private final int modifiers;

    public ParameterInteropWrapper(Symbol<?> name, int modifiers) {
        this.name = name;
        this.modifiers = modifiers;
    }

    private static final KeysArray<String> ALL_MEMBERS;
    private static final Set<String> ALL_MEMBERS_SET;

    static {
        String[] members = {
                        ReadMember.NAME,
                        ReadMember.MODIFIERS,
        };
        ALL_MEMBERS = new KeysArray<>(members);
        ALL_MEMBERS_SET = Set.of(members);
    }

    @ExportMessage
    abstract static class ReadMember {
        static final String NAME = "name";
        static final String MODIFIERS = "modifiers";

        @Specialization(guards = "NAME.equals(member)")
        static String getName(ParameterInteropWrapper receiver, @SuppressWarnings("unused") String member) {
            return receiver.name.toString();
        }

        @Specialization(guards = "MODIFIERS.equals(member)")
        static int getModifiers(ParameterInteropWrapper receiver, @SuppressWarnings("unused") String member) {
            return receiver.modifiers;
        }

        @Fallback
        @SuppressWarnings("unused")
        public static Object doUnknown(ParameterInteropWrapper receiver, String member) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    @SuppressWarnings("static-method")
    public boolean isMemberReadable(String member) {
        return ALL_MEMBERS_SET.contains(member);
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
}
