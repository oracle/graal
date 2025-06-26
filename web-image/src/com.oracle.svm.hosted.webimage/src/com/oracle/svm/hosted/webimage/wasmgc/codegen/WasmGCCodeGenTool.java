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
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmBlockContext;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmCodeGenTool;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmCompilationResult;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmProviders;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.GCKnownIds;
import com.oracle.svm.webimage.hightiercodegen.variables.VariableAllocation;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.InvokeTarget;

public class WasmGCCodeGenTool extends WasmCodeGenTool {
    protected WasmGCCodeGenTool(CoreProviders provider, VariableAllocation variableAllocation, WebImageWasmCompilationResult compilationResult, WebImageWasmProviders wasmProviders,
                    WasmBlockContext topContext, StructuredGraph graph) {
        super(provider, variableAllocation, compilationResult, wasmProviders, topContext, graph);
    }

    @Override
    public WebImageWasmGCProviders getWasmProviders() {
        return (WebImageWasmGCProviders) super.getWasmProviders();
    }

    @Override
    public void lowerCatchPreamble() {
    }

    @Override
    public void finish() {
        super.finish();
        compilationResult.setTargetCode(new byte[0], 0);
    }

    @Override
    public GCKnownIds getKnownIds() {
        return (GCKnownIds) super.getKnownIds();
    }

    @Override
    public Instruction.AbstractCall getCall(InvokeTarget target, boolean direct, Instruction.AbstractCall callInstruction) {
        // The WasmGC backend does not track call site locations
        recordCall(0, 0, target, direct);
        return callInstruction;
    }
}
