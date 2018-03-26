/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = KeysArray.class)
final class KeysArray implements TruffleObject {

    private final String[] keys;

    KeysArray(String[] keys) {
        this.keys = keys;
    }

    @Resolve(message = "HAS_SIZE")
    abstract static class HasSize extends Node {

        public Object access(@SuppressWarnings("unused") KeysArray receiver) {
            return true;
        }
    }

    @Resolve(message = "GET_SIZE")
    abstract static class GetSize extends Node {

        public Object access(KeysArray receiver) {
            return receiver.keys.length;
        }
    }

    @Resolve(message = "READ")
    abstract static class Read extends Node {

        public Object access(KeysArray receiver, int index) {
            try {
                return receiver.keys[index];
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(String.valueOf(index));
            }
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return KeysArrayForeign.ACCESS;
    }

    static boolean isInstance(TruffleObject array) {
        return array instanceof KeysArray;
    }

}
