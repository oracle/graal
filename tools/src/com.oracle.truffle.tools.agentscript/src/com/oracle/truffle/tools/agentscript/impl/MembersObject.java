/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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

@SuppressWarnings({"static-method"})
@ExportLibrary(InteropLibrary.class)
final class MembersObject implements TruffleObject {

    @CompilationFinal(dimensions = 1) private final Enum<?>[] enums;

    private MembersObject(Enum<?>[] enums) {
        this.enums = enums;
    }

    @ExportMessage
    Object readArrayElement(long index) {
        return new InsightMember(enums[(int) index]);
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        return 0 <= index && index < enums.length;
    }

    @ExportMessage
    long getArraySize() {
        return enums.length;
    }

    static MembersObject wrap(Enum<?>... arr) {
        return new MembersObject(arr);
    }

    @ExportLibrary(InteropLibrary.class)
    public class InsightMember implements TruffleObject {

        private final Enum<?> nameEnum;

        InsightMember(Enum<?> nameEnum) {
            this.nameEnum = nameEnum;
        }

        @ExportMessage
        boolean isMember() {
            return true;
        }

        @ExportMessage
        Object getMemberSimpleName() {
            return getName();
        }

        @ExportMessage
        Object getMemberQualifiedName() {
            return getName();
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

        Enum<?> getEnum() {
            return nameEnum;
        }

        String getName() {
            return nameEnum.name();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    abstract static class AbstractReader implements TruffleObject {

        @SuppressWarnings("rawtypes") //
        private final Class<? extends Enum> enumClass;
        private final Enum<?>[] enums;

        AbstractReader(Class<? extends Enum<?>> enumClass, Enum<?>[] enums) {
            this.enumClass = enumClass;
            this.enums = enums;
        }

        abstract Object readInsightMember(Enum<?> e);

        @TruffleBoundary
        @SuppressWarnings("unchecked")
        private Enum<?> getEnumOf(String name) {
            try {
                return Enum.valueOf(enumClass, name);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        @ExportMessage
        static class IsMemberReadable {

            @Specialization
            static boolean isReadable(AbstractReader receiver, InsightMember member) {
                return receiver.enumClass == member.getEnum().getDeclaringClass();
            }

            @Specialization(guards = "interop.isString(memberObj)")
            static boolean isReadable(AbstractReader receiver, Object memberObj, @Cached.Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop) {
                try {
                    String name = interop.asString(memberObj);
                    return receiver.getEnumOf(name) != null;
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
            static Object read(AbstractReader receiver, InsightMember member) {
                Object value = receiver.readInsightMember(member.getEnum());
                return value;
            }

            @Specialization(guards = "interop.isString(memberObj)")
            static Object read(AbstractReader receiver, Object memberObj, @Cached.Shared("interop") @CachedLibrary(limit = "3") InteropLibrary interop)
                            throws UnknownMemberException, UnsupportedMessageException {
                String name = interop.asString(memberObj);
                Enum<?> theEnum = receiver.getEnumOf(name);
                if (theEnum != null) {
                    return receiver.readInsightMember(theEnum);
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownMemberException.create(memberObj);
                }
            }

            @Fallback
            static Object read(@SuppressWarnings("unused") AbstractReader receiver, Object unknownObj) throws UnknownMemberException {
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
            return new MembersObject(enums);
        }
    }
}
