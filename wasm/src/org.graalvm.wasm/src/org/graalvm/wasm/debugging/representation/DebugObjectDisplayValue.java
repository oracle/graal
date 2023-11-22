/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.debugging.representation;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.MaterializedFrame;
import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmType;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.data.DebugContext;
import org.graalvm.wasm.debugging.data.DebugObject;
import org.graalvm.wasm.debugging.data.DebugFunction;
import org.graalvm.wasm.nodes.WasmDataAccess;

/**
 * Represents an object scope in the debug environment.
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class DebugObjectDisplayValue extends DebugDisplayValue implements TruffleObject {
    private final DebugContext context;
    private final DebugLocation location;
    private final String name;
    private final EconomicMap<String, DebugObject> members;

    private DebugObjectDisplayValue(DebugContext context, DebugLocation location, String name, EconomicMap<String, DebugObject> members) {
        assert context != null : "the context provided to a debug object display value must not be null";
        assert location != null : "the location provided to a debug object display value must not be null";
        assert members != null : "the list of members provided to a debug object display value must not be null";
        this.context = context;
        this.location = location;
        this.name = name;
        this.members = members;
    }

    @TruffleBoundary
    public static Object fromDebugObject(DebugObject object, DebugContext context, DebugLocation location) {
        final EconomicMap<String, DebugObject> members = EconomicMap.create();
        final int count = object.memberCount();
        for (int i = 0; i < count; i++) {
            final DebugObject member = object.readMember(context, location, i);
            if (member.isVisible(context.sourceCodeLocation())) {
                members.put(member.toDisplayString(), member);
            }
        }
        return new DebugObjectDisplayValue(context, location, object.toString(), members);
    }

    @TruffleBoundary
    public static Object fromDebugFunction(DebugFunction function, DebugContext context, MaterializedFrame frame, WasmDataAccess dataAccess, boolean testMode) {
        final DebugLocation frameBase = function.frameBaseOrNull(frame, dataAccess);
        if (frameBase == null) {
            return DebugConstantDisplayValue.UNDEFINED;
        }
        if (function.hasGlobals() || testMode) {
            final EconomicMap<String, DebugObject> members = EconomicMap.of("globals", function.globals(), "locals", function.locals());
            return new DebugObjectDisplayValue(context, frameBase, "", members);
        } else {
            return fromDebugObject(function.locals(), context, frameBase);
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    boolean isScope() {
        return true;
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return WasmLanguage.class;
    }

    @ExportMessage
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return name != null ? name : "";
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException {
        if (members == null) {
            return WasmType.VOID_TYPE;
        }
        if (!members.containsKey(member)) {
            throw UnknownIdentifierException.create(member);
        }
        final DebugObject memberObject = members.get(member);
        return resolveDebugObject(memberObject, context, location);
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new WasmVariableNamesObject(members.getKeys());
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        return members.containsKey(member);
    }

    @SuppressWarnings("static-method")
    @ExportLibrary(InteropLibrary.class)
    static final class WasmVariableNamesObject implements TruffleObject {
        final List<String> names;

        WasmVariableNamesObject(Iterable<String> names) {
            this.names = new ArrayList<>(0);
            for (String name : names) {
                this.names.add(name);
            }
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        long getArraySize() {
            return names.size();
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return names.get((int) index);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < names.size();
        }
    }
}
