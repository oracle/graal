/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Creates a view of the current execution scope, hiding members of the parent scope.
 */
@ExportLibrary(value = InteropLibrary.class, delegateTo = "scope")
final class CurrentScopeView implements TruffleObject {

    final Object scope;

    CurrentScopeView(Object scope) {
        this.scope = scope;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal,
                    @CachedLibrary("this.scope") InteropLibrary scopeLib,
                    @CachedLibrary(limit = "5") InteropLibrary parentScopeLib) throws UnsupportedMessageException {
        Object allKeys = scopeLib.getMembers(scope);
        if (scopeLib.hasScopeParent(scope)) {
            Object parentScope = scopeLib.getScopeParent(scope);
            Object parentKeys = parentScopeLib.getMembers(parentScope);
            return new SubtractedKeys(allKeys, parentKeys);
        }
        return allKeys;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class SubtractedKeys implements TruffleObject {

        final Object allKeys;
        private final long allSize;
        private final long removedSize;

        SubtractedKeys(Object allKeys, Object removedKeys) throws UnsupportedMessageException {
            this.allKeys = allKeys;
            this.allSize = InteropLibrary.getUncached().getArraySize(allKeys);
            this.removedSize = InteropLibrary.getUncached().getArraySize(removedKeys);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return allSize - removedSize;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @CachedLibrary("this.allKeys") InteropLibrary interop) throws InvalidArrayIndexException, UnsupportedMessageException {
            if (0 <= index && index < getArraySize()) {
                return interop.readArrayElement(allKeys, index);
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        boolean isArrayElementReadable(long index,
                        @CachedLibrary("this.allKeys") InteropLibrary interop) {
            if (0 <= index && index < getArraySize()) {
                return interop.isArrayElementReadable(allKeys, index);
            } else {
                return false;
            }
        }
    }
}
