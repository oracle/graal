/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.hosted.webimage.wasmgc.codegen;

import org.graalvm.webimage.api.JSValue;

import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instructions;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmFunctionTemplate;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmGCUtil;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.webimage.wasm.types.WasmPrimitiveType;
import com.oracle.svm.webimage.wasm.types.WasmUtil;
import com.oracle.svm.webimage.wasmgc.WasmExtern;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Collection of {@link WasmFunctionTemplate}s to support the {@link org.graalvm.webimage.api.JS}
 * annotation.
 */
public class WasmGCJSBodyTemplates {

    /**
     * Given an {@link JSValue} instance, gets or sets the hidden {@code jsNativeValue} field.
     * <p>
     * If the parameter is {@code true}, this is a get, otherwise a set.
     */
    public static class ExtractJSValue extends WasmFunctionTemplate<Boolean> {
        public ExtractJSValue(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected String getFunctionName(Boolean isGet) {
            return "jsnative." + (isGet ? "get" : "set");
        }

        public final WasmId.Func requestGetterFunctionId() {
            return requestFunctionId(true);
        }

        public final WasmId.Func requestSetterFunctionId() {
            return requestFunctionId(false);
        }

        @Override
        protected Function createFunction(Context ctxt) {
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            WasmGCUtil util = providers.util();
            ResolvedJavaType jsValueType = providers.getMetaAccess().lookupJavaType(JSValue.class);
            WasmId.StructType jsValueId = idFactory.newJavaStruct(jsValueType);
            WasmRefType jsValueRef = jsValueId.asNullable();

            WasmRefType wasmExternRef = util.typeForJavaClass(WasmExtern.class);

            boolean isGet = ctxt.getParameter();

            TypeUse typeUse = isGet ? TypeUse.forUnary(wasmExternRef, jsValueRef) : TypeUse.withoutResult(jsValueRef, wasmExternRef);

            Function f = ctxt.createFunction(typeUse, (isGet ? "Extract" : "Set") + "JavaScript Native Value");
            Instructions instructions = f.getInstructions();
            WasmId.Local objectParam = f.getParam(0);
            if (isGet) {
                instructions.add(new Instruction.StructGet(jsValueId, providers.knownIds().jsNativeValueField, WasmUtil.Extension.None, objectParam.getter()));
            } else {
                WasmId.Local valueParam = f.getParam(1);
                instructions.add(new Instruction.StructSet(jsValueId, providers.knownIds().jsNativeValueField, new Instruction.RefCast(objectParam.getter(), jsValueRef), valueParam.getter()));
            }

            return f;
        }
    }

    /**
     * Given an {@code externref} determines if it is a Java object instance.
     * <p>
     * Useful for JS code to identify Java objects.
     */
    public static class IsJavaObject extends WasmFunctionTemplate.Singleton {
        public IsJavaObject(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected String getFunctionName() {
            return "extern.isjavaobject";
        }

        @Override
        protected Function createFunction(WasmFunctionTemplate<Param>.Context ctxt) {
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            WasmGCUtil util = providers.util();
            WasmRefType jlObjectRef = util.getJavaLangObjectType().asNonNull();

            Function f = ctxt.createFunction(TypeUse.withResult(WasmPrimitiveType.i32, WasmRefType.EXTERNREF), "Check if reference is a Java Object");
            Instructions instructions = f.getInstructions();
            WasmId.Local objectParam = f.getParam(0);
            instructions.add(new Instruction.RefTest(Instruction.AnyExternConversion.toAny(objectParam.getter()), jlObjectRef));
            return f;
        }
    }
}
