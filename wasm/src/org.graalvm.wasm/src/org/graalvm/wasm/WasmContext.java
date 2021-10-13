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
package org.graalvm.wasm;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.parser.module.WasmModule;
import org.graalvm.wasm.predefined.BuiltinModule;
import org.graalvm.wasm.predefined.wasi.fd.FdManager;
import org.graalvm.wasm.runtime.WasmFunctionType;
import org.graalvm.wasm.runtime.WasmFunctionTypeStore;
import org.graalvm.wasm.runtime.WasmGlobal;
import org.graalvm.wasm.runtime.WasmModuleInstance;
import org.graalvm.wasm.runtime.WasmTable;
import org.graalvm.wasm.runtime.memory.WasmMemory;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

public final class WasmContext {
    private static final WasmTable[] EMPTY_TABLES = new WasmTable[0];
    private static final WasmMemory[] EMPTY_MEMORIES = new WasmMemory[0];
    private static final WasmGlobal[] EMPTY_GLOBALS = new WasmGlobal[0];

    private final Env env;
    private final WasmLanguage language;
    @CompilationFinal private int moduleNameCount;
    private final FdManager filesManager;
    private final WasmContextOptions contextOptions;

    private final Map<String, WasmModuleInstance> moduleInstances;
    @CompilationFinal(dimensions = 1) private WasmTable[] tables;
    @CompilationFinal(dimensions = 1) private WasmMemory[] memories;
    @CompilationFinal(dimensions = 1) private WasmGlobal[] globals;

    @CompilationFinal private int tableIndex = 0;
    @CompilationFinal private int memoryIndex = 0;
    @CompilationFinal private int globalIndex = 0;

    private final WasmFunctionTypeStore functionTypes;

    @CompilationFinal private WasmModuleInstance wasi;

    public WasmContext(Env env, WasmLanguage language) {
        this.env = env;
        this.language = language;
        this.moduleNameCount = 0;
        this.filesManager = new FdManager(env);
        this.contextOptions = WasmContextOptions.fromOptionValues(env.getOptions());

        this.moduleInstances = new LinkedHashMap<>();
        this.tables = EMPTY_TABLES;
        this.memories = EMPTY_MEMORIES;
        this.globals = EMPTY_GLOBALS;

        this.functionTypes = new WasmFunctionTypeStore();
        instantiateBuiltinInstances();
    }

    public Env environment() {
        return env;
    }

    public WasmLanguage language() {
        return language;
    }

    public WasmTable[] getTables() {
        return tables;
    }

    public WasmMemory[] getMemories() {
        return memories;
    }

    public void addTable(WasmTable table) {
        tables[tableIndex++] = table;
    }

    public void addMemory(WasmMemory memory) {
        memories[memoryIndex++] = memory;
    }

    public void addGlobal(WasmGlobal global) {
        globals[globalIndex++] = global;
    }

    public void increaseTablesSize(int size) {
        final WasmTable[] updatedTables = new WasmTable[tables.length + size];
        System.arraycopy(tables, 0, updatedTables, 0, tables.length);
        tables = updatedTables;
    }

    public void increaseMemoriesSize(int size) {
        final WasmMemory[] updatedMemories = new WasmMemory[memories.length + size];
        System.arraycopy(memories, 0, updatedMemories, 0, memories.length);
        memories = updatedMemories;
    }

    public void increaseGlobalsSize(int size) {
        final WasmGlobal[] updatedGlobals = new WasmGlobal[globals.length + size];
        System.arraycopy(globals, 0, updatedGlobals, 0, globals.length);
        globals = updatedGlobals;
    }

    public WasmGlobal[] getGlobalCopy() {
        WasmGlobal[] copy = new WasmGlobal[globals.length];
        for (int i = 0; i < globals.length; i++) {
            final WasmGlobal global = globals[i];
            copy[i] = new WasmGlobal(global.getValueType(), global.getMutability(), global.loadAsLong());
        }
        return copy;
    }

    public WasmFunctionType getFunctionType(byte[] parameterTypes, byte returnType) {
        return functionTypes.getFunctionType(parameterTypes, returnType);
    }

    public WasmFunctionType getFunctionType(byte[] parameterTypes, byte[] returnTypes) {
        return functionTypes.getFunctionType(parameterTypes, returnTypes);
    }

    @SuppressWarnings("unused")
    public Object getScope() {
        return new WasmScope(moduleInstances);
    }

    public FdManager fdManager() {
        return filesManager;
    }

    /**
     * Returns the map with all the modules that have been parsed.
     */
    public Map<String, WasmModuleInstance> moduleInstances() {
        return moduleInstances;
    }

    public void register(WasmModuleInstance instance) {
        if (moduleInstances.containsKey(instance.getName())) {
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Context already contains an instance named '" + instance.getName() + "'.");
        }
        moduleInstances.put(instance.getName(), instance);
    }

    private void instantiateBuiltinInstances() {
        final String extraModuleValue = WasmOptions.Builtins.getValue(env.getOptions());
        if (extraModuleValue.equals("")) {
            return;
        }
        final String[] moduleSpecs = extraModuleValue.split(",");
        for (String moduleSpec : moduleSpecs) {
            final String[] parts = moduleSpec.split(":");
            if (parts.length > 2) {
                throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Module specification '" + moduleSpec + "' is not valid.");
            }
            final String name = parts[0];
            final String key = parts.length == 2 ? parts[1] : parts[0];
            final WasmModuleInstance module = BuiltinModule.createBuiltinInstance(language, this, name, key, ModuleLimits.DEFAULTS);
            moduleInstances.put(name, module);
            if (name.equals(BuiltinModule.WASI_NAME)) {
                wasi = module;
            }
        }
    }

    private String freshModuleName() {
        return "module-" + moduleNameCount++;
    }

    public WasmModule readModule(byte[] data, ModuleLimits moduleLimits) {
        String moduleName = freshModuleName();
        Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(data), moduleName).build();
        return readModule(moduleName, data, moduleLimits, source);
    }

    public WasmModule readModule(String moduleName, byte[] data, ModuleLimits moduleLimits, Source source) {
        final BinaryParser reader = new BinaryParser(this, data, moduleLimits);
        return reader.readModule(moduleName, source);
    }

    public WasmModuleInstance readInstance(WasmModule module, ModuleLimits limits) {
        if (moduleInstances.containsKey(module.getName())) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, null, "Module " + module.getName() + " is already instantiated in this context.");
        }
        final WasmInstantiator instantiator = new WasmInstantiator(language, limits);
        final WasmModuleInstance instance = instantiator.createInstance(this, module, moduleInstances);
        this.register(instance);
        return instance;
    }

    public void reinitializeInstance(WasmModuleInstance instance, boolean reinitializeMemory) {
        // Note: this is not a complete and correct instantiation as defined in
        // https://webassembly.github.io/spec/core/exec/modules.html#instantiation
        // For testing only.
        new WasmInstantiator(language, null).reinitializeInstance(instance, reinitializeMemory);
    }

    public WasmContextOptions getContextOptions() {
        return this.contextOptions;
    }

    private static final ContextReference<WasmContext> REFERENCE = ContextReference.create(WasmLanguage.class);

    public static WasmContext get(Node node) {
        return REFERENCE.get(node);
    }

    public boolean hasWasi() {
        return wasi != null;
    }

    public WasmModuleInstance getWasiInstance() {
        return wasi;
    }

}
