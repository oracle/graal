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

package com.oracle.svm.hosted.webimage.wasm.ast.visitors;

import org.graalvm.collections.EconomicSet;

import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.Import;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.BreakTable;
import com.oracle.svm.hosted.webimage.wasm.ast.Tag;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId.FuncType;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds;
import com.oracle.svm.hosted.webimage.wasmgc.ast.RecursiveGroup;

/**
 * Adds missing elements to the WASM module.
 * <p>
 * This includes:
 * <ul>
 * <li>Local and global variable declarations.</li>
 * <li>Imports referenced in instructions</li>
 * <li>Replace {@code null} targets in {@link BreakTable} with the default target.</li>
 * <li>Create type definitions for {@link FuncType}</li>
 * </ul>
 * <p>
 * Adding these elements after the module is generated allows us to only include what is needed in
 * the final module.
 */
public class WasmElementCreator extends WasmVisitor {

    protected Function currentFunction = null;
    /**
     * Local variables referenced in the current function.
     */
    protected EconomicSet<WasmId.Local> currentLocals;

    /**
     * Set of all processed function types.
     */
    protected EconomicSet<WebImageWasmIds.DescriptorFuncType> funcTypes = EconomicSet.create();

    private void prepareForFunction(Function f) {
        currentFunction = f;
        currentLocals = EconomicSet.create();
    }

    private void postProcessFunction() {
        currentLocals.forEach(currentFunction::allocateVariable);
        currentFunction = null;
        currentLocals = null;
    }

    private void registerLocal(WasmId.Local id) {
        if (id instanceof WebImageWasmIds.Param) {
            // Parameters do not have to be collected.
            return;
        }

        if (!currentLocals.contains(id)) {
            currentLocals.add(id);
        }
    }

    private void registerImport(WasmId.Import<?> id) {
        if (!module.getImports().containsKey(id)) {
            module.addImport(new Import(id));
        }
    }

    private void registerTag(WasmId.Tag id) {
        if (module.getTags().stream().map(t -> t.id).noneMatch(tagId -> tagId == id)) {
            module.addTag(new Tag(id, null));
        }
    }

    @Override
    protected void visitId(WasmId id) {
        if (id == null) {
            return;
        }

        switch (id) {
            case WasmId.Type t -> registerType(t);
            case WasmId.Import<?> i -> registerImport(i);
            case WasmId.Local l -> registerLocal(l);
            case WasmId.Tag t -> registerTag(t);
            default -> {
                // Other types don't need to be handled
            }
        }

        super.visitId(id);
    }

    protected void registerType(WasmId.Type id) {
        if (id instanceof WebImageWasmIds.DescriptorFuncType funcType) {
            registerFunctionType(funcType);
        }
    }

    protected void registerFunctionType(WebImageWasmIds.DescriptorFuncType funcType) {
        if (funcTypes.add(funcType)) {
            registerNewFunctionType(funcType);
        }
    }

    protected void registerNewFunctionType(WebImageWasmIds.DescriptorFuncType funcType) {
        module.addRecursiveGroup(RecursiveGroup.singletonGroup(funcType.createTypeDefinition(null)));
    }

    @Override
    public void visitFunction(Function f) {
        prepareForFunction(f);
        super.visitFunction(f);
        postProcessFunction();
    }

    @Override
    public void visitBreakTable(BreakTable inst) {
        super.visitBreakTable(inst);
        inst.fillTargets();
    }
}
