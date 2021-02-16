/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.Pair;
import org.graalvm.wasm.SymbolTable;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.BuiltinModule;

import java.util.HashMap;
import java.util.Map;

public class ImportModule extends BuiltinModule {
    private final HashMap<String, Pair<WasmFunction, Object>> functions;
    private final HashMap<String, Memory> memories;
    private final HashMap<String, Table> tables;
    private final HashMap<String, Object> globals;

    public ImportModule() {
        this.functions = new HashMap<>();
        this.memories = new HashMap<>();
        this.tables = new HashMap<>();
        this.globals = new HashMap<>();
    }

    @Override
    protected WasmInstance createInstance(WasmLanguage language, WasmContext context, String name) {
        WasmInstance instance = new WasmInstance(new WasmModule(name, null));
        for (Map.Entry<String, Pair<WasmFunction, Object>> entry : functions.entrySet()) {
            final String functionName = entry.getKey();
            final Pair<WasmFunction, Object> info = entry.getValue();
            final WasmFunction function = info.getLeft();
            final SymbolTable.FunctionType type = function.type();
            defineFunction(instance, functionName, type.paramTypes(), type.returnTypes(), new ExecuteInParentContextNode(context.language(), instance, info.getRight()));
        }
        for (Map.Entry<String, Memory> entry : memories.entrySet()) {
            final String memoryName = entry.getKey();
            final Memory memory = entry.getValue();
            defineExternalMemory(instance, memoryName, memory.wasmMemory());
        }
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            final String tableName = entry.getKey();
            final Table table = entry.getValue();
            defineExternalTable(instance, tableName, table.wasmTable());
        }
        for (Map.Entry<String, Object> entry : globals.entrySet()) {
            final String globalName = entry.getKey();
            final Object global = entry.getValue();
            defineExternalGlobal(instance, globalName, global);
        }
        return instance;
    }

    public void addFunction(String name, Pair<WasmFunction, Object> info) {
        functions.put(name, info);
    }

    public void addMemory(String name, Memory memory) {
        memories.put(name, memory);
    }

    public void addTable(String name, Table table) {
        tables.put(name, table);
    }

    public void addGlobal(String name, Object global) {
        globals.put(name, global);
    }
}
