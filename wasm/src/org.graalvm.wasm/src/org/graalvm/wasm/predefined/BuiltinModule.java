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
package org.graalvm.wasm.predefined;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.wasm.Assert;
import org.graalvm.wasm.ReferenceTypes;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.emscripten.EmscriptenModule;
import org.graalvm.wasm.predefined.spectest.SpectestModule;
import org.graalvm.wasm.predefined.testutil.TestutilModule;
import org.graalvm.wasm.predefined.wasi.WasiModule;

import java.util.HashMap;
import java.util.Map;

public abstract class BuiltinModule {
    private static final Map<String, BuiltinModule> predefinedModules = new HashMap<>();

    static {
        final Map<String, BuiltinModule> pm = predefinedModules;
        pm.put("emscripten", new EmscriptenModule());
        pm.put("testutil", new TestutilModule());
        pm.put("wasi_snapshot_preview1", new WasiModule());
        pm.put("spectest", new SpectestModule());
    }

    public static WasmInstance createBuiltinInstance(WasmLanguage language, WasmContext context, String name, String predefinedModuleName) {
        final BuiltinModule builtinModule = predefinedModules.get(predefinedModuleName);
        if (builtinModule == null) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Unknown predefined module: " + predefinedModuleName);
        }
        return builtinModule.createInstance(language, context, name);
    }

    protected abstract WasmInstance createInstance(WasmLanguage language, WasmContext context, String name);

    protected WasmFunction defineFunction(WasmInstance instance, String name, byte[] paramTypes, byte[] retTypes, RootNode rootNode) {
        // We could check if the same function type had already been allocated,
        // but this is just an optimization, and probably not very important,
        // since predefined modules have a relatively small size.
        final int typeIdx = instance.symbolTable().allocateFunctionType(paramTypes, retTypes);
        final WasmFunction function = instance.symbolTable().declareExportedFunction(typeIdx, name);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        instance.setTarget(function.index(), callTarget);
        return function;
    }

    protected int defineExternalGlobal(WasmInstance instance, String globalName, Object global) {
        int index = instance.symbolTable().maxGlobalIndex() + 1;
        instance.symbolTable().declareExportedExternalGlobal(globalName, index, global);
        return index;
    }

    protected int defineGlobal(WasmInstance instance, String name, byte valueType, byte mutability, long value) {
        int index = instance.symbolTable().maxGlobalIndex() + 1;
        instance.symbolTable().declareExportedGlobalWithValue(name, index, valueType, mutability, value);
        return index;
    }

    protected void defineExternalTable(WasmInstance instance, String tableName, WasmTable externalTable) {
        instance.symbolTable().allocateExternalTable(externalTable);
        instance.symbolTable().exportTable(tableName);
    }

    protected int defineTable(WasmInstance instance, String tableName, int initSize, int maxSize, byte type) {
        Assert.assertByteEqual(type, ReferenceTypes.FUNCREF, "Only function types are currently supported in tables.", Failure.UNSPECIFIED_MALFORMED);
        instance.symbolTable().allocateTable(initSize, maxSize);
        instance.symbolTable().exportTable(tableName);
        return 0;
    }

    protected void defineExternalMemory(WasmInstance instance, String memoryName, WasmMemory externalMemory) {
        instance.symbolTable().allocateExternalMemory(externalMemory);
        instance.symbolTable().exportMemory(memoryName);
    }

    protected void defineMemory(WasmInstance instance, String memoryName, int initSize, int maxSize) {
        instance.symbolTable().allocateMemory(initSize, maxSize);
        instance.symbolTable().exportMemory(memoryName);
    }

    protected void importFunction(WasmInstance instance, String importModuleName, String importFunctionName, byte[] paramTypes, byte[] retTypes, String exportName) {
        final int typeIdx = instance.symbolTable().allocateFunctionType(paramTypes, retTypes);
        final WasmFunction function = instance.symbolTable().importFunction(importModuleName, importFunctionName, typeIdx);
        instance.symbolTable().exportFunction(function.index(), exportName);
    }

    protected void importMemory(WasmInstance instance, String importModuleName, String memoryName, int initSize, int maxSize) {
        instance.symbolTable().importMemory(importModuleName, memoryName, initSize, maxSize);
    }

    protected byte[] types(byte... args) {
        return args;
    }
}
