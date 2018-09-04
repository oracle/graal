/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 * 
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 * 
 * (a) the Software, and
 * 
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 * 
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 * 
 * This license is subject to the following condition:
 * 
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
