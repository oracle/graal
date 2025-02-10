/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vm.structs;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.jni.RawBuffer;
import com.oracle.truffle.espresso.meta.EspressoError;

public final class NativeMemberOffsetGetter implements MemberOffsetGetter {
    private final InteropLibrary library;
    private final TruffleObject memberInfoPtr;
    private final TruffleObject lookupMemberOffset;

    public NativeMemberOffsetGetter(InteropLibrary library, TruffleObject memberInfoPtr, TruffleObject lookupMemberOffset) {
        this.library = library;
        this.memberInfoPtr = memberInfoPtr;
        this.lookupMemberOffset = lookupMemberOffset;
    }

    @Override
    public long getInfo(String str) {
        long result = lookupInfo(str);
        if (result == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Struct offset lookup for " + str + " failed.");
        }
        return result;
    }

    private long lookupInfo(String str) {
        try (RawBuffer memberBuffer = RawBuffer.getNativeString(str)) {
            return (long) library.execute(lookupMemberOffset, memberInfoPtr, memberBuffer.pointer());
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }
}
