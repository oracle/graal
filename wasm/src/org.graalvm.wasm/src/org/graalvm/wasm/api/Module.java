/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.api.ImportExportKind.function;
import static org.graalvm.wasm.api.ImportExportKind.global;
import static org.graalvm.wasm.api.ImportExportKind.memory;
import static org.graalvm.wasm.api.ImportExportKind.table;

import java.util.ArrayList;
import java.util.Map;

import org.graalvm.wasm.ImportDescriptor;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmType;

public class Module extends Dictionary {
    private final WasmModule module;

    public Module(WasmContext context, byte[] source) {
        this.module = context.readModule(source);
        addMembers(new Object[]{
                        "exports", new Executable(args -> exports()),
                        "imports", new Executable(args -> imports()),
                        "customSections", new Executable(args -> customSections(args[0])),
        });
    }

    public Sequence<ModuleExportDescriptor> exports() {
        final ArrayList<ModuleExportDescriptor> list = new ArrayList<>();
        for (Map.Entry<String, WasmFunction> entry : module.exportedFunctions().entrySet()) {
            list.add(new ModuleExportDescriptor(entry.getKey(), function.name(), functionTypeToString(entry.getValue())));
        }
        final String exportedTable = module.exportedTable();
        if (exportedTable != null) {
            list.add(new ModuleExportDescriptor(exportedTable, table.name(), null));
        }
        final String exportedMemory = module.exportedMemory();
        if (exportedMemory != null) {
            list.add(new ModuleExportDescriptor(exportedMemory, memory.name(), null));
        }
        for (Map.Entry<String, Integer> entry : module.exportedGlobals().entrySet()) {
            String name = entry.getKey();
            int index = entry.getValue();
            String valueType = ValueType.fromByteValue(module.globalValueType(index)).toString();
            list.add(new ModuleExportDescriptor(name, global.name(), valueType));
        }
        return new Sequence<>(list);
    }

    public Sequence<ModuleImportDescriptor> imports() {
        final ArrayList<ModuleImportDescriptor> list = new ArrayList<>();
        for (WasmFunction f : module.importedFunctions()) {
            list.add(new ModuleImportDescriptor(f.importedModuleName(), f.importedFunctionName(), function.name(), functionTypeToString(f)));
        }
        final ImportDescriptor tableDescriptor = module.importedTable();
        if (tableDescriptor != null) {
            list.add(new ModuleImportDescriptor(tableDescriptor.moduleName, tableDescriptor.memberName, table.name(), null));
        }
        final ImportDescriptor memoryDescriptor = module.importedMemory();
        if (memoryDescriptor != null) {
            list.add(new ModuleImportDescriptor(memoryDescriptor.moduleName, memoryDescriptor.memberName, memory.name(), null));
        }
        for (Map.Entry<Integer, ImportDescriptor> entry : module.importedGlobals().entrySet()) {
            int index = entry.getKey();
            String valueType = ValueType.fromByteValue(module.globalValueType(index)).toString();
            ImportDescriptor descriptor = entry.getValue();
            list.add(new ModuleImportDescriptor(descriptor.moduleName, descriptor.memberName, global.name(), valueType));
        }
        return new Sequence<>(list);
    }

    private static String functionTypeToString(WasmFunction f) {
        StringBuilder typeInfo = new StringBuilder();

        typeInfo.append(f.index());

        typeInfo.append('(');
        int argumentCount = f.numArguments();
        for (int i = 0; i < argumentCount; i++) {
            typeInfo.append(ValueType.fromByteValue(f.argumentTypeAt(i)));
        }
        typeInfo.append(')');

        byte returnType = f.returnType();
        if (returnType != WasmType.VOID_TYPE) {
            typeInfo.append(ValueType.fromByteValue(f.returnType()));
        }
        return typeInfo.toString();
    }

    @SuppressWarnings("unused")
    public Sequence<ByteArrayBuffer> customSections(Object sectionName) {
        // TODO: Implement once we support custom sections.
        return null;
    }

    public WasmModule wasmModule() {
        return module;
    }
}
