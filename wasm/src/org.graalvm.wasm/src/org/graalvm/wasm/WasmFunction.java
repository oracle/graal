/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class WasmFunction {
    private final SymbolTable symbolTable;
    private final int index;
    private final ImportDescriptor importDescriptor;
    private final int typeIndex;
    @CompilationFinal private int typeEquivalenceClass;
    @CompilationFinal private String debugName;
    @CompilationFinal private CallTarget callTarget;

    /**
     * Represents a WebAssembly function.
     */
    public WasmFunction(SymbolTable symbolTable, int index, int typeIndex, ImportDescriptor importDescriptor) {
        this.symbolTable = symbolTable;
        this.index = index;
        this.importDescriptor = importDescriptor;
        this.typeIndex = typeIndex;
        this.typeEquivalenceClass = -1;
    }

    public String moduleName() {
        return symbolTable.module().name();
    }

    public int paramCount() {
        return symbolTable.functionTypeParamCount(typeIndex);
    }

    public byte paramTypeAt(int argumentIndex) {
        return symbolTable.functionTypeParamTypeAt(typeIndex, argumentIndex);
    }

    public int resultCount() {
        return symbolTable.functionTypeResultCount(typeIndex);
    }

    public byte resultTypeAt(int returnIndex) {
        return symbolTable.functionTypeResultTypeAt(typeIndex, returnIndex);
    }

    void setTypeEquivalenceClass(int typeEquivalenceClass) {
        this.typeEquivalenceClass = typeEquivalenceClass;
    }

    @Override
    public String toString() {
        return name();
    }

    @TruffleBoundary
    public String name() {
        if (importDescriptor != null) {
            return importDescriptor.memberName;
        }
        String exportedName = symbolTable.exportedFunctionName(index);
        if (exportedName != null) {
            return exportedName;
        }
        if (debugName != null) {
            return debugName;
        }
        return "wasm-function:" + index;
    }

    public void setDebugName(String debugName) {
        this.debugName = debugName;
    }

    public boolean isImported() {
        return importDescriptor != null;
    }

    public ImportDescriptor importDescriptor() {
        return importDescriptor;
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

    public SymbolTable.FunctionType type() {
        return symbolTable.typeAt(typeIndex());
    }

    public int typeEquivalenceClass() {
        return typeEquivalenceClass;
    }

    public int index() {
        return index;
    }

    public CallTarget target() {
        return callTarget;
    }

    public void setTarget(CallTarget callTarget) {
        assert !isImported() : this;
        assert this.callTarget == null : this;
        this.callTarget = callTarget;
    }

    void setImportedFunctionCallTarget(CallTarget callTarget) {
        assert isImported() : this;
        this.callTarget = callTarget;
    }
}
