/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@SuppressWarnings("unused")
@ExportLibrary(InteropLibrary.class)
final class VariablesObject implements TruffleObject {

    final Object scope;
    private final Object returnValue;

    VariablesObject(Object scope, Object returnValue) {
        this.scope = scope;
        this.returnValue = returnValue;
    }

    Object getReturnValue() {
        return NullObject.nullCheck(returnValue);
    }

    @ExportMessage
    static boolean hasMembers(VariablesObject obj) {
        return true;
    }

    @ExportMessage
    static Object getMembers(VariablesObject obj, boolean includeInternal, @CachedLibrary("obj.scope") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.getMembers(obj.scope);
    }

    @ExportMessage
    static Object readMember(VariablesObject obj, String member, @CachedLibrary("obj.scope") InteropLibrary interop) throws UnknownIdentifierException, UnsupportedMessageException {
        return interop.readMember(obj.scope, member);
    }

    @ExportMessage
    static boolean isMemberReadable(VariablesObject obj, String member, @CachedLibrary("obj.scope") InteropLibrary interop) {
        return interop.isMemberReadable(obj.scope, member);
    }

    @ExportMessage
    static void writeMember(VariablesObject obj, String member, Object value, @CachedLibrary("obj.scope") InteropLibrary interop)
                    throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
        interop.writeMember(obj.scope, member, value);
    }

    @ExportMessage
    static boolean isMemberModifiable(VariablesObject obj, String member, @CachedLibrary("obj.scope") InteropLibrary interop) {
        return interop.isMemberModifiable(obj.scope, member);
    }

    @ExportMessage
    static boolean isMemberInsertable(VariablesObject obj, String member, @CachedLibrary("obj.scope") InteropLibrary interop) {
        return interop.isMemberInsertable(obj.scope, member);
    }
}
