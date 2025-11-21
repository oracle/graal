/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.KeysArray;
import com.oracle.truffle.espresso.impl.Klass;

/**
 * Interop wrapper used when returning types of unknown kind (primitive, array, instance). See
 * {@code com.oracle.truffle.espresso.vmaccess.EspressoExternalVMAccess.toResolvedJavaType}.
 */
@ExportLibrary(InteropLibrary.class)
public class TypeWrapper implements TruffleObject {
    private static final KeysArray<String> ALL_MEMBERS;
    private static final Set<String> ALL_MEMBERS_SET;

    private final Klass klass;

    static {
        String[] members = {
                        ReadMember.KIND,
                        ReadMember.ELEMENTAL,
                        ReadMember.DIMENSIONS,
                        ReadMember.META,
        };
        ALL_MEMBERS = new KeysArray<>(members);
        ALL_MEMBERS_SET = Set.of(members);
    }

    public TypeWrapper(Klass klass) {
        this.klass = klass;
    }

    @ExportMessage
    abstract static class ReadMember {
        static final String KIND = "kind";
        static final String ELEMENTAL = "elemental";
        static final String DIMENSIONS = "dimensions";
        static final String META = "meta";

        @Specialization(guards = "KIND.equals(member)")
        static int kind(TypeWrapper receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            if (receiver.klass.isArray()) {
                return '[';
            }
            return receiver.klass.getJavaKind().getTypeChar();
        }

        @Specialization(guards = "ELEMENTAL.equals(member)")
        static TypeWrapper elemental(TypeWrapper receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            return new TypeWrapper(receiver.klass.getElementalType());
        }

        @Specialization(guards = "DIMENSIONS.equals(member)")
        static int dimensions(TypeWrapper receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            if (receiver.klass instanceof ArrayKlass arrayKlass) {
                return arrayKlass.getDimension();
            }
            return 0;
        }

        @Specialization(guards = "META.equals(member)")
        static Klass meta(TypeWrapper receiver, @SuppressWarnings("unused") String member) {
            assert EspressoLanguage.get(null).isExternalJVMCIEnabled();
            return receiver.klass;
        }

        @Fallback
        public static Object doUnknown(@SuppressWarnings("unused") TypeWrapper receiver, String member) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @CompilerDirectives.TruffleBoundary
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
