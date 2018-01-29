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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.impl.LibFFIType.PrepareArgument;
import com.oracle.truffle.nfi.impl.TypeConversion.AsPointerNode;
import com.oracle.truffle.nfi.impl.TypeConversionFactory.AsPointerNodeGen;

abstract class SlowPathSerializeArgumentNode extends Node {

    public abstract Object execute(NativeArgumentBuffer buffer, LibFFIType type, Object value);

    @Specialization(guards = {"type == cachedType"})
    @SuppressWarnings("unused")
    protected Object cacheType(NativeArgumentBuffer buffer, LibFFIType type, Object value, @Cached("type") LibFFIType cachedType,
                    @Cached("cachedType.createSerializeArgumentNode()") SerializeArgumentNode serialize) {
        boolean consumed = serialize.execute(buffer, value);
        assert cachedType.injectedArgument != consumed : "return value of SerializeArgumentNode doesn't agree with injectedArgument flag";
        return null;
    }

    @Specialization(replaces = "cacheType", guards = "needNoPrepare(value)")
    protected Object genericWithoutPrepare(NativeArgumentBuffer buffer, LibFFIType type, Object value) {
        slowPathSerialize(buffer, type, value);
        return null;
    }

    @Specialization(replaces = "cacheType", guards = {"value != null"})
    protected Object genericWithPrepare(NativeArgumentBuffer buffer, LibFFIType type, TruffleObject value,
                    @Cached("createUnbox()") Node unbox,
                    @Cached("createIsExecutable()") Node isExecutable,
                    @Cached("createAsPointer()") AsPointerNode asPointer,
                    @Cached("createRecursive()") SlowPathSerializeArgumentNode recursive) {
        Object prepared = type.slowpathPrepareArgument(value);
        if (prepared == PrepareArgument.EXECUTABLE) {
            if (ForeignAccess.sendIsExecutable(isExecutable, value)) {
                prepared = value;
            } else {
                prepared = PrepareArgument.POINTER;
            }
        }

        if (prepared == PrepareArgument.POINTER) {
            prepared = asPointer.execute(value);
        } else if (prepared == PrepareArgument.UNBOX) {
            Object unboxed;
            try {
                unboxed = ForeignAccess.sendUnbox(unbox, value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.raise(ex, new Object[]{value});
            }
            return recursive.execute(buffer, type, unboxed);
        }
        slowPathSerialize(buffer, type, prepared);
        return null;
    }

    protected static Node createIsNull() {
        return Message.IS_NULL.createNode();
    }

    protected static Node createUnbox() {
        return Message.UNBOX.createNode();
    }

    protected static Node createIsExecutable() {
        return Message.IS_EXECUTABLE.createNode();
    }

    protected static SlowPathSerializeArgumentNode createRecursive() {
        return SlowPathSerializeArgumentNodeGen.create();
    }

    protected static AsPointerNode createAsPointer() {
        return AsPointerNodeGen.create();
    }

    protected static boolean needNoPrepare(Object value) {
        return value == null || !(value instanceof TruffleObject);
    }

    @TruffleBoundary
    private static void slowPathSerialize(NativeArgumentBuffer buffer, LibFFIType type, Object value) {
        type.serialize(buffer, value);
    }
}
