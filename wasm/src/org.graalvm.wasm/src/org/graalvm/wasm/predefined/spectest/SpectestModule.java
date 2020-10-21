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

package org.graalvm.wasm.predefined.spectest;

import org.graalvm.wasm.ReferenceTypes;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmOptions;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.predefined.BuiltinModule;

import static org.graalvm.wasm.WasmType.F32_TYPE;
import static org.graalvm.wasm.WasmType.F64_TYPE;
import static org.graalvm.wasm.WasmType.I32_TYPE;
import static org.graalvm.wasm.WasmType.I64_TYPE;

public class SpectestModule extends BuiltinModule {
    @Override
    protected WasmInstance createInstance(WasmLanguage language, WasmContext context, String name) {
        final WasmOptions.StoreConstantsPolicyEnum storeConstantsPolicy = WasmOptions.StoreConstantsPolicy.getValue(context.environment().getOptions());
        WasmInstance module = new WasmInstance(new WasmModule(name, null, storeConstantsPolicy), storeConstantsPolicy);
        defineFunction(module, "print", types(), types(), new Print(language, module));
        defineFunction(module, "print_i32", types(I32_TYPE), types(), new Print(language, module));
        defineFunction(module, "print_i64", types(I64_TYPE), types(), new Print(language, module));
        defineFunction(module, "print_f32", types(F32_TYPE), types(), new Print(language, module));
        defineFunction(module, "print_f64", types(F64_TYPE), types(), new Print(language, module));
        defineFunction(module, "print_i32_f32", types(I32_TYPE, F32_TYPE), types(), new Print(language, module));
        defineFunction(module, "print_f64_f64", types(F64_TYPE, F64_TYPE), types(), new Print(language, module));
        defineGlobal(module, "global_i32", I32_TYPE, (byte) GlobalModifier.MUTABLE, 0);
        defineGlobal(module, "global_i64", I64_TYPE, (byte) GlobalModifier.MUTABLE, 0);
        defineGlobal(module, "global_f32", F32_TYPE, (byte) GlobalModifier.MUTABLE, 0);
        defineGlobal(module, "global_f64", F64_TYPE, (byte) GlobalModifier.MUTABLE, 0);
        defineTable(module, "table", 10, 20, ReferenceTypes.FUNCREF);
        defineMemory(module, "memory", 1, 2);
        return module;
    }
}
