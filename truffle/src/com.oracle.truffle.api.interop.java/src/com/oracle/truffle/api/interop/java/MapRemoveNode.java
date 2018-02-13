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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import java.util.Map;

abstract class MapRemoveNode extends Node {

    protected abstract Object executeWithTarget(JavaObject receiver, String name);

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    @Specialization(guards = {"isMap(receiver)"})
    protected Object doMapGeneric(JavaObject receiver, String name) {
        Map<String, Object> map = (Map<String, Object>) receiver.obj;
        if (!map.containsKey(name)) {
            throw UnknownIdentifierException.raise(name);
        }
        map.remove(name);
        return true;
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    @Specialization(guards = {"!isMap(receiver)"})
    protected static Object notMap(JavaObject receiver, String name) {
        throw UnsupportedMessageException.raise(Message.REMOVE);
    }

    static boolean isMap(JavaObject receiver) {
        return receiver.obj instanceof Map;
    }

    static MapRemoveNode create() {
        return MapRemoveNodeGen.create();
    }
}
