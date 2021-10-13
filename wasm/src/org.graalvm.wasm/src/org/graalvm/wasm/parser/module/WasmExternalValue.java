/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.parser.module;

import org.graalvm.wasm.runtime.WasmFunctionInstance;
import org.graalvm.wasm.runtime.WasmModuleInstance;
import org.graalvm.wasm.constants.ExportIdentifier;
import org.graalvm.wasm.runtime.memory.WasmMemory;
import org.graalvm.wasm.runtime.WasmFunctionType;
import org.graalvm.wasm.runtime.WasmGlobal;
import org.graalvm.wasm.runtime.WasmTable;

public class WasmExternalValue {
    private final int exportIndex;
    private final byte type;
    private final int localIndex;

    public WasmExternalValue(int exportIndex, byte type, int localIndex) {
        this.exportIndex = exportIndex;
        this.type = type;
        this.localIndex = localIndex;
    }

    public int getExportIndex() {
        return exportIndex;
    }

    public byte getType() {
        return type;
    }

    public boolean isFunction() {
        return type == ExportIdentifier.FUNCTION;
    }

    public boolean isTable() {
        return type == ExportIdentifier.TABLE;
    }

    public boolean isMemory() {
        return type == ExportIdentifier.MEMORY;
    }

    public boolean isGlobal() {
        return type == ExportIdentifier.GLOBAL;
    }

    public boolean functionExists(WasmModuleInstance instance) {
        return instance.hasFunction(localIndex);
    }

    public boolean tableExists(WasmModuleInstance instance) {
        return instance.hasTable();
    }

    public boolean memoryExists(WasmModuleInstance instance) {
        return instance.hasMemory();
    }

    public boolean globalExists(WasmModuleInstance instance) {
        return instance.hasGlobal(localIndex);
    }

    public WasmFunctionInstance getFunction(WasmModuleInstance instance) {
        return instance.getFunction(localIndex);
    }

    public WasmTable getTable(WasmModuleInstance instance) {
        return instance.getTable();
    }

    public WasmMemory getMemory(WasmModuleInstance instance) {
        return instance.getMemory();
    }

    public WasmGlobal getGlobal(WasmModuleInstance instance) {
        return instance.getGlobal(localIndex);
    }

    public WasmFunctionType getFunctionType(WasmModule module) {
        return module.getFunctionType(localIndex);
    }

    public int getFunctionIndex() {
        return localIndex;
    }

    public byte getGlobalValueType(WasmModule module) {
        return module.getGlobalValueType(localIndex);
    }
}
