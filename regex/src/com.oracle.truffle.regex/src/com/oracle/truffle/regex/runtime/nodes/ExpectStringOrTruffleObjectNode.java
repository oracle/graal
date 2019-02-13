/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.runtime.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

public abstract class ExpectStringOrTruffleObjectNode extends Node {

    public abstract Object execute(Object arg);

    @Specialization
    Object doString(String arg) {
        return arg;
    }

    @Specialization
    Object doTruffleObject(TruffleObject arg,
                    @Cached("createIsBoxedNode()") Node isBoxed,
                    @Cached("createUnboxNode()") Node unbox) {
        try {
            if (ForeignAccess.sendIsBoxed(isBoxed, arg)) {
                Object unboxedObject = ForeignAccess.sendUnbox(unbox, arg);
                if (unboxedObject instanceof String) {
                    return unboxedObject;
                }
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(new Object[]{arg});
            }
            return arg;
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedTypeException.raise(new Object[]{arg});
        }
    }

    @Fallback
    Object doPrimitive(Object arg) {
        throw UnsupportedTypeException.raise(new Object[]{arg});
    }

    static Node createIsBoxedNode() {
        return Message.IS_BOXED.createNode();
    }

    static Node createUnboxNode() {
        return Message.UNBOX.createNode();
    }

    public static ExpectStringOrTruffleObjectNode create() {
        return ExpectStringOrTruffleObjectNodeGen.create();
    }
}
