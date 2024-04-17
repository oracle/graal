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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.EspressoLanguage;

@ExportLibrary(InteropLibrary.class)
final class SubstitutionScope implements TruffleObject {
    private final Object[] args;

    SubstitutionScope(Object[] arguments) {
        this.args = arguments;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isScope() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return EspressoLanguage.class;
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException {
        try {
            int index = Integer.parseInt(member);
            return args[index];
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isMemberReadable(@SuppressWarnings("unused") String member) {
        try {
            int index = Integer.parseInt(member);
            return index > -1 && index < args.length;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "<intrinsified>";
    }

}
