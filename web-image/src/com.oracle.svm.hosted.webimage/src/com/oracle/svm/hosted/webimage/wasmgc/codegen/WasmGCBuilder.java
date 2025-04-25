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

import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instructions;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.GCKnownIds;
import com.oracle.svm.webimage.wasm.types.WasmUtil;

import jdk.vm.ci.meta.JavaKind;

/**
 * Provides methods to create common instruction patterns found in the WasmGC backend.
 * <p>
 * This class must be immutable because it may be concurrently accessed.
 */
public class WasmGCBuilder {

    private final WebImageWasmGCProviders providers;
    private final WasmIdFactory idFactory;
    private final GCKnownIds knownIds;

    public WasmGCBuilder(WebImageWasmGCProviders providers) {
        this.providers = providers;
        this.idFactory = providers.idFactory();
        this.knownIds = providers.knownIds();
    }

    /**
     * Create an instruction to check if the given value is an array struct (and is non-null).
     *
     * @param componentKind Check for an array struct with this component kind. If null, this checks
     *            if this just checks for the base array struct.
     */
    public Instruction isArrayStruct(Instruction ref, JavaKind componentKind) {
        return new Instruction.RefTest(ref, knownIds.getArrayStructType(componentKind).asNonNull());
    }

    /**
     * Creates instructions to read the inner array of the given array struct.
     *
     * @param componentKind The array component kind which is used to look up the array struct type.
     *            If null, the base array struct is used and the instruction will produce a generic
     *            array ({@code arrayref}).
     */
    public Instruction getInnerArray(Instruction arrayStruct, JavaKind componentKind) {
        return new Instruction.StructGet(knownIds.getArrayStructType(componentKind), knownIds.innerArrayField, WasmUtil.Extension.None, arrayStruct);
    }

    /**
     * Reads the length of the given array struct.
     */
    public Instruction getArrayLength(Instruction arrayStruct) {
        return new Instruction.ArrayLen(getInnerArray(arrayStruct, null));
    }

    /**
     * Reads an array element from the given array struct.
     */
    public Instruction getArrayElement(Instruction arrayStruct, Instruction index, JavaKind componentKind) {
        WasmId.ArrayType innerArray = idFactory.newJavaInnerArray(componentKind);
        return new Instruction.ArrayGet(innerArray, WasmUtil.Extension.forKind(componentKind), getInnerArray(arrayStruct, componentKind), index);
    }

    /**
     * Sets an array element in the given array struct to the given value.
     */
    public Instruction setArrayElement(Instruction arrayStruct, Instruction index, Instruction value, JavaKind componentKind) {
        WasmId.ArrayType innerArray = idFactory.newJavaInnerArray(componentKind);
        return new Instruction.ArraySet(innerArray, getInnerArray(arrayStruct, componentKind), index, value);
    }

    /**
     * Reads {@link com.oracle.svm.core.hub.DynamicHub} from object.
     */
    public Instruction getHub(Instruction objectStruct) {
        WasmId.StructType baseObjectId = providers.util().getJavaLangObjectId();
        return new Instruction.StructGet(baseObjectId, knownIds.hubField, WasmUtil.Extension.None, objectStruct);
    }

    /**
     * Creates a new, default initialized, Java array struct.
     */
    public Instruction createNewArray(JavaKind componentKind, Instruction hub, Instruction length) {
        Instruction innerArray = new Instruction.ArrayNew(providers.knownIds().innerArrayTypes.get(componentKind), length);

        return new Instruction.StructNew(providers.knownIds().arrayStructTypes.get(componentKind), hub,
                        Instruction.Const.forInt(0), innerArray);
    }

    /**
     * Creates a new uninitialized instance by calling the function reference in
     * {@link GCKnownIds#newInstanceField}.
     */
    public Instruction createUninitialized(Instruction hub) {
        return new Instruction.CallRef(knownIds.newInstanceFieldType,
                        new Instruction.StructGet(providers.util().getHubObjectId(), knownIds.newInstanceField, WasmUtil.Extension.None, hub),
                        new Instructions());
    }
}
