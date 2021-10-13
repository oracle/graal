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
package org.graalvm.wasm.predefined;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.wasm.Assert;
import org.graalvm.wasm.ModuleLimits;
import org.graalvm.wasm.ReferenceTypes;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.runtime.WasmFunctionInstance;
import org.graalvm.wasm.runtime.WasmModuleInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmOptions;
import org.graalvm.wasm.constants.ExportIdentifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.runtime.memory.ByteArrayWasmMemory;
import org.graalvm.wasm.runtime.memory.UnsafeWasmMemory;
import org.graalvm.wasm.runtime.memory.WasmMemory;
import org.graalvm.wasm.parser.module.WasmExternalValue;
import org.graalvm.wasm.predefined.emscripten.EmscriptenModule;
import org.graalvm.wasm.predefined.spectest.SpectestModule;
import org.graalvm.wasm.predefined.testutil.TestutilModule;
import org.graalvm.wasm.predefined.wasi.WasiModule;
import org.graalvm.wasm.runtime.WasmFunctionType;
import org.graalvm.wasm.runtime.WasmGlobal;
import org.graalvm.wasm.runtime.WasmTable;

import java.util.HashMap;
import java.util.Map;

import static org.graalvm.wasm.WasmMath.minUnsigned;

public abstract class BuiltinModule {
    private static final Map<String, BuiltinModule> predefinedModules = new HashMap<>();

    protected static final WasmGlobal[] EMPTY_GLOBALS = new WasmGlobal[0];

    public static final String WASI_NAME = "wasi_snapshot_preview1";

    static {
        final Map<String, BuiltinModule> pm = predefinedModules;
        pm.put("emscripten", new EmscriptenModule());
        pm.put("testutil", new TestutilModule());
        pm.put(WASI_NAME, new WasiModule());
        pm.put("spectest", new SpectestModule());
    }

    private int exportIndex = 0;

    public static WasmModuleInstance createBuiltinInstance(WasmLanguage language, WasmContext context, String name, String predefinedModuleName, ModuleLimits limits) {
        CompilerAsserts.neverPartOfCompilation();
        final BuiltinModule builtinModule = predefinedModules.get(predefinedModuleName);
        if (builtinModule == null) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Unknown predefined module: " + predefinedModuleName);
        }
        return builtinModule.createInstance(language, context, name, limits);
    }

    protected abstract WasmModuleInstance createInstance(WasmLanguage language, WasmContext context, String name, ModuleLimits limits);

    protected void reexportFunction(WasmModuleInstance instance, String name, WasmFunctionInstance function) {
        final int index = instance.addFunction(function);
        final WasmExternalValue externalValue = new WasmExternalValue(exportIndex++, (byte) ExportIdentifier.FUNCTION, index);
        instance.addExport(name, externalValue);
    }

    protected void exportFunction(WasmContext context, WasmModuleInstance instance, String name, byte[] paramTypes, byte[] retTypes, RootNode rootNode) {
        defineFunction(context, instance, name, paramTypes, retTypes, rootNode, true);
    }

    protected void defineFunction(WasmContext context, WasmModuleInstance instance, String name, byte[] paramTypes, byte[] retTypes, RootNode rootNode, boolean export) {
        final WasmFunctionType functionType = context.getFunctionType(paramTypes, retTypes);
        final WasmFunctionInstance function = new WasmFunctionInstance(context, functionType, rootNode.getCallTarget(), name, instance.getNextFunctionIndex());
        final int index = instance.addFunction(function);
        if (export) {
            final WasmExternalValue externalValue = new WasmExternalValue(exportIndex++, (byte) ExportIdentifier.FUNCTION, index);
            instance.addExport(name, externalValue);
        }
    }

    protected void exportTable(WasmModuleInstance instance, String tableName, int initSize, int maxSize, byte type, ModuleLimits limits) {
        defineTable(instance, tableName, initSize, maxSize, type, limits, true);
    }

    protected void defineTable(WasmModuleInstance instance, String tableName, int initSize, int maxSize, byte type, ModuleLimits limits, boolean export) {
        Assert.assertByteEqual(type, ReferenceTypes.FUNCREF, "Only function types are currently supported in tables.", Failure.UNSPECIFIED_MALFORMED);
        final int maxAllowedSize = minUnsigned(maxSize, limits.tableInstanceSizeLimit());
        final WasmTable table = new WasmTable(initSize, maxSize, maxAllowedSize);
        instance.addTable(table);
        if (export) {
            final WasmExternalValue externalValue = new WasmExternalValue(exportIndex++, (byte) ExportIdentifier.TABLE, -1);
            instance.addExport(tableName, externalValue);
        }
    }

    protected void exportMemory(WasmContext context, WasmModuleInstance instance, String memoryName, int initSize, int maxSize, ModuleLimits limits) {
        defineMemory(context, instance, memoryName, initSize, maxSize, limits, true);
    }

    protected void defineMemory(WasmContext context, WasmModuleInstance instance, String memoryName, int initSize, int maxSize, ModuleLimits limits, boolean export) {
        final int maxAllowedSize = minUnsigned(maxSize, limits.memoryInstanceSizeLimit());
        final WasmMemory memory;
        if (context.environment().getOptions().get(WasmOptions.UseUnsafeMemory)) {
            memory = new UnsafeWasmMemory(initSize, maxSize, maxAllowedSize);
        } else {
            memory = new ByteArrayWasmMemory(initSize, maxSize, maxAllowedSize);
        }
        instance.addMemory(memory);
        if (export) {
            final WasmExternalValue externalValue = new WasmExternalValue(exportIndex++, (byte) ExportIdentifier.MEMORY, -1);
            instance.addExport(memoryName, externalValue);
        }
    }

    protected void exportGlobal(WasmModuleInstance instance, String name, byte valueType, byte mutability, long value) {
        defineGlobal(instance, name, valueType, mutability, value, true);
    }

    protected void defineGlobal(WasmModuleInstance instance, String name, byte valueType, byte mutability, long value, boolean export) {
        final WasmGlobal global = new WasmGlobal(valueType, mutability, value);
        final int index = instance.addGlobal(global);
        if (export) {
            final WasmExternalValue externalValue = new WasmExternalValue(exportIndex++, (byte) ExportIdentifier.GLOBAL, index);
            instance.addExport(name, externalValue);
        }
    }

    protected byte[] types(byte... args) {
        return args;
    }
}
