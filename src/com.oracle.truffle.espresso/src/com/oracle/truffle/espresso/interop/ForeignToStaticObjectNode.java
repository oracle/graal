/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.interop;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class ForeignToStaticObjectNode extends Node {
    public abstract Object executeConvert(Meta meta, Object value);

    @Child Node isBoxed = Message.IS_BOXED.createNode();
    @Child Node unbox = Message.UNBOX.createNode();

    @Specialization
    protected Object convertStaticObjectArray(@SuppressWarnings("unused") Meta meta, StaticObject[] array) {
        return StaticObject.wrap(array);
    }

    @Specialization(guards = "value.getClass().isArray()")
    protected Object convertArray(@SuppressWarnings("unused") Meta meta, Object value) {
        return StaticObject.wrapPrimitiveArray(value);
    }

    @Specialization
    protected Object convertTruffle(@SuppressWarnings("unused") Meta meta, TruffleObject value) {
        if (ForeignAccess.sendIsBoxed(isBoxed, value)) {
            try {
                // return Meta.box(meta, ForeignAccess.sendUnbox(unbox, value));
                return ForeignAccess.sendUnbox(unbox, value);
            } catch (UnsupportedMessageException e) {
                throw new IllegalStateException();
            }
        } else {
            throw new IllegalStateException();
        }
    }

    @Fallback
    protected Object convert(Meta meta, Object value) {
        return Meta.box(meta, value);
    }
}