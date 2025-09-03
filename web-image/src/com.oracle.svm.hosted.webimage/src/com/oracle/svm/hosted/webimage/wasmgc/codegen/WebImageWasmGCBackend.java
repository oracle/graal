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

import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmBlockContext;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmCodeGenTool;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmBackend;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmCompilationResult;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmVariableAllocation;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.util.Providers;

public class WebImageWasmGCBackend extends WebImageWasmBackend {

    public WebImageWasmGCBackend(Providers providers) {
        super(providers);
    }

    @Override
    public WasmCodeGenTool createCodeGenTool(WebImageWasmVariableAllocation variableAllocation, WebImageWasmCompilationResult compilationResult, WasmBlockContext topContext,
                    StructuredGraph graph) {
        return new WasmGCCodeGenTool(getProviders(), variableAllocation, compilationResult, wasmProviders, topContext, graph);
    }

    @Override
    protected WasmId.FuncType functionIdForMethod(TypeUse typeUse) {
        return ((WebImageWasmGCProviders) getWasmProviders()).util().functionIdForMethod(typeUse);
    }
}
