/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.api.WebAssembly;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.exception.WasmJsApiException;
import org.graalvm.wasm.parser.bytecode.BytecodeParser;
import org.graalvm.wasm.predefined.BuiltinModule;
import org.graalvm.wasm.predefined.wasi.fd.FdManager;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.TruffleObject;

/**
 * Holds shared (a.k.a. global) state that belongs to a module instantiation & linking context.
 * <p>
 * Modules that belong to different {@link WasmStore}s are linked, and can be garbage-collected,
 * independently.
 * <p>
 * Modules instantiated through the polyglot embedder API are always linked in the
 * {@link WasmContext}'s primary {@link WasmStore}, while modules instantiated through the
 * {@code module_instantiate} JS API each get their own private {@link WasmStore}.
 */
public final class WasmStore implements TruffleObject {
    private final WasmContext context;
    private final WasmLanguage language;
    private final MemoryRegistry memoryRegistry;
    private final TableRegistry tableRegistry;
    private final Linker linker;
    private final Map<String, WasmInstance> moduleInstances;
    private final FdManager filesManager;
    private final WasmContextOptions contextOptions;

    public WasmStore(WasmContext context, WasmLanguage language) {
        this.context = context;
        this.language = language;
        this.contextOptions = context.getContextOptions();
        this.tableRegistry = new TableRegistry();
        this.memoryRegistry = new MemoryRegistry();
        this.moduleInstances = new LinkedHashMap<>();
        this.linker = new Linker();
        this.filesManager = context.fdManager();
    }

    public WasmContext context() {
        return context;
    }

    public Env environment() {
        return context.environment();
    }

    public WasmLanguage language() {
        return language;
    }

    public MemoryRegistry memories() {
        return memoryRegistry;
    }

    public TableRegistry tables() {
        return tableRegistry;
    }

    public Linker linker() {
        return linker;
    }

    public FdManager fdManager() {
        return filesManager;
    }

    /**
     * Returns the map with all the modules that have been parsed.
     */
    public Map<String, WasmInstance> moduleInstances() {
        return moduleInstances;
    }

    @TruffleBoundary
    public WasmInstance lookupModuleInstance(WasmModule module) {
        WasmInstance instance = moduleInstances.get(module.name());
        assert instance == null || instance.module() == module;
        return instance;
    }

    @TruffleBoundary
    public WasmInstance lookupModuleInstance(String name) {
        return moduleInstances.get(name);
    }

    public void register(WasmInstance instance) {
        if (moduleInstances.containsKey(instance.name())) {
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Context already contains an instance named '" + instance.name() + "'.");
        }
        moduleInstances.put(instance.name(), instance);
    }

    public ImportValueSupplier instantiateBuiltinInstances() {
        final Map<String, BuiltinModule> builtinModules = WasmOptions.Builtins.getValue(environment().getOptions());
        var importValues = ImportValueSupplier.none();
        if (builtinModules.isEmpty()) {
            return importValues;
        }
        for (Map.Entry<String, BuiltinModule> entry : builtinModules.entrySet()) {
            final String name = entry.getKey();
            final BuiltinModule builtinModule = entry.getValue();

            final WasmInstance importingModuleInstance = lookupImportingModuleInstance(name);
            // only instantiate built-in modules that are actually imported
            if (importingModuleInstance == null) {
                continue;
            }
            final WasmInstance builtinInstance = builtinModule.createInstance(language, this, name);
            moduleInstances.put(name, builtinInstance);
            if (builtinInstance.module().numImportedSymbols() != 0) {
                // Link imports of the built-in module (WASIp1 memory) to the importing module.
                importValues = importValues.andThen((importDesc, intoInstance) -> {
                    if (intoInstance == builtinInstance) {
                        try {
                            return WebAssembly.instanceExport(importingModuleInstance, importDesc.memberName());
                        } catch (WasmJsApiException e) {
                            // fallthrough
                        }
                    }
                    return null;
                });
            }
        }
        return importValues;
    }

    private WasmInstance lookupImportingModuleInstance(String moduleName) {
        for (WasmInstance instance : moduleInstances().values()) {
            for (ImportDescriptor importDesc : instance.module().importedSymbols()) {
                if (moduleName.equals(importDesc.moduleName())) {
                    return instance;
                }
            }
        }
        return null;
    }

    @TruffleBoundary
    public WasmInstance readInstance(WasmModule module) {
        if (moduleInstances.containsKey(module.name())) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, null, "Module " + module.name() + " is already instantiated in this context.");
        }
        final WasmInstantiator translator = new WasmInstantiator(language);
        final WasmInstance instance = translator.createInstance(this, module);
        this.register(instance);
        return instance;
    }

    public void reinitInstance(WasmInstance instance, boolean reinitMemory) {
        // Note: this is not a complete and correct instantiation as defined in
        // https://webassembly.github.io/spec/core/exec/modules.html#instantiation
        // For testing only.
        BytecodeParser.resetGlobalState(instance.module(), instance);
        if (reinitMemory) {
            BytecodeParser.resetMemoryState(instance.module(), instance);
            BytecodeParser.resetTableState(this, instance.module(), instance);
            Linker.runStartFunction(instance);
        }
    }

    public WasmContextOptions getContextOptions() {
        return this.contextOptions;
    }
}
