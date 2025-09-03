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

package com.oracle.svm.hosted.webimage.wasm.snippets;

import java.util.Arrays;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.hosted.webimage.wasm.ast.ImportDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmProviders;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.webimage.wasm.types.WasmValType;
import com.oracle.svm.webimage.wasmgc.WasmExtern;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.vm.ci.meta.JavaType;

/**
 * Foreign call descriptor that maps to an imported Wasm function. Unlike
 * {@link com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor}, it is not
 * associated with any Java method.
 * <p>
 * Use {@link WasmExtern} in argument and return types to denote {@code externref}, both when
 * creating the descriptor and on any {@link jdk.graal.compiler.graph.Node.NodeIntrinsic} methods
 * that target the descriptor.
 * <p>
 * No two instances of this class must exist that share {@link #module} and {@link #name}, but have
 * a different signature. This will result in multiple Wasm imports with the same name but different
 * signatures.
 */
public class WasmImportForeignCallDescriptor extends ForeignCallDescriptor {

    private final String module;
    private final String name;
    private final String comment;

    public WasmImportForeignCallDescriptor(String module, String name, Class<?> resultType, Class<?>[] argumentTypes, String comment) {
        super(module + "." + name, resultType, argumentTypes, CallSideEffect.HAS_SIDE_EFFECT, new LocationIdentity[]{LocationIdentity.any()}, false, true);

        this.module = module;
        this.name = name;
        this.comment = comment;
    }

    public ImportDescriptor.Function getImportDescriptor(WebImageWasmProviders providers) {
        Class<?> returnType = getResultType();
        Class<?>[] argTypes = getArgumentTypes();

        WasmValType wasmReturnType = convertExternType(providers, returnType);
        WasmValType[] wasmArgTypes = Arrays.stream(argTypes).map(argType -> convertExternType(providers, argType)).toArray(WasmValType[]::new);

        TypeUse typeUse = returnType == void.class ? TypeUse.withoutResult(wasmArgTypes) : TypeUse.withResult(wasmReturnType, wasmArgTypes);

        return new ImportDescriptor.Function(module, name, typeUse, comment);
    }

    /**
     * Maps the given class to a {@link WasmValType}. Instances of {@link WasmExtern} are mapped to
     * {@code externref}.
     *
     * @see com.oracle.svm.webimage.wasm.types.WasmUtil#typeForJavaType(JavaType)
     */
    private static WasmValType convertExternType(WebImageWasmProviders providers, Class<?> clazz) {
        WasmValType wasmType;
        if (clazz == WasmExtern.class) {
            wasmType = WasmRefType.EXTERNREF;
        } else {
            wasmType = providers.util().typeForJavaClass(clazz);
        }

        return wasmType;
    }
}
