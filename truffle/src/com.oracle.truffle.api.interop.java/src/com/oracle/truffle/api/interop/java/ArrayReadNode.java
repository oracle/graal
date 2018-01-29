/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import java.lang.reflect.Array;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

abstract class ArrayReadNode extends Node {

    protected abstract Object executeWithTarget(JavaObject receiver, Object index);

    @Specialization(guards = {"receiver.isArray()"})
    protected Object doArrayIntIndex(JavaObject receiver, int index) {
        return doArrayAccess(receiver, index);
    }

    @Specialization(guards = {"receiver.isArray()", "index.getClass() == clazz"}, replaces = "doArrayIntIndex")
    protected Object doArrayCached(JavaObject receiver, Number index,
                    @Cached("index.getClass()") Class<? extends Number> clazz) {
        return doArrayAccess(receiver, clazz.cast(index).intValue());
    }

    @Specialization(guards = {"receiver.isArray()"}, replaces = "doArrayCached")
    protected Object doArrayGeneric(JavaObject receiver, Number index) {
        return doArrayAccess(receiver, index.intValue());
    }

    @TruffleBoundary
    @Specialization(guards = {"isList(receiver)"})
    protected Object doListIntIndex(JavaObject receiver, int index) {
        return ((List<?>) receiver.obj).get(index);
    }

    @TruffleBoundary
    @Specialization(guards = {"isList(receiver)"}, replaces = "doListIntIndex")
    protected Object doListGeneric(JavaObject receiver, Number index) {
        return doListIntIndex(receiver, index.intValue());
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    @Specialization(guards = {"!receiver.isArray()", "!isList(receiver)"})
    protected static Object notArray(JavaObject receiver, Number index) {
        throw UnsupportedMessageException.raise(Message.READ);
    }

    private static Object doArrayAccess(JavaObject object, int index) {
        Object obj = object.obj;
        assert object.isArray();
        Object val = null;
        try {
            val = Array.get(obj, index);
        } catch (ArrayIndexOutOfBoundsException outOfBounds) {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.raise(String.valueOf(index));
        }
        return JavaInterop.toGuestValue(val, object.languageContext);
    }

    static boolean isList(JavaObject receiver) {
        return receiver.obj instanceof List;
    }

    static ArrayReadNode create() {
        return ArrayReadNodeGen.create();
    }
}
