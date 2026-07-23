/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test;

import org.graalvm.polyglot.Context;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.constants.Mutability;
import org.graalvm.wasm.types.DefinedType;
import org.graalvm.wasm.types.FieldType;
import org.graalvm.wasm.types.FunctionType;
import org.graalvm.wasm.types.NumberType;
import org.graalvm.wasm.types.RecursiveTypes;
import org.graalvm.wasm.types.ReferenceType;
import org.graalvm.wasm.types.StructType;
import org.graalvm.wasm.types.SubType;
import org.graalvm.wasm.types.ValueType;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;

public class TypeRepresentationSuite {

    private static DefinedType recursiveFunctionType(NumberType fieldType) {
        FunctionType functionType = new FunctionType(
                        new ValueType[]{new ReferenceType(false, DefinedType.makeRecursiveReference(1))},
                        ValueType.EMPTY);
        StructType structType = new StructType(
                        new FieldType[]{new FieldType(fieldType, Mutability.CONSTANT)});
        SubType[] subTypes = {
                        new SubType(true, null, functionType),
                        new SubType(true, null, structType)
        };
        RecursiveTypes recursiveTypes = new RecursiveTypes(subTypes);
        DefinedType definedType = DefinedType.makeTopLevelType(recursiveTypes, 0);
        for (SubType subType : subTypes) {
            subType.unroll(recursiveTypes);
        }
        return definedType;
    }

    @Test
    public void testInteropCallAdapterUsesTopLevelTypeEquality() {
        DefinedType firstI32Type = recursiveFunctionType(NumberType.I32);
        DefinedType secondI32Type = recursiveFunctionType(NumberType.I32);
        DefinedType i64Type = recursiveFunctionType(NumberType.I64);

        // When looking at the components of the two defined function types for i32 and i64, they
        // are structurally the same, but the recursive references resolve to different types in
        // different recursive groups.
        Assert.assertEquals(firstI32Type.asFunctionType(), i64Type.asFunctionType());
        // When comparing the defined types referencing i32 and i64 structs as a whole, they should
        // not be equal.
        Assert.assertNotEquals(firstI32Type, i64Type);
        // The two function types that reference an i32 struct should still be seen as equal.
        Assert.assertEquals(firstI32Type, secondI32Type);

        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            WasmTestUtils.runInWasmContext(context, wasmContext -> {
                CallTarget firstI32Adapter = wasmContext.language().interopCallAdapterFor(firstI32Type);
                CallTarget secondI32Adapter = wasmContext.language().interopCallAdapterFor(secondI32Type);
                CallTarget i64Adapter = wasmContext.language().interopCallAdapterFor(i64Type);

                // Functions with equivalent types should share the same adapter.
                Assert.assertSame(firstI32Adapter, secondI32Adapter);
                // Functions whose component types are structurally identical but which are
                // semantically distinct (because of recursive references resolving to different
                // types) should have different call adapters.
                Assert.assertNotSame(firstI32Adapter, i64Adapter);
            });
        }
    }
}
