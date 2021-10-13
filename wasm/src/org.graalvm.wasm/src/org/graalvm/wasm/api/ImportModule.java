/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.wasm.ModuleLimits;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.runtime.WasmFunctionInstance;
import org.graalvm.wasm.constants.ExportIdentifier;
import org.graalvm.wasm.parser.module.WasmExternalValue;
import org.graalvm.wasm.runtime.WasmFunctionType;
import org.graalvm.wasm.runtime.WasmGlobal;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.runtime.WasmModuleInstance;
import org.graalvm.wasm.runtime.WasmTable;
import org.graalvm.wasm.runtime.memory.WasmMemory;
import org.graalvm.wasm.predefined.BuiltinModule;

import java.util.HashMap;
import java.util.Map;

public class ImportModule extends BuiltinModule {
    private final HashMap<String, Pair<WasmFunctionType, Object>> functions;
    private final HashMap<String, WasmTable> tables;
    private final HashMap<String, WasmMemory> memories;
    private final HashMap<String, WasmGlobal> globals;

    public ImportModule() {
        this.functions = new HashMap<>();
        this.memories = new HashMap<>();
        this.tables = new HashMap<>();
        this.globals = new HashMap<>();
    }

    @Override
    protected WasmModuleInstance createInstance(WasmLanguage language, WasmContext context, String name, ModuleLimits limits) {
        final int exportSize = functions.size() + tables.size() + memories.size() + globals.size();
        WasmModuleInstance instance = new WasmModuleInstance(
                        null,
                        null,
                        new WasmFunctionInstance[functions.size()],
                        new WasmGlobal[globals.size()],
                        exportSize,
                        name,
                        null);
        int exportIndex = 0;
        for (Map.Entry<String, Pair<WasmFunctionType, Object>> function : functions.entrySet()) {
            final String functionName = function.getKey();
            final Pair<WasmFunctionType, Object> functionInfo = function.getValue();
            final WasmFunctionType functionType = functionInfo.getLeft();
            final Object f = functionInfo.getRight();
            final WasmFunctionInstance functionInstance;
            if (f instanceof WasmFunctionInstance) {
                functionInstance = (WasmFunctionInstance) f;
            } else {
                functionInstance = new WasmFunctionInstance(context, functionType, new ExecuteInParentContextNode(context.language(), instance.getInstance(), f).getCallTarget(), functionName,
                                instance.getNextFunctionIndex());
            }
            final int index = instance.addFunction(functionInstance);
            final WasmExternalValue externalValue = new WasmExternalValue(exportIndex++, (byte) ExportIdentifier.FUNCTION, index);
            instance.addExport(functionName, externalValue);
        }
        for (Map.Entry<String, WasmTable> table : tables.entrySet()) {
            final String tableName = table.getKey();
            final WasmTable t = table.getValue();
            instance.addTable(t);
            final WasmExternalValue externalValue = new WasmExternalValue(exportIndex++, (byte) ExportIdentifier.TABLE, -1);
            instance.addExport(tableName, externalValue);
        }
        for (Map.Entry<String, WasmMemory> memory : memories.entrySet()) {
            final String memoryName = memory.getKey();
            final WasmMemory m = memory.getValue();
            instance.addMemory(m);
            final WasmExternalValue externalValue = new WasmExternalValue(exportIndex++, (byte) ExportIdentifier.MEMORY, -1);
            instance.addExport(memoryName, externalValue);
        }
        for (Map.Entry<String, WasmGlobal> global : globals.entrySet()) {
            final String globalName = global.getKey();
            final WasmGlobal g = global.getValue();
            final int index = instance.addGlobal(g);
            final WasmExternalValue externalValue = new WasmExternalValue(exportIndex++, (byte) ExportIdentifier.GLOBAL, index);
            instance.addExport(globalName, externalValue);
        }
        return instance;
    }

    public void addFunction(String name, Pair<WasmFunctionType, Object> function) {
        functions.put(name, function);
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
