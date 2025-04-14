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

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.parser.bytecode.BytecodeParser;
import org.graalvm.wasm.predefined.BuiltinModule;
import org.graalvm.wasm.predefined.wasi.fd.FdManager;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

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
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class WasmStore implements TruffleObject {
    private final WasmContext context;
    private final WasmLanguage language;
    private final MemoryRegistry memoryRegistry;
    private final GlobalRegistry globals;
    private final TableRegistry tableRegistry;
    private final Linker linker;
    private final Map<String, WasmInstance> moduleInstances;
    private WasmInstance mainModuleInstance;
    private final FdManager filesManager;
    private final WasmContextOptions contextOptions;

    public WasmStore(WasmContext context, WasmLanguage language) {
        this.context = context;
        this.language = language;
        this.contextOptions = context.getContextOptions();
        this.globals = new GlobalRegistry();
        this.tableRegistry = new TableRegistry();
        this.memoryRegistry = new MemoryRegistry();
        this.moduleInstances = new LinkedHashMap<>();
        this.linker = new Linker();
        this.filesManager = context.fdManager();
        instantiateBuiltinInstances();
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

    public GlobalRegistry globals() {
        return globals;
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

    /**
     * Returns the first module evaluated in this context (not including built-in modules).
     */
    public WasmInstance lookupMainModule() {
        return mainModuleInstance;
    }

    public void register(WasmInstance instance) {
        if (moduleInstances.containsKey(instance.name())) {
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Context already contains an instance named '" + instance.name() + "'.");
        }
        moduleInstances.put(instance.name(), instance);
        if (mainModuleInstance == null && !instance.isBuiltin()) {
            mainModuleInstance = instance;
        }
    }

    private void instantiateBuiltinInstances() {
        final String extraModuleValue = WasmOptions.Builtins.getValue(environment().getOptions());
        if (extraModuleValue.isEmpty()) {
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
            final WasmInstance module = BuiltinModule.createBuiltinInstance(language, this, name, key);
            moduleInstances.put(name, module);
        }
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
        BytecodeParser.resetGlobalState(this, instance.module(), instance);
        if (reinitMemory) {
            BytecodeParser.resetMemoryState(this, instance.module(), instance);
            BytecodeParser.resetTableState(this, instance.module(), instance);
            Linker.runStartFunction(instance);
        }
    }

    public WasmContextOptions getContextOptions() {
        return this.contextOptions;
    }

    private static final String NEW_INSTANCES = "newInstances";

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        return moduleInstances.containsKey(member);
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException {
        if (!isMemberReadable(member)) {
            throw UnknownIdentifierException.create(member);
        }
        return moduleInstances.get(member);
    }

    @ExportMessage
    boolean isMemberInvocable(String member) {
        return NEW_INSTANCES.equals(member);
    }

    @ExportMessage
    Object invokeMember(String member, Object... arguments) throws ArityException, UnsupportedTypeException, UnknownIdentifierException {
        if (!isMemberInvocable(member)) {
            throw UnknownIdentifierException.create(member);
        }
        assert NEW_INSTANCES.equals(member);

        if (arguments.length == 0) {
            throw ArityException.create(1, -1, 0);
        }
        if (arguments[0] instanceof WasmModule m) {
            final WasmInstance instance = readInstance(m);
            for (int i = 1; i < arguments.length; i++) {
                if (arguments[i] instanceof WasmModule module) {
                    readInstance(module);
                } else {
                    throw UnsupportedTypeException.create(arguments);
                }
            }
            linker.tryLink(instance);
        } else {
            throw UnsupportedTypeException.create(arguments);
        }
        return WasmConstant.VOID;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        final String[] keys = new String[moduleInstances.size() + 1];
        int index = 0;
        for (String key : moduleInstances.keySet()) {
            keys[index++] = key;
        }
        keys[index] = NEW_INSTANCES;
        return new WasmNamesObject(keys);
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "wasm-store" + moduleInstances.keySet();
    }
}
