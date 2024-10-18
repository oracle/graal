/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi;

import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;

import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class ErrnoMirror implements TruffleObject {

    static final FastThreadLocalBytes<CIntPointer> errnoMirror = FastThreadLocalFactory.createBytes(() -> SizeOf.get(CIntPointer.class), "ErrnoMirror.errnoMirror");

    private static final KeysArray KEYS = new KeysArray(new String[]{"bind"});

    @ExportMessage
    Object execute(@SuppressWarnings("unused") Object[] args) {
        return new Target_com_oracle_truffle_nfi_backend_libffi_NativePointer(errnoMirror.getAddress().rawValue());
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMemberObjects() {
        return KEYS;
    }

    @ExportMessage
    static class IsMemberInvocable {

        @Specialization
        static boolean isInvocable(@SuppressWarnings("unused") ErrnoMirror error, KeyMember member) {
            return "bind".equals(member.key);
        }

        @Specialization(guards = "interop.isString(member)")
        static boolean isInvocable(@SuppressWarnings("unused") ErrnoMirror error, Object member,
                        @Shared("interop") @CachedLibrary(limit = "2") InteropLibrary interop) {
            try {
                return "bind".equals(interop.asString(member));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean isInvocable(ErrnoMirror error, Object unknown) {
            return false;
        }
    }

    @ExportMessage
    static class InvokeMember {

        @Specialization
        static Object invoke(ErrnoMirror error, KeyMember member, @SuppressWarnings("unused") Object[] args) throws UnknownMemberException {
            if ("bind".equals(member.key)) {
                return error;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnknownMemberException.create(member);
            }
        }

        @Specialization(guards = "interop.isString(member)")
        static Object invoke(ErrnoMirror error, Object member, @SuppressWarnings("unused") Object[] args,
                        @Shared("interop") @CachedLibrary(limit = "2") InteropLibrary interop) throws UnknownMemberException {
            try {
                if ("bind".equals(interop.asString(member))) {
                    return error;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownMemberException.create(member);
                }
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object invoke(ErrnoMirror error, Object unknown, Object[] args) throws UnknownMemberException {
            CompilerDirectives.transferToInterpreter();
            throw UnknownMemberException.create(unknown);
        }
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class KeysArray implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final String[] keys;

        KeysArray(String[] keys) {
            this.keys = keys;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return 0 <= idx && idx < keys.length;
        }

        @ExportMessage
        Object readArrayElement(long idx,
                        @Bind("$node") Node node,
                        @Cached InlinedBranchProfile exception) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                exception.enter(node);
                throw InvalidArrayIndexException.create(idx);
            }
            return new KeyMember(keys[(int) idx]);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class KeyMember implements TruffleObject {

        private final String key;

        KeyMember(String key) {
            this.key = key;
        }

        @ExportMessage
        boolean isMember() {
            return true;
        }

        @ExportMessage
        Object getMemberSimpleName() {
            return key;
        }

        @ExportMessage
        Object getMemberQualifiedName() {
            return key;
        }

        @ExportMessage
        boolean isMemberKindField() {
            return false;
        }

        @ExportMessage
        boolean isMemberKindMethod() {
            return false;
        }

        @ExportMessage
        boolean isMemberKindMetaObject() {
            return false;
        }
    }
}
