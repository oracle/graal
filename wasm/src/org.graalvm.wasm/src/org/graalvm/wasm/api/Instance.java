/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.Pair;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.exception.WasmJsApiException;
import org.graalvm.wasm.exception.WasmJsApiException.Kind;
import org.graalvm.wasm.globals.ExportedWasmGlobal;
import org.graalvm.wasm.globals.WasmGlobal;
import org.graalvm.wasm.memory.WasmMemory;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;

public class Instance extends Dictionary {
    private final TruffleContext truffleContext;
    private final WasmModule module;
    private final WasmInstance instance;
    private final Object importObject;
    private final Dictionary exportObject;

    @CompilerDirectives.TruffleBoundary
    public Instance(TruffleContext truffleContext, WasmModule module, Object importObject) {
        this.truffleContext = truffleContext;
        this.module = module;
        this.importObject = importObject;
        final WasmContext instanceContext = WasmContext.get(null);
        this.instance = instantiateModule(instanceContext);
        instanceContext.linker().tryLink(instance);
        this.exportObject = initializeExports(instanceContext);
        addMembers(new Object[]{
                        "module", this.module,
                        "importObject", this.importObject,
                        "exports", this.exportObject,
        });
    }

    public Dictionary exports() {
        return exportObject;
    }

    private WasmInstance instantiateModule(WasmContext context) {
        final HashMap<String, ImportModule> importModules;
        // To read the content of the import object, we need to enter the parent context that this
        // import object originates from.
        Object prev = truffleContext.getParent().enter(null);
        try {
            importModules = readImportModules();
        } finally {
            truffleContext.getParent().leave(null, prev);
        }
        return instantiateCore(context, importModules);
    }

    private HashMap<String, ImportModule> readImportModules() {
        CompilerAsserts.neverPartOfCompilation();
        final Sequence<ModuleImportDescriptor> imports = WebAssembly.moduleImports(module);
        if (imports.getArraySize() != 0 && importObject == null) {
            throw new WasmJsApiException(Kind.TypeError, "Module requires imports, but import object is undefined.");
        }

        HashMap<String, ImportModule> importModules = new HashMap<>();
        final InteropLibrary lib = InteropLibrary.getUncached();
        try {
            int i = 0;
            while (i < WebAssembly.moduleImports(module).getArraySize()) {
                final ModuleImportDescriptor d = (ModuleImportDescriptor) WebAssembly.moduleImports(module).readArrayElement(i);
                final Object importedModule = getMember(importObject, d.module());
                final Object member = getMember(importedModule, d.name());
                switch (d.kind()) {
                    case function:
                        if (!lib.isExecutable(member)) {
                            throw new WasmJsApiException(Kind.LinkError, "Member " + member + " is not callable.");
                        }
                        WasmFunction f = module.importedFunction(d.name());
                        ensureImportModule(importModules, d.module()).addFunction(d.name(), Pair.create(f, member));
                        break;
                    case memory:
                        if (!(member instanceof WasmMemory)) {
                            throw new WasmJsApiException(Kind.LinkError, "Member " + member + " is not a valid memory.");
                        }
                        // TODO: Use the Interop API to access the memory.
                        ensureImportModule(importModules, d.module()).addMemory(d.name(), (WasmMemory) member);
                        break;
                    case table:
                        if (!(member instanceof WasmTable)) {
                            throw new WasmJsApiException(Kind.LinkError, "Member " + member + " is not a valid table.");
                        }
                        // TODO: Use the Interop API to access the table.
                        ensureImportModule(importModules, d.module()).addTable(d.name(), (WasmTable) member);
                        break;
                    case global:
                        if (!(member instanceof WasmGlobal)) {
                            throw new WasmJsApiException(Kind.LinkError, "Member " + member + " is not a valid global.");
                        }
                        ensureImportModule(importModules, d.module()).addGlobal(d.name(), (WasmGlobal) member);
                        break;
                    default:
                        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Unimplemented case: " + d.kind());
                }

                i += 1;
            }
        } catch (InvalidArrayIndexException | UnknownIdentifierException | ClassCastException | UnsupportedMessageException e) {
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Unexpected state.");
        }

        return importModules;
    }

    private WasmInstance instantiateCore(WasmContext context, HashMap<String, ImportModule> importModules) {
        for (Map.Entry<String, ImportModule> entry : importModules.entrySet()) {
            final String name = entry.getKey();
            final ImportModule importModule = entry.getValue();
            final WasmInstance importedInstance = importModule.createInstance(context.language(), context, name);
            context.register(importedInstance);
        }
        return context.readInstance(module);
    }

    private Dictionary initializeExports(WasmContext context) {
        CompilerAsserts.neverPartOfCompilation();
        Dictionary e = new Dictionary();
        for (String name : instance.module().exportedSymbols()) {
            WasmFunction function = instance.module().exportedFunctions().get(name);
            Integer globalIndex = instance.module().exportedGlobals().get(name);

            if (function != null) {
                final CallTarget target = instance.target(function.index());
                e.addMember(name, new WasmFunctionInstance(context, function, target));
            } else if (globalIndex != null) {
                final int index = globalIndex;
                final int address = instance.globalAddress(index);
                if (address < 0) {
                    WasmGlobal global = context.globals().externalGlobal(address);
                    e.addMember(name, global);
                } else {
                    final ValueType valueType = ValueType.fromByteValue(instance.symbolTable().globalValueType(index));
                    final boolean mutable = instance.symbolTable().isGlobalMutable(index);
                    e.addMember(name, new ExportedWasmGlobal(valueType, mutable, context.globals(), address));
                }
            } else if (instance.module().exportedMemoryNames().contains(name)) {
                final WasmMemory memory = instance.memory();
                e.addMember(name, memory);
            } else if (instance.module().exportedTableNames().contains(name)) {
                final WasmTable table = instance.table();
                e.addMember(name, table);
            } else {
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Exported symbol list does not match the actual exports.");
            }
        }
        return e;
    }

    private static ImportModule ensureImportModule(HashMap<String, ImportModule> importModules, String name) {
        ImportModule importModule = importModules.get(name);
        if (importModule == null) {
            importModule = new ImportModule();
            importModules.put(name, importModule);
        }
        return importModule;
    }

    private static Object getMember(Object object, String name) throws UnknownIdentifierException, UnsupportedMessageException {
        final InteropLibrary lib = InteropLibrary.getUncached();
        if (!lib.isMemberReadable(object, name)) {
            throw new WasmJsApiException(Kind.TypeError, "Object does not contain member " + name + ".");
        }
        return lib.readMember(object, name);
    }

}
