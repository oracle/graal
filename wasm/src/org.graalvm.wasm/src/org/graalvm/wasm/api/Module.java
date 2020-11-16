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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.graalvm.wasm.ModuleLimits;
import org.graalvm.wasm.ImportDescriptor;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmCustomSection;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.constants.ImportIdentifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

public class Module extends Dictionary {
    // Limits specified by https://www.w3.org/TR/wasm-js-api/#limits
    private static final int MODULE_SIZE_LIMIT = 1 << 30;
    private static final int TYPE_COUNT_LIMIT = 1000000;
    private static final int FUNCTION_COUNT_LIMIT = 1000000;
    private static final int IMPORT_COUNT_LIMIT = 100000;
    private static final int EXPORT_COUNT_LIMIT = 100000;
    private static final int GLOBAL_COUNT_LIMIT = 1000000;
    private static final int DATA_SEGMENT_LIMIT = 100000;
    private static final int ELEMENT_SEGMENT_LIMIT = 10000000;
    private static final int FUNCTION_SIZE_LIMIT = 7654321;
    private static final int PARAM_COUNT_LIMIT = 1000;
    private static final int RETURN_COUNT_LIMIT = 1;
    private static final int LOCAL_COUNT_LIMIT = 50000;
    private static final int TABLE_SIZE_LIMIT = 10000000;
    private static final int MEMORY_SIZE_LIMIT = 32767;
    private static final ModuleLimits LIMITS = new ModuleLimits(
                    MODULE_SIZE_LIMIT,
                    TYPE_COUNT_LIMIT,
                    FUNCTION_COUNT_LIMIT,
                    IMPORT_COUNT_LIMIT,
                    EXPORT_COUNT_LIMIT,
                    GLOBAL_COUNT_LIMIT,
                    DATA_SEGMENT_LIMIT,
                    ELEMENT_SEGMENT_LIMIT,
                    FUNCTION_SIZE_LIMIT,
                    PARAM_COUNT_LIMIT,
                    RETURN_COUNT_LIMIT,
                    LOCAL_COUNT_LIMIT,
                    TABLE_SIZE_LIMIT,
                    MEMORY_SIZE_LIMIT);
    private final WasmModule module;

    public Module(WasmContext context, byte[] source) {
        this.module = context.readModule(source, LIMITS);
        addMembers(new Object[]{
                        "exports", new Executable(args -> exports()),
                        "imports", new Executable(args -> imports()),
                        "customSections", new Executable(args -> customSections(args[0])),
        });
    }

    public Sequence<ModuleExportDescriptor> exports() {
        final ArrayList<ModuleExportDescriptor> list = new ArrayList<>();
        for (String name : module.exportedSymbols()) {
            WasmFunction f = module.exportedFunctions().get(name);
            Integer globalIndex = module.exportedGlobals().get(name);

            if (Objects.equals(module.exportedMemory(), name)) {
                list.add(new ModuleExportDescriptor(name, memory.name(), null));
            } else if (Objects.equals(module.exportedTable(), name)) {
                list.add(new ModuleExportDescriptor(name, table.name(), null));
            } else if (f != null) {
                list.add(new ModuleExportDescriptor(name, function.name(), functionTypeToString(f)));
            } else if (globalIndex != null) {
                String valueType = ValueType.fromByteValue(module.globalValueType(globalIndex)).toString();
                list.add(new ModuleExportDescriptor(name, global.name(), valueType));
            } else {
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Exported symbol list does not match the actual exports.");
            }
        }
        return new Sequence<>(list);
    }

    public Sequence<ModuleImportDescriptor> imports() {
        final LinkedHashMap<ImportDescriptor, Integer> importedGlobalDescriptors = module.importedGlobalDescriptors();
        final ArrayList<ModuleImportDescriptor> list = new ArrayList<>();
        for (ImportDescriptor descriptor : module.importedSymbols()) {
            switch (descriptor.identifier) {
                case ImportIdentifier.FUNCTION:
                    final WasmFunction f = module.importedFunction(descriptor);
                    list.add(new ModuleImportDescriptor(f.importedModuleName(), f.importedFunctionName(), function.name(), functionTypeToString(f)));
                    break;
                case ImportIdentifier.TABLE:
                    if (Objects.equals(module.importedTable(), descriptor)) {
                        list.add(new ModuleImportDescriptor(descriptor.moduleName, descriptor.memberName, table.name(), null));
                    } else {
                        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Table import inconsistent.");
                    }
                    break;
                case ImportIdentifier.MEMORY:
                    if (Objects.equals(module.importedMemory(), descriptor)) {
                        list.add(new ModuleImportDescriptor(descriptor.moduleName, descriptor.memberName, memory.name(), null));
                    } else {
                        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Memory import inconsistent.");
                    }
                    break;
                case ImportIdentifier.GLOBAL:
                    final Integer index = importedGlobalDescriptors.get(descriptor);
                    String valueType = ValueType.fromByteValue(module.globalValueType(index)).toString();
                    list.add(new ModuleImportDescriptor(descriptor.moduleName, descriptor.memberName, global.name(), valueType));
                    break;
                default:
                    throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Unknown import descriptor type: " + descriptor.identifier);
            }
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

    public Sequence<ByteArrayBuffer> customSections(Object sectionName) {
        List<ByteArrayBuffer> sections = new ArrayList<>();
        for (WasmCustomSection section : module.customSections()) {
            if (section.getName().equals(sectionName)) {
                sections.add(new ByteArrayBuffer(module.data(), section.getOffset(), section.getLength()));
            }
        }
        return new Sequence<>(sections);
    }

    public WasmModule wasmModule() {
        return module;
    }
}
