/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.binary;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreter;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.wasm.binary.constants.GlobalModifier;
import com.oracle.truffle.wasm.collection.IntArrayList;

import java.util.ArrayList;
import java.util.List;

@ExportLibrary(InteropLibrary.class)
// TODO: We should make this class more Truffle-compliant.
//  In particular, one thing that's missing:
//   - access exported tables and read them
//   - access exported memories and modify them
public class WasmModule implements TruffleObject {
    @CompilationFinal private final String name;
    @CompilationFinal private final SymbolTable symbolTable;
    @CompilationFinal(dimensions = 1) private final byte[] data;

    public WasmModule(String name, byte[] data) {
        this.name = name;
        this.symbolTable = new SymbolTable(this);
        this.data = data;
    }

    public SymbolTable symbolTable() {
        return symbolTable;
    }

    public String name() {
        return name;
    }

    public byte[] data() {
        return data;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    public Object readMember(String member) throws UnknownIdentifierException {
        final WasmFunction function = symbolTable.exportedFunctions().get(member);
        if (function != null) {
            return function;
        }
        final Integer globalIndex = symbolTable.exportedGlobals().get(member);
        if (globalIndex != null) {
            readGlobal(symbolTable, globalIndex);
        }
        if (symbolTable.exportedMemory().equals(member)) {
            return symbolTable.memory();
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    @TruffleBoundary
    public void writeMember(String member, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
        // This method works only for mutable globals.
        final Integer index = symbolTable.exportedGlobals().get(member);
        if (index == null) {
            throw UnknownIdentifierException.create(member);
        }
        final int address = symbolTable.globalAddress(index);
        if (!(value instanceof Number)) {
            throw UnsupportedMessageException.create();
        }
        final boolean mutable = symbolTable.globalMutability(index) == GlobalModifier.MUTABLE;
        if (!mutable) {
            throw UnsupportedMessageException.create();
        }
        long longValue = ((Number) value).longValue();
        WasmContext.getCurrent().globals().storeLong(address, longValue);
    }

    @ExportMessage
    @TruffleBoundary
    final boolean isMemberReadable(String member) {
        try {
            return symbolTable.exportedFunctions().containsKey(member) || symbolTable.exportedGlobals().containsKey(member) ||
                            symbolTable.exportedMemory().equals(member);
        } catch (NumberFormatException exc) {
            return false;
        }
    }

    @ExportMessage
    final boolean isMemberModifiable(String member) {
        final Integer index = symbolTable.exportedGlobals().get(member);
        if (index == null) {
            return false;
        }
        final boolean mutable = symbolTable.globalMutability(index) == GlobalModifier.MUTABLE;
        if (!mutable) {
            return false;
        }
        return true;
    }

    @ExportMessage
    final boolean isMemberInsertable(String member) {
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        // TODO: Handle includeInternal.
        return new ExportedMembers(symbolTable);
    }

    private static Object readGlobal(SymbolTable symbolTable, int globalIndex) {
        final int address = symbolTable.globalAddress(globalIndex);
        final Globals globals = WasmContext.getCurrent().globals();
        final byte type = symbolTable.globalValueType(globalIndex);
        switch (type) {
            case ValueTypes.I32_TYPE:
                return globals.loadAsInt(address);
            case ValueTypes.I64_TYPE:
                return globals.loadAsLong(address);
            case ValueTypes.F32_TYPE:
                return globals.loadAsFloat(address);
            case ValueTypes.F64_TYPE:
                return globals.loadAsDouble(address);
            default:
                throw new RuntimeException("Unknown type: " + type);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ExportedMembers implements TruffleObject {
        private final SymbolTable symbolTable;
        private final List<WasmFunction> exportedFunctions;
        private final IntArrayList exportedGlobals;

        ExportedMembers(SymbolTable symbolTable) {
            this.symbolTable = symbolTable;
            this.exportedFunctions = new ArrayList<>(symbolTable.exportedFunctions().values());
            this.exportedGlobals = new IntArrayList();
            for (int globalIndex : symbolTable.exportedGlobals().values()) {
                this.exportedGlobals.add(globalIndex);
            }
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < getArraySize();
        }

        @ExportMessage
        long getArraySize() {
            return exportedFunctions.size() + exportedGlobals.size() + memoriesSize();
        }

        private int memoriesSize() {
            return (symbolTable.exportedMemory() != null ? 1 : 0);
        }

        @ExportMessage
        Object readArrayElement(long absoluteIndex) throws InvalidArrayIndexException {
            long index = absoluteIndex;
            if (!isArrayElementReadable(index)) {
                transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
            if (index < exportedFunctions.size()) {
                return exportedFunctions.get((int) index);
            }
            index -= exportedFunctions.size();
            if (index < exportedGlobals.size()) {
                final int globalIndex = exportedGlobals.get((int) index);
                return readGlobal(symbolTable, globalIndex);
            }
            return symbolTable.memory();
        }
    }

    @Override
    public String toString() {
        return "wasm-module(" + name + ")";
    }
}
