/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

final class ToPrimitiveNode extends Node {
    private static final ToPrimitiveNode INSTANCE = new ToPrimitiveNode();

    @Child Node isBoxedNode;
    @Child Node hasSizeNode;
    @Child Node unboxNode;

    private ToPrimitiveNode() {
        this.isBoxedNode = Message.IS_BOXED.createNode();
        this.hasSizeNode = Message.HAS_SIZE.createNode();
        this.unboxNode = Message.UNBOX.createNode();
    }

    static ToPrimitiveNode create() {
        return new ToPrimitiveNode();
    }

    static ToPrimitiveNode shared() {
        CompilerAsserts.neverPartOfCompilation();
        return INSTANCE;
    }

    boolean isPrimitive(Object attr) {
        return toPrimitive(attr, null) != null;
    }

    Object toPrimitive(Object value, Class<?> requestedType) {
        Object attr;
        if (value instanceof TruffleObject) {
            boolean isBoxed = ForeignAccess.sendIsBoxed(isBoxedNode, (TruffleObject) value);
            if (!isBoxed) {
                return null;
            }
            try {
                attr = ForeignAccess.send(unboxNode, (TruffleObject) value);
            } catch (InteropException e) {
                throw new IllegalStateException();
            }
        } else {
            attr = value;
        }
        if (attr instanceof Number) {
            if (requestedType == null) {
                return attr;
            }
            Number n = (Number) attr;
            if (requestedType == byte.class || requestedType == Byte.class) {
                return n.byteValue();
            }
            if (requestedType == short.class || requestedType == Short.class) {
                return n.shortValue();
            }
            if (requestedType == int.class || requestedType == Integer.class) {
                return n.intValue();
            }
            if (requestedType == long.class || requestedType == Long.class) {
                return n.longValue();
            }
            if (requestedType == float.class || requestedType == Float.class) {
                return n.floatValue();
            }
            if (requestedType == double.class || requestedType == Double.class) {
                return n.doubleValue();
            }
            if (requestedType == char.class || requestedType == Character.class) {
                return (char) n.intValue();
            }
            return n;
        }
        if (attr instanceof CharSequence) {
            if (requestedType == char.class || requestedType == Character.class) {
                if (((String) attr).length() == 1) {
                    return ((String) attr).charAt(0);
                }
            }
            return String.valueOf(attr);
        }
        if (attr instanceof Character) {
            return attr;
        }
        if (attr instanceof Boolean) {
            return attr;
        }
        return null;
    }

    boolean hasSize(TruffleObject truffleObject) {
        return ForeignAccess.sendHasSize(hasSizeNode, truffleObject);
    }

}
