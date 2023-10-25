/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm;

import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings({"static-method"})
public final class WasmScope implements TruffleObject {
    private final WasmContext context;

    public WasmScope(WasmContext context) {
        this.context = context;
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
    boolean hasMembers() {
        return true;
    }

    private Map<String, WasmInstance> instances() {
        return context.moduleInstances();
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException {
        var instances = instances();
        Object value = instances.get(member);
        if (value != null) {
            return value;
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    boolean isMemberReadable(String member) {
        var instances = instances();
        return instances.containsKey(member);
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        var instances = instances();
        String[] keys = instances.keySet().toArray(new String[instances.size()]);
        return new InstanceNamesObject(keys);
    }

    @ExportMessage
    boolean isScope() {
        return true;
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "wasm-global-scope" + instances().keySet();
    }

    @ExportLibrary(InteropLibrary.class)
    static final class InstanceNamesObject implements TruffleObject {

        private final String[] names;

        InstanceNamesObject(String[] names) {
            this.names = names;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < names.length;
        }

        @ExportMessage
        long getArraySize() {
            return names.length;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Bind("$node") Node node,
                        @Cached InlinedBranchProfile error) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                error.enter(node);
                throw InvalidArrayIndexException.create(index);
            }
            return names[(int) index];
        }
    }
}
