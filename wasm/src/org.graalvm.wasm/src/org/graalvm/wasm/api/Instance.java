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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import org.graalvm.wasm.exception.WasmExecutionException;
import org.graalvm.wasm.exception.WasmJsApiException;
import org.graalvm.wasm.exception.WasmJsApiException.Kind;

import java.util.HashMap;

@ExportLibrary(InteropLibrary.class)
public class Instance extends Dictionary {
    private final Module module;
    private final Dictionary importObject;

    @CompilerDirectives.TruffleBoundary
    public Instance(Module module, Dictionary importObject) {
        this.module = module;
        this.importObject = importObject;
        readImports(importObject);
        addMembers(new Object[]{
                        "module", this.module,
                        "importObject", this.importObject,
                        // TODO: Return a sequence of exported functions.
                        "exports", new Executable(args -> null),
        });
    }

    private void readImports(Dictionary importObject) {
        final Sequence<ModuleImportDescriptor> imports = module.imports();
        if (imports.getArraySize() != 0 && importObject != null) {
            throw new WasmJsApiException(Kind.TypeError, "Module requires imports, but import object is undefined.");
        }

        HashMap<String, Executable> functions = new HashMap<>();
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
                        Executable f = (Executable) member;
                        functions.put(d.name(), f);
                        break;
                    default:
                        throw new WasmExecutionException(null, "Unimplemented case.");
                }

                i += 1;
            }
        } catch (InvalidArrayIndexException | UnknownIdentifierException | ClassCastException e) {
            throw new WasmExecutionException(null, "Unexpected state.", e);
        }
    }

    private Object getMember(Dictionary object, String name) throws UnknownIdentifierException {
        if (!object.isMemberReadable(name)) {
            throw new WasmJsApiException(Kind.TypeError, "Object does not contain member " + name + ".");
        }
        return object.readMember(name);
    }

}
