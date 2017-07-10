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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

abstract class ArrayReadNode extends Node {

    protected abstract Object executeWithTarget(JavaObject receiver, Object index);

    @SuppressWarnings("unchecked")
    @Specialization(guards = {"receiver.isArray()", "index.getClass() == clazz"})
    protected Object doNumber(JavaObject receiver, Number index, @Cached("index.getClass()") Class<?> clazz) {
        Class<Number> numberClazz = (Class<Number>) clazz;
        return doArrayAccess(receiver, numberClazz.cast(index).intValue());
    }

    @Specialization(guards = {"receiver.isArray()"}, replaces = "doNumber")
    protected Object doNumberGeneric(JavaObject receiver, Number index) {
        return doArrayAccess(receiver, index.intValue());
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    @Specialization(guards = {"!receiver.isArray()"})
    protected static Object notArray(JavaObject receiver, Number index) {
        throw UnknownIdentifierException.raise(String.valueOf(index));
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

    static ArrayReadNode create() {
        return ArrayReadNodeGen.create();
    }
}
