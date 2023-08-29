/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.api;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.Pair;
import org.graalvm.wasm.SymbolTable;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.globals.WasmGlobal;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.BuiltinModule;

import com.oracle.truffle.api.CompilerDirectives;

public class ImportModule extends BuiltinModule {
    private final HashMap<String, Pair<WasmFunction, Object>> functions;
    private final HashMap<String, WasmMemory> memories;
    private final HashMap<String, WasmTable> tables;
    private final HashMap<String, WasmGlobal> globals;

    public ImportModule() {
        this.functions = new HashMap<>();
        this.memories = new HashMap<>();
        this.tables = new HashMap<>();
        this.globals = new HashMap<>();
    }

    @Override
    protected WasmModule createModule(WasmLanguage language, WasmContext context, String name) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected WasmInstance createInstance(WasmLanguage language, WasmContext context, String name) {
        WasmModule module = WasmModule.createBuiltin(name);
        WasmInstance instance = new WasmInstance(context, module, functions.size());
        for (Map.Entry<String, Pair<WasmFunction, Object>> entry : functions.entrySet()) {
            final String functionName = entry.getKey();
            final Pair<WasmFunction, Object> info = entry.getValue();
            final WasmFunction function = info.getLeft();
            final SymbolTable.FunctionType type = function.type();
            if (info.getRight() instanceof WasmFunctionInstance) {
                defineExportedFunction(instance, functionName, type.paramTypes(), type.resultTypes(), (WasmFunctionInstance) info.getRight());
            } else {
                var executableWrapper = new ExecuteInParentContextNode(context.language(), module, info.getRight());
                WasmFunction exported = defineFunction(context, module, functionName, type.paramTypes(), type.resultTypes(), executableWrapper);
                instance.setTarget(exported.index(), executableWrapper.getCallTarget());
            }
        }
        for (Map.Entry<String, WasmMemory> entry : memories.entrySet()) {
            final String memoryName = entry.getKey();
            final WasmMemory memory = entry.getValue();
            defineExternalMemory(context, module, memoryName, memory);
        }
        for (Map.Entry<String, WasmTable> entry : tables.entrySet()) {
            final String tableName = entry.getKey();
            final WasmTable table = entry.getValue();
            defineExternalTable(context, module, tableName, table);
        }
        for (Map.Entry<String, WasmGlobal> entry : globals.entrySet()) {
            final String globalName = entry.getKey();
            final WasmGlobal global = entry.getValue();
            defineExternalGlobal(module, globalName, global);
        }
        assert module.numFunctions() == functions.size();
        return instance;
    }

    public void addFunction(String name, Pair<WasmFunction, Object> info) {
        functions.put(name, info);
    }

    public void addMemory(String name, WasmMemory memory) {
        memories.put(name, memory);
    }

    public void addTable(String name, WasmTable table) {
        tables.put(name, table);
    }

    public void addGlobal(String name, WasmGlobal global) {
        globals.put(name, global);
    }
}
