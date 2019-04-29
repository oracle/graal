/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * A base class for objects returned by Inspector module.
 */
@ExportLibrary(InteropLibrary.class)
abstract class AbstractInspectorObject implements TruffleObject {

    protected AbstractInspectorObject() {
    }

    @ExportMessage
    protected abstract Object getMembers(boolean includeInternal);

    protected abstract boolean isField(String name);

    protected abstract boolean isMethod(String name);

    protected abstract Object getFieldValueOrNull(String name);

    @ExportMessage
    protected abstract Object invokeMember(String name, Object[] arguments) throws UnsupportedTypeException, UnknownIdentifierException, ArityException, UnsupportedMessageException;

    @SuppressWarnings("static-method")
    @ExportMessage
    final boolean hasMembers() {
        return true;
    }

    @ExportMessage
    final boolean isMemberReadable(String member) {
        return isMethod(member) || isField(member);
    }

    @ExportMessage
    final boolean isMemberInvocable(String member) {
        return isMethod(member);
    }

    @ExportMessage
    protected final Object readMember(String name) throws UnknownIdentifierException {
        Object value = getFieldValueOrNull(name);
        if (value == null) {
            value = getMethodExecutable(name);
        }
        return value;
    }

    @TruffleBoundary
    final TruffleObject getMethodExecutable(String name) throws UnknownIdentifierException {
        if (isMethod(name)) {
            return createMethodExecutable(name);
        } else {
            throw UnknownIdentifierException.create(name);
        }
    }

    @ExportMessage
    protected boolean isInstantiable() {
        return false;
    }

    @SuppressWarnings("unused")
    @ExportMessage
    protected Object instantiate(Object[] arguments) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    private TruffleObject createMethodExecutable(String name) {
        CompilerAsserts.neverPartOfCompilation();
        return new MethodExecutable(this, name);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MethodExecutable implements TruffleObject {

        final AbstractInspectorObject inspector;
        private final String name;

        MethodExecutable(AbstractInspectorObject inspector, String name) {
            this.inspector = inspector;
            this.name = name;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments, @CachedLibrary("this.inspector") InteropLibrary interop)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            try {
                return interop.invokeMember(inspector, name, arguments);
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();
            }
        }
    }
}
