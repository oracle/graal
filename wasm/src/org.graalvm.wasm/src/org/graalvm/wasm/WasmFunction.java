/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.graalvm.wasm.nodes.WasmIndirectCallNode;

@ExportLibrary(InteropLibrary.class)
public class WasmFunction implements TruffleObject {
    private final SymbolTable symbolTable;
    private final int index;
    private ImportDescriptor importDescriptor;
    private WasmCodeEntry codeEntry;
    private final int typeIndex;
    private int typeEquivalenceClass;
    private CallTarget callTarget;

    /**
     * Represents a WebAssembly function.
     */
    public WasmFunction(SymbolTable symbolTable, int index, int typeIndex, ImportDescriptor importDescriptor) {
        this.symbolTable = symbolTable;
        this.index = index;
        this.importDescriptor = importDescriptor;
        this.codeEntry = null;
        this.typeIndex = typeIndex;
        this.typeEquivalenceClass = -1;
        this.callTarget = null;
    }

    public String moduleName() {
        return symbolTable.module().name();
    }

    public int numArguments() {
        return symbolTable.functionTypeArgumentCount(typeIndex);
    }

    public byte argumentTypeAt(int argumentIndex) {
        return symbolTable.functionTypeArgumentTypeAt(typeIndex, argumentIndex);
    }

    public byte returnType() {
        return symbolTable.functionTypeReturnType(typeIndex);
    }

    int returnTypeLength() {
        return symbolTable.functionTypeReturnTypeLength(typeIndex);
    }

    public void setCallTarget(CallTarget callTarget) {
        this.callTarget = callTarget;
    }

    public CallTarget resolveCallTarget() {
        if (callTarget == null) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException("Call target was not resolved.");
        }
        return callTarget;
    }

    void setTypeEquivalenceClass(int typeEquivalenceClass) {
        this.typeEquivalenceClass = typeEquivalenceClass;
    }

    @Override
    public String toString() {
        return name();
    }

    public String name() {
        if (importDescriptor != null) {
            return importDescriptor.memberName;
        }
        String exportedName = symbolTable.exportedFunctionName(index);
        if (exportedName != null) {
            return exportedName;
        }
        return "wasm-function:" + index;
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

    public int typeEquivalenceClass() {
        return typeEquivalenceClass;
    }

    public int index() {
        return index;
    }
}
