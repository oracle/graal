/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.source.Source;
import org.graalvm.wasm.exception.WasmExecutionException;
import org.graalvm.wasm.exception.WasmValidationException;
import org.graalvm.wasm.predefined.BuiltinModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WasmContext {
    private final Env env;
    private final WasmLanguage language;
    private final MemoryRegistry memoryRegistry;
    private final GlobalRegistry globals;
    private final TableRegistry tableRegistry;
    private final Linker linker;
    private final Map<String, WasmInstance> moduleInstances;
    private int moduleNameCount;

    public static WasmContext getCurrent() {
        return WasmLanguage.getCurrentContext();
    }

    public WasmContext(Env env, WasmLanguage language) {
        this.env = env;
        this.language = language;
        this.globals = new GlobalRegistry();
        this.tableRegistry = new TableRegistry();
        this.memoryRegistry = new MemoryRegistry();
        this.moduleInstances = new LinkedHashMap<>();
        this.linker = new Linker();
        this.moduleNameCount = 0;
        instantiateBuiltinInstances();
    }

    public CallTarget parse(Source source) {
        // TODO: Not used -- can we remove this?
        return env.parsePublic(source);
    }

    public Env environment() {
        return env;
    }

    public WasmLanguage language() {
        return language;
    }

    public MemoryRegistry memories() {
        return memoryRegistry;
    }

    public GlobalRegistry globals() {
        return globals;
    }

    public TableRegistry tables() {
        return tableRegistry;
    }

    public Linker linker() {
        return linker;
    }

    @SuppressWarnings("deprecation")
    public Iterable<com.oracle.truffle.api.Scope> getTopScopes() {
        // Go through all WasmModules parsed with this context, and create a Scope for each of them.
        ArrayList<com.oracle.truffle.api.Scope> scopes = new ArrayList<>();
        for (Map.Entry<String, WasmInstance> entry : moduleInstances.entrySet()) {
            com.oracle.truffle.api.Scope scope = com.oracle.truffle.api.Scope.newBuilder(entry.getKey(), entry.getValue()).build();
            scopes.add(scope);
        }
        return scopes;
    }

    /**
     * Returns the map with all the modules that have been parsed.
     */
    public Map<String, WasmInstance> moduleInstances() {
        return moduleInstances;
    }

    public void register(WasmInstance instance) {
        if (moduleInstances.containsKey(instance.name())) {
            throw new WasmExecutionException(null, "Context already contains an instance named '" + instance.name() + "'.");
        }
        moduleInstances.put(instance.name(), instance);
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
                throw new WasmValidationException("Module specification '" + moduleSpec + "' is not valid.");
            }
            final String name = parts[0];
            final String key = parts.length == 2 ? parts[1] : parts[0];
            final WasmInstance module = BuiltinModule.createBuiltinInstance(language, this, name, key);
            moduleInstances.put(name, module);
        }
    }

    private String freshModuleName() {
        return "module-" + moduleNameCount++;
    }

    public WasmModule readModule(byte[] data) {
        return readModule(freshModuleName(), data);
    }

    public WasmModule readModule(String moduleName, byte[] data) {
        final WasmOptions.StoreConstantsPolicyEnum storeConstantsPolicy = WasmOptions.StoreConstantsPolicy.getValue(this.environment().getOptions());
        final WasmModule module = new WasmModule(moduleName, data, storeConstantsPolicy);
        final BinaryParser reader = new BinaryParser(language, module);
        reader.readModule();
        return module;
    }

    public WasmInstance readInstance(WasmModule module) {
        final WasmInstance instance = new WasmInstance(module, module.storeConstantsPolicy());
        final BinaryParser reader = new BinaryParser(language, module);
        reader.readInstance(this, instance);
        this.register(instance);
        return instance;
    }

    public void reinitInstance(WasmInstance instance) {
        // Note: this is not a complete and correct instantiation as defined in
        // https://webassembly.github.io/spec/core/exec/modules.html#instantiation
        // For testing only.
        final BinaryParser reader = new BinaryParser(language, instance.module());
        reader.resetGlobalState(this, instance);
        reader.resetMemoryState(this, instance);
        final WasmFunction startFunction = instance.symbolTable().startFunction();
        if (startFunction != null) {
            instance.target(startFunction.index());
        }
    }
}
