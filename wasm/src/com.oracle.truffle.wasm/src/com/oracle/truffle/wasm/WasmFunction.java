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
package com.oracle.truffle.wasm;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.wasm.nodes.WasmIndirectCallNode;

@ExportLibrary(InteropLibrary.class)
public class WasmFunction implements TruffleObject {

    private final SymbolTable symbolTable;
    private final int index;
    private ImportDescriptor importDescriptor;
    private WasmCodeEntry codeEntry;
    private final String name;
    private final int typeIndex;
    private CallTarget callTarget;

    /**
     * Represents a WebAssembly function.
     *
     * If a Wasm function has a
     */
    public WasmFunction(SymbolTable symbolTable, int index, int typeIndex, ImportDescriptor importDescriptor) {
        this.symbolTable = symbolTable;
        this.index = index;
        this.importDescriptor = importDescriptor;
        this.codeEntry = null;
        // TODO: Establish a valid naming convention (integers are not valid identifiers), or remove
        // this.
        this.name = String.valueOf(index);
        this.typeIndex = typeIndex;
        this.callTarget = null;
    }

    public int numArguments() {
        return symbolTable.functionTypeArgumentCount(typeIndex);
    }

    public byte argumentTypeAt(int argumentIndex) {
        return symbolTable.getFunctionTypeArgumentTypeAt(typeIndex, argumentIndex);
    }

    public byte returnType() {
        return symbolTable.getFunctionTypeReturnType(typeIndex);
    }

    public int returnTypeLength() {
        return symbolTable.getFunctionTypeReturnTypeLength(typeIndex);
    }

    public void setCallTarget(CallTarget callTarget) {
        this.callTarget = callTarget;
    }

    public CallTarget resolveCallTarget() {
        if (callTarget == null) {
            CompilerDirectives.transferToInterpreter();
            // TODO: If this is an imported function, the call target might not yet be resolved.
            // Check this, and wait until the call target gets resolved.
            throw new RuntimeException("Call target was not resolved.");
        }
        return callTarget;
    }

    @Override
    public String toString() {
        return name;
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object[] arguments, @Cached WasmIndirectCallNode callNode) {
        return callNode.execute(this, arguments);
    }

    public WasmCodeEntry codeEntry() {
        return codeEntry;
    }

    public void setCodeEntry(WasmCodeEntry codeEntry) {
        if (isImported()) {
            throw new RuntimeException("Cannot set the code entry for an imported function.");
        }
        this.codeEntry = codeEntry;
    }

    public boolean isImported() {
        return importDescriptor != null;
    }

    public String importedModuleName() {
        return isImported() ? importDescriptor.moduleName : null;
    }

    public String importedFunctionName() {
        return isImported() ? importDescriptor.memberName : null;
    }

    public int typeIndex() {
        return typeIndex;
    }

    public int index() {
        return index;
    }
}
