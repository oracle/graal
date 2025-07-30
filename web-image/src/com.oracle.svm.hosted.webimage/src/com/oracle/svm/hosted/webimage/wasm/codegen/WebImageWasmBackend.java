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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.codegen.WebImageBackend;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.ReconstructionData;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.ScheduleWithReconstructionResult;
import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.FunctionTypeDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.hosted.webimage.wasm.phases.WebImageWasmLMAddressLowering;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.asm.EntryPointDecorator;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public abstract class WebImageWasmBackend extends WebImageBackend {
    protected WebImageWasmProviders wasmProviders;

    protected WebImageWasmBackend(Providers providers) {
        super(providers);
    }

    public void setWasmProviders(WebImageWasmProviders providers) {
        this.wasmProviders = providers;
    }

    public WebImageWasmProviders getWasmProviders() {
        return wasmProviders;
    }

    @Override
    public WebImageWasmCompilationResult newCompilationResult(CompilationIdentifier compilationIdentifier, String name) {
        return new WebImageWasmCompilationResult(compilationIdentifier, name);
    }

    @Override
    public BasePhase<CoreProviders> newAddressLoweringPhase(CodeCacheProvider codeCache) {
        return new AddressLoweringByNodePhase(new WebImageWasmLMAddressLowering());
    }

    @Override
    public void emitBackEnd(StructuredGraph graph, Object stub, ResolvedJavaMethod installedCodeOwner, CompilationResult compilationResult, CompilationResultBuilderFactory factory,
                    EntryPointDecorator entryPointDecorator, RegisterConfig config, LIRSuites lirSuites) {
        super.emitBackEnd(graph, stub, installedCodeOwner, compilationResult, factory, entryPointDecorator, config, lirSuites);

        WebImageWasmCompilationResult wasmCompilationResult = (WebImageWasmCompilationResult) compilationResult;
        HostedMethod hostedMethod = (HostedMethod) graph.method();

        wasmCompilationResult.setParamTypes(constructParamTypes(wasmProviders, hostedMethod));
        wasmCompilationResult.setReturnType(constructReturnType(hostedMethod));

        Function fun = createFunction(hostedMethod, wasmProviders.idFactory(), wasmCompilationResult);
        wasmCompilationResult.setFunction(fun);

        WasmBlockContext topContext = WasmBlockContext.getTopLevel(fun.getInstructions());

        ScheduleWithReconstructionResult schedule = (ScheduleWithReconstructionResult) graph.getLastSchedule();

        ControlFlowGraph cfg = schedule.getCFG();
        BlockMap<List<Node>> blockToNodeMap = schedule.getBlockToNodesMap();
        ReconstructionData reconstructionData = schedule.reconstructionData();

        WasmCodeGenTool masm = createCodeGenTool(new WebImageWasmVariableAllocation(), wasmCompilationResult, topContext, graph);
        masm.prepareForMethod(graph);
        new WasmIRWalker(masm, cfg, blockToNodeMap, cfg.getNodeToBlock(), reconstructionData).lowerFunction(graph.getDebug());
    }

    public abstract WasmCodeGenTool createCodeGenTool(WebImageWasmVariableAllocation variableAllocation, WebImageWasmCompilationResult compilationResult,
                    WasmBlockContext topContext, StructuredGraph graph);

    /**
     * Creates an empty {@link Function} for the given graph.
     */
    protected Function createFunction(HostedMethod m, WasmIdFactory idFactory, WebImageWasmCompilationResult compilationResult) {
        TypeUse typeUse = signatureToTypeUse(wasmProviders, compilationResult.getParamTypes(), compilationResult.getReturnType());
        return Function.create(idFactory, idFactory.forMethod(m), functionIdForMethod(typeUse), typeUse, m.format("%H.%n(%P)%R"));
    }

    protected WasmId.FuncType functionIdForMethod(TypeUse typeUse) {
        return wasmProviders.idFactory.newFuncType(FunctionTypeDescriptor.createSimple(typeUse));
    }

    public static TypeUse signatureToTypeUse(WebImageWasmProviders wasmProviders, JavaType[] paramTypes, JavaType returnType) {
        List<WasmValType> params = Arrays.stream(paramTypes).map(type -> getAccurateType(wasmProviders, type)).collect(Collectors.toList());

        List<WasmValType> results;
        if (returnType.getJavaKind() == JavaKind.Void) {
            results = Collections.emptyList();
        } else {
            results = Collections.singletonList(getAccurateType(wasmProviders, returnType));
        }

        return new TypeUse(params, results);
    }

    public static TypeUse methodToTypeUse(WebImageWasmProviders wasmProviders, HostedMethod m) {
        return signatureToTypeUse(wasmProviders, constructParamTypes(wasmProviders, m), constructReturnType(m));
    }

    private static WasmValType getAccurateType(WebImageWasmProviders wasmProviders, JavaType type) {
        ResolvedJavaType inputType = type.resolve(null);
        return wasmProviders.util().typeForJavaType(inputType);
    }

    public static ResolvedJavaType[] constructParamTypes(WebImageWasmProviders providers, HostedMethod m) {
        Signature sig = m.getSignature();
        List<ResolvedJavaType> paramTypes = new ArrayList<>(sig.getParameterCount(true));

        if (m.hasReceiver()) {
            paramTypes.add(providers.getReceiverType(m));
        }

        for (int i = 0; i < sig.getParameterCount(false); i++) {
            paramTypes.add(sig.getParameterType(i, null).resolve(null));
        }

        return paramTypes.toArray(ResolvedJavaType[]::new);
    }

    public static ResolvedJavaType constructReturnType(ResolvedJavaMethod m) {
        Signature sig = m.getSignature();
        return sig.getReturnType(null).resolve(null);
    }
}
