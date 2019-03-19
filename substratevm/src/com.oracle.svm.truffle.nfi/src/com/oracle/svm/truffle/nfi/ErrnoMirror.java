/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class ErrnoMirror implements TruffleObject {

    private static final FastThreadLocalBytes<CIntPointer> errnoMirror = FastThreadLocalFactory.createBytes(() -> SizeOf.get(CIntPointer.class));

    private static final KeysArray KEYS = new KeysArray(new String[]{"bind"});

    public static CIntPointer getErrnoMirrorLocation() {
        return errnoMirror.getAddress();
    }

    @ExportMessage
    Object execute(@SuppressWarnings("unused") Object[] args) {
        return new Target_com_oracle_truffle_nfi_impl_NativePointer(ErrnoMirror.getErrnoMirrorLocation().rawValue());
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    boolean isMemberInvocable(String member) {
        return member.equals("bind");
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return KEYS;
    }

    @ExportMessage
    Object invokeMember(String member, @SuppressWarnings("unused") Object[] args) throws UnknownIdentifierException {
        if (!"bind".equals(member)) {
            throw UnknownIdentifierException.create(member);
        }
        return this;
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
        String readArrayElement(long idx,
                        @Cached BranchProfile exception) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                exception.enter();
                throw InvalidArrayIndexException.create(idx);
            }
            return keys[(int) idx];
        }
    }

}
