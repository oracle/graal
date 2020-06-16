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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.source.Source;
import org.graalvm.wasm.exception.WasmValidationException;
import org.graalvm.wasm.predefined.BuiltinModule;

public final class WasmContext {
    private final Env env;
    private final WasmLanguage language;
    private final MemoryRegistry memoryRegistry;
    private final GlobalRegistry globals;
    private final TableRegistry tableRegistry;
    private final Linker linker;
    private Map<String, WasmModule> modules;

    public static WasmContext getCurrent() {
        return WasmLanguage.getCurrentContext();
    }

    public WasmContext(Env env, WasmLanguage language) {
        this.env = env;
        this.language = language;
        this.globals = new GlobalRegistry();
        this.tableRegistry = new TableRegistry();
        this.memoryRegistry = new MemoryRegistry();
        this.modules = new LinkedHashMap<>();
        this.linker = new Linker(language);
        initializeBuiltinModules();
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

    public Iterable<Scope> getTopScopes() {
        // Go through all WasmModules parsed with this context, and create a Scope for each of them.
        ArrayList<Scope> scopes = new ArrayList<>();
        for (Map.Entry<String, WasmModule> entry : modules.entrySet()) {
            Scope scope = Scope.newBuilder(entry.getKey(), entry.getValue()).build();
            scopes.add(scope);
        }
        return scopes;
    }

    /**
     * Returns the map with all the modules that have been parsed.
     */
    public Map<String, WasmModule> modules() {
        return modules;
    }

    void registerModule(WasmModule module) {
        if (modules.containsKey(module.name())) {
            throw new RuntimeException("Context already contains a module named '" + module.name() + "'.");
        }
        modules.put(module.name(), module);
    }

    private void initializeBuiltinModules() {
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
            final WasmModule module = BuiltinModule.createBuiltinModule(language, this, name, key);
            modules.put(name, module);
        }
    }
}
