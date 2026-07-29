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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.classfile.attributes.Attribute;
import com.oracle.truffle.espresso.classfile.attributes.AttributedElement;
import com.oracle.truffle.espresso.classfile.attributes.RecordAttribute.RecordComponentInfo;
import com.oracle.truffle.espresso.impl.KeysArray;

/** Interop handle for class-file record-component metadata used by external JVMCI. */
@ExportLibrary(InteropLibrary.class)
public final class RecordComponentInteropWrapper implements TruffleObject, AttributedElement {
    private final RecordComponentInfo recordComponentInfo;

    /** Creates an interop handle for {@code recordComponentInfo}. */
    public RecordComponentInteropWrapper(RecordComponentInfo recordComponentInfo) {
        this.recordComponentInfo = recordComponentInfo;
    }

    @Override
    public Attribute[] getAttributes() {
        return recordComponentInfo.getAttributes();
    }

    private static final KeysArray<String> ALL_MEMBERS;
    private static final Set<String> ALL_MEMBERS_SET;

    static {
        String[] members = {
                        ReadMember.NAME_INDEX,
                        ReadMember.DESCRIPTOR_INDEX,
        };
        ALL_MEMBERS = new KeysArray<>(members);
        ALL_MEMBERS_SET = Set.of(members);
    }

    @ExportMessage
    abstract static class ReadMember {
        static final String NAME_INDEX = "nameIndex";
        static final String DESCRIPTOR_INDEX = "descriptorIndex";

        @Specialization(guards = "NAME_INDEX.equals(member)")
        static int getNameIndex(RecordComponentInteropWrapper receiver, @SuppressWarnings("unused") String member) {
            return receiver.recordComponentInfo.getNameIndex();
        }

        @Specialization(guards = "DESCRIPTOR_INDEX.equals(member)")
        static int getDescriptorIndex(RecordComponentInteropWrapper receiver, @SuppressWarnings("unused") String member) {
            return receiver.recordComponentInfo.getDescriptorIndex();
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doUnknown(RecordComponentInteropWrapper receiver, String member) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("static-method")
    boolean isMemberReadable(String member) {
        return ALL_MEMBERS_SET.contains(member);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return ALL_MEMBERS;
    }
}
