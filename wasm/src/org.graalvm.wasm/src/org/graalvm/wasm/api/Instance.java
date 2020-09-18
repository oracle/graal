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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import org.graalvm.collections.Pair;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.exception.WasmExecutionException;
import org.graalvm.wasm.exception.WasmJsApiException;
import org.graalvm.wasm.exception.WasmJsApiException.Kind;

import java.util.HashMap;
import java.util.Map;

@ExportLibrary(InteropLibrary.class)
public class Instance extends Dictionary {
    private final TruffleContext truffleContext;
    private final Module module;
    private final WasmInstance instance;
    private final Dictionary importObject;
    private final Dictionary exportObject;

    @CompilerDirectives.TruffleBoundary
    public Instance(TruffleContext truffleContext, Module module, Dictionary importObject) {
        this.truffleContext = truffleContext;
        this.module = module;
        this.importObject = importObject;
        this.instance = instantiateModule(WasmContext.getCurrent());
        this.exportObject = initializeExports();
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
        final HashMap<String, ImportModule> importModules = readImportModules();
        final WasmInstance wasmInstance = instantiateCore(context, importModules);
        return wasmInstance;
    }

    private HashMap<String, ImportModule> readImportModules() {
        final Sequence<ModuleImportDescriptor> imports = module.imports();
        if (imports.getArraySize() != 0 && importObject != null) {
            throw new WasmJsApiException(Kind.TypeError, "Module requires imports, but import object is undefined.");
        }

        HashMap<String, ImportModule> importModules = new HashMap<>();
        try {
            int i = 0;
            while (i < module.imports().getArraySize()) {
                final ModuleImportDescriptor d = (ModuleImportDescriptor) module.imports().readArrayElement(i);
                final Dictionary importedModule = (Dictionary) getMember(importObject, d.module());
                final Object member = getMember(importedModule, d.name());
                switch(d.kind()) {
                    case function:
                        if (!(member instanceof Executable)) {
                            throw new WasmJsApiException(Kind.LinkError, "Member " + member + " is not callable.");
                        }
                        Executable e = (Executable) member;
                        WasmFunction f = module.wasmModule().importedFunction(d.name());
                        ensureImportModule(importModules, d.name()).addFunction(d.name(), Pair.create(f, e));
                        break;
                    default:
                        throw new WasmExecutionException(null, "Unimplemented case.");
                }

                i += 1;
            }
        } catch (InvalidArrayIndexException | UnknownIdentifierException | ClassCastException e) {
            throw new WasmExecutionException(null, "Unexpected state.", e);
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
        return context.readInstance(module.wasmModule());
    }

    private Dictionary initializeExports() {
        Dictionary e = new Dictionary();
        for (Map.Entry<String, WasmFunction> entry : instance.symbolTable().exportedFunctions().entrySet()) {
            String name = entry.getKey();
            WasmFunction function = entry.getValue();
            final CallTarget target = instance.target(function.index());
            e.addMember(name, new Executable(args -> {
                final Object prev = truffleContext.enter();
                try {
                    return target.call(args);
                } finally {
                    truffleContext.leave(prev);
                }
            }));
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

    private static Object getMember(Dictionary object, String name) throws UnknownIdentifierException {
        if (!object.isMemberReadable(name)) {
            throw new WasmJsApiException(Kind.TypeError, "Object does not contain member " + name + ".");
        }
        return object.readMember(name);
    }

}
