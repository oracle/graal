/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.wasm.api.Sequence;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class WasmInstanceExports implements TruffleObject {
    private final WasmInstance instance;

    public WasmInstanceExports(WasmInstance instance) {
        this.instance = instance;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    public Object readMember(String member) throws UnknownIdentifierException {
        final SymbolTable symbolTable = instance.symbolTable();
        final WasmFunction function = symbolTable.exportedFunctions().get(member);
        if (function != null) {
            return instance.functionInstance(function);
        }
        final Integer tableIndex = symbolTable.exportedTables().get(member);
        if (tableIndex != null) {
            return instance.store().tables().table(instance.tableAddress(tableIndex));
        }
        final Integer memoryIndex = symbolTable.exportedMemories().get(member);
        if (memoryIndex != null) {
            return instance.memory(memoryIndex);
        }
        final Integer globalIndex = symbolTable.exportedGlobals().get(member);
        if (globalIndex != null) {
            return instance.externalGlobal(globalIndex);
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        final SymbolTable symbolTable = instance.symbolTable();
        return symbolTable.exportedFunctions().containsKey(member) ||
                        symbolTable.exportedMemories().containsKey(member) ||
                        symbolTable.exportedTables().containsKey(member) ||
                        symbolTable.exportedGlobals().containsKey(member);
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberInvocable(String member) {
        final SymbolTable symbolTable = instance.symbolTable();
        return symbolTable.exportedFunctions().containsKey(member);
    }

    @ExportMessage
    @TruffleBoundary
    Object invokeMember(String member, Object... arguments) throws UnknownIdentifierException, UnsupportedMessageException, UnsupportedTypeException, ArityException {
        if (!isMemberInvocable(member)) {
            throw UnknownIdentifierException.create(member);
        }
        final SymbolTable symbolTable = instance.symbolTable();
        final WasmFunction function = symbolTable.exportedFunctions().get(member);
        if (function != null) {
            final WasmFunctionInstance functionInstance = instance.functionInstance(function);
            final InteropLibrary lib = InteropLibrary.getUncached(functionInstance);
            assert lib.isExecutable(functionInstance);
            return lib.execute(functionInstance, arguments);
        }
        return WasmConstant.VOID;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        // TODO: Handle includeInternal.
        final SymbolTable symbolTable = instance.symbolTable();
        final List<String> exportNames = new ArrayList<>();
        for (String functionName : symbolTable.exportedFunctions().getKeys()) {
            exportNames.add(functionName);
        }
        for (String tableName : symbolTable.exportedTables().getKeys()) {
            exportNames.add(tableName);
        }
        for (String memoryName : symbolTable.exportedMemories().getKeys()) {
            exportNames.add(memoryName);
        }
        for (String globalName : symbolTable.exportedGlobals().getKeys()) {
            exportNames.add(globalName);
        }
        return new Sequence<>(exportNames);
    }

    @Override
    public String toString() {
        return "wasm-instance-exports(" + instance.name() + ")";
    }
}
