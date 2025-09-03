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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.webimage.wasm.types.WasmValType;

/**
 * Represents a WASM function definition.
 * <p>
 * Ref: https://webassembly.github.io/spec/core/text/modules.html#functions
 */
public final class Function extends ModuleField {

    private final WasmId.Func id;
    public final List<WasmId.Local> params;

    private final List<WasmId.Local> locals = new ArrayList<>();
    private final Instructions instructions = new Instructions();

    private final WasmId.FuncType funcType;
    private final TypeUse signature;

    public Function(WasmId.Func id, List<WasmId.Local> params, WasmId.FuncType funcType, TypeUse signature, Object comment) {
        super(comment);
        this.id = id;
        this.params = Collections.unmodifiableList(params);
        this.funcType = funcType;
        this.signature = signature;

        assert this.signature.params.size() == params.size() : this.signature.params.size() + " != " + params.size();
    }

    public static Function create(WasmIdFactory idFactory, WasmId.Func id, WasmId.FuncType funcType, TypeUse signature, Object comment) {
        List<WasmId.Local> params = new ArrayList<>(signature.params.size());
        for (int i = 0; i < signature.params.size(); i++) {
            WasmValType paramType = signature.params.get(i);
            assert paramType != null;
            params.add(idFactory.newParam(i, paramType));
        }

        return new Function(id, params, funcType, signature, comment);
    }

    /**
     * Create function with a simple function type (final, no supertype).
     */
    public static Function createSimple(WasmIdFactory idFactory, WasmId.Func id, TypeUse signature, Object comment) {
        return create(idFactory, id, idFactory.newFuncType(FunctionTypeDescriptor.createSimple(signature)), signature, comment);
    }

    public WasmId.Func getId() {
        return id;
    }

    public WasmId.FuncType getFuncType() {
        return funcType;
    }

    public TypeUse getSignature() {
        return signature;
    }

    public void allocateVariable(WasmId.Local varId) {
        locals.add(varId);
    }

    public List<WasmId.Local> getLocals() {
        return Collections.unmodifiableList(locals);
    }

    public WasmId.Local getParam(int idx) {
        return params.get(idx);
    }

    public List<WasmId.Local> getParams() {
        return params;
    }

    public List<WasmValType> getResults() {
        return getSignature().results;
    }

    /**
     * Returns the list of instructions, possibly for modification.
     */
    public Instructions getInstructions() {
        return instructions;
    }
}
