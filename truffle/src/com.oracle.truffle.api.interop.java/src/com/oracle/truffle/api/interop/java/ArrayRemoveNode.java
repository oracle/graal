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

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

abstract class ArrayRemoveNode extends Node {

    protected abstract boolean executeWithTarget(JavaObject receiver, Object index);

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    @Specialization(guards = {"isList(receiver)"})
    protected boolean doListIntIndex(JavaObject receiver, int index) {
        try {
            ((List<Object>) receiver.obj).remove(index);
        } catch (IndexOutOfBoundsException outOfBounds) {
            throw UnknownIdentifierException.raise(String.valueOf(index));
        }
        return true;
    }

    @TruffleBoundary
    @Specialization(guards = {"isList(receiver)"}, replaces = "doListIntIndex")
    protected boolean doListGeneric(JavaObject receiver, Number index) {
        return doListIntIndex(receiver, index.intValue());
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    @Specialization(guards = {"!isList(receiver)"})
    protected static boolean notArray(JavaObject receiver, Number index) {
        throw UnsupportedMessageException.raise(Message.REMOVE);
    }

    static boolean isList(JavaObject receiver) {
        return receiver.obj instanceof List;
    }

    static ArrayRemoveNode create() {
        return ArrayRemoveNodeGen.create();
    }
}
