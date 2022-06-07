/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.heap.instrument;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * An interop member of the memory dump. It represents a name in the dump.
 */
@ExportLibrary(InteropLibrary.class)
public class DumpMember implements TruffleObject {

    private final String name;

    DumpMember(String name) {
        this.name = name;
    }

    @ExportMessage
    boolean isMember() {
        return true;
    }

    @ExportMessage
    Object getMemberSimpleName() {
        return name;
    }

    @ExportMessage
    Object getMemberQualifiedName() {
        return name;
    }

    @ExportMessage
    boolean isMemberKindField() {
        return true;
    }

    @ExportMessage
    boolean isMemberKindMethod() {
        return false;
    }

    @ExportMessage
    boolean isMemberKindMetaObject() {
        return false;
    }

    String getName() {
        return name;
    }

    @ExportLibrary(InteropLibrary.class)
    abstract static class AbstractReader implements TruffleObject {

        abstract Object getDumpMemberObjects();

        abstract boolean isDumpMemberReadable(String name);

        abstract Object readDumpMember(String name);

        @ExportMessage
        static class IsMemberReadable {

            @Specialization
            static boolean isReadable(AbstractReader receiver, DumpMember member) {
                return receiver.isDumpMemberReadable(member.getName());
            }

            @Specialization(guards = "interop.isString(memberObj)")
            static boolean isReadable(AbstractReader receiver, Object memberObj, @Cached.Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
                try {
                    String member = interop.asString(memberObj);
                    return receiver.isDumpMemberReadable(member);
                } catch (UnsupportedMessageException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
            }

            @Fallback
            @SuppressWarnings("unused")
            static boolean isReadable(AbstractReader receiver, Object unknownObj) {
                return false;
            }
        }

        @ExportMessage
        static class ReadMember {

            @Specialization
            static Object read(AbstractReader receiver, DumpMember member) throws UnknownMemberException {
                Object value = receiver.readDumpMember(member.getName());
                if (value != null) {
                    return value;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownMemberException.create(member);
                }
            }

            @Specialization(guards = "interop.isString(memberObj)")
            static Object read(AbstractReader receiver, Object memberObj, @Cached.Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop)
                            throws UnknownMemberException, UnsupportedMessageException {
                String name = interop.asString(memberObj);
                Object value = receiver.readDumpMember(name);
                if (value != null) {
                    return value;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownMemberException.create(memberObj);
                }
            }

            @Fallback
            @SuppressWarnings("unused")
            static Object read(AbstractReader receiver, Object unknownObj) throws UnknownMemberException {
                CompilerDirectives.transferToInterpreter();
                throw UnknownMemberException.create(unknownObj);
            }
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMemberObjects() {
            return getDumpMemberObjects();
        }
    }
}
