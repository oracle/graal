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

package com.oracle.svm.hosted.webimage.wasm.ast;

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasmgc.ast.FunctionType;

/**
 * Holds all information necessary to declare a WasmGC function type.
 * <p>
 * This descriptor is used to create
 * {@link com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds.DescriptorFuncType} ids, from
 * which full {@link FunctionType} definitions can be generated.
 * <p>
 * See {@link FunctionType} and {@link com.oracle.svm.hosted.webimage.wasmgc.ast.TypeDefinition} for
 * a description of the fields in this class.
 */
public record FunctionTypeDescriptor(WasmId.FuncType supertype, boolean isFinal, TypeUse typeUse) {
    public FunctionType toTypeDefinition(WasmId.FuncType id, Object comment) {
        return new FunctionType(id, supertype, isFinal, typeUse, comment);
    }

    public static FunctionTypeDescriptor createSimple(TypeUse typeUse) {
        return new FunctionTypeDescriptor(null, true, typeUse);
    }
}
