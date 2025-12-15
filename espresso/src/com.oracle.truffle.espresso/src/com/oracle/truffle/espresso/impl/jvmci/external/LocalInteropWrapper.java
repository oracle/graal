/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.impl.KeysArray;

@ExportLibrary(InteropLibrary.class)
public final class LocalInteropWrapper implements TruffleObject {
    private final Local local;

    public LocalInteropWrapper(Local local) {
        this.local = local;
    }

    private static final KeysArray<String> ALL_MEMBERS;
    private static final Set<String> ALL_MEMBERS_SET;

    static {
        String[] members = {
                        ReadMember.START_BCI,
                        ReadMember.END_BCI,
                        ReadMember.SLOT,
                        ReadMember.NAME,
                        ReadMember.TYPE,
        };
        ALL_MEMBERS = new KeysArray<>(members);
        ALL_MEMBERS_SET = Set.of(members);
    }

    @ExportMessage
    abstract static class ReadMember {
        static final String START_BCI = "startBCI";
        static final String END_BCI = "endBCI";
        static final String SLOT = "slot";
        static final String NAME = "name";
        static final String TYPE = "catchType";

        @Specialization(guards = "START_BCI.equals(member)")
        static int getStartBCI(LocalInteropWrapper receiver, @SuppressWarnings("unused") String member) {
            return receiver.local.getStartBCI();
        }

        @Specialization(guards = "END_BCI.equals(member)")
        static int getEndBCI(LocalInteropWrapper receiver, @SuppressWarnings("unused") String member) {
            return receiver.local.getEndBCI();
        }

        @Specialization(guards = "SLOT.equals(member)")
        static int getSlot(LocalInteropWrapper receiver, @SuppressWarnings("unused") String member) {
            return receiver.local.getSlot();
        }

        @Specialization(guards = "NAME.equals(member)")
        static String getName(LocalInteropWrapper receiver, @SuppressWarnings("unused") String member) {
            return receiver.local.getName().toString();
        }

        @Specialization(guards = "TYPE.equals(member)")
        static String getType(LocalInteropWrapper receiver, @SuppressWarnings("unused") String member) {
            return receiver.local.getTypeOrDesc().toString();
        }

        @Fallback
        @SuppressWarnings("unused")
        public static Object doUnknown(LocalInteropWrapper receiver, String member) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    @TruffleBoundary
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
