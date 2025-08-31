/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm;

import static com.oracle.svm.core.util.VMError.intentionallyUnimplemented;

import java.util.Collections;
import java.util.List;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.asm.EntryPointDecorator;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;
import jdk.graal.compiler.phases.util.Providers;

import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.llvm.lowering.LLVMAddressLowering;
import com.oracle.svm.core.graal.llvm.util.LLVMOptions;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

public class SubstrateLLVMBackend extends SubstrateBackend {
    private static final TimerKey EmitLLVM = DebugContext.timer("EmitLLVM").doc("Time spent generating LLVM from HIR.");
    private static final TimerKey BackEnd = DebugContext.timer("BackEnd").doc("Time spent in EmitLLVM and Populate.");

    public SubstrateLLVMBackend(Providers providers) {
        super(providers);
    }

    @Override
    public BasePhase<CoreProviders> newAddressLoweringPhase(CodeCacheProvider codeCache) {
        return new AddressLoweringByNodePhase(new LLVMAddressLowering());
    }

    @Override
    public CompilationResult createJNITrampolineMethod(ResolvedJavaMethod method, CompilationIdentifier identifier,
                    RegisterValue threadArg, int threadIsolateOffset, RegisterValue methodIdArg, int methodObjEntryPointOffset) {

        CompilationResult result = new CompilationResult(identifier);
        result.setMethods(method, Collections.emptySet());

        LLVMGenerator generator = new LLVMGenerator(getProviders(), result, null, method, 0);
        generator.createJNITrampoline(threadArg, threadIsolateOffset, methodIdArg, methodObjEntryPointOffset);
        byte[] bitcode = generator.getBitcode();
        result.setTargetCode(bitcode, bitcode.length);

        return result;
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult, boolean isDefault, OptionValues options) {
        throw intentionallyUnimplemented();
    }

    @Override
    @SuppressWarnings("try")
    public void emitBackEnd(StructuredGraph graph, Object stub, ResolvedJavaMethod installedCodeOwner, CompilationResult result, CompilationResultBuilderFactory factory,
                    EntryPointDecorator entryPointDecorator, RegisterConfig config, LIRSuites lirSuites) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("BackEnd", graph.getLastSchedule()); DebugCloseable a = BackEnd.start(debug)) {
            emitLLVM(graph, result);
            dumpDebugInfo(result, graph);
        } catch (Throwable e) {
            throw debug.handle(e);
        } finally {
            graph.checkCancellation();
        }
    }

    @SuppressWarnings("try")
    private void emitLLVM(StructuredGraph graph, CompilationResult result) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope ds = debug.scope("EmitLLVM"); DebugCloseable a = EmitLLVM.start(debug)) {
            assert graph.isAfterStage(StageFlag.VALUE_PROXY_REMOVAL);

            ResolvedJavaMethod method = graph.method();
            LLVMGenerator generator = new LLVMGenerator(getProviders(), result, graph, method, LLVMOptions.IncludeLLVMDebugInfo.getValue());
            NodeLLVMBuilder nodeBuilder = newNodeLLVMBuilder(graph, generator);

            /* LLVM generation */
            generate(nodeBuilder, graph);
            byte[] bitcode = generator.getBitcode();
            result.setTargetCode(bitcode, bitcode.length);

            try (DebugContext.Scope s = debug.scope("LIRStages", nodeBuilder, null, null)) {
                /* Dump LIR along with HIR (the LIR is looked up from context) */
                debug.dump(DebugContext.BASIC_LEVEL, graph.getLastSchedule(), "After LIR generation");
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        } finally {
            graph.checkCancellation();
        }
    }

    protected NodeLLVMBuilder newNodeLLVMBuilder(StructuredGraph graph, LLVMGenerator generator) {
        return new NodeLLVMBuilder(graph, generator);
    }

    private static void generate(NodeLLVMBuilder nodeBuilder, StructuredGraph graph) {
        StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        for (HIRBlock b : schedule.getCFG().getBlocks()) {
            nodeBuilder.doBlock(b, graph, schedule.getBlockToNodesMap());
        }
        nodeBuilder.finish();
    }

    private static void dumpDebugInfo(CompilationResult compilationResult, StructuredGraph graph) {
        DebugContext debug = graph.getDebug();

        if (debug.areCountersEnabled()) {
            List<DataPatch> ldp = compilationResult.getDataPatches();
            JavaKind[] kindValues = JavaKind.values();
            CounterKey[] dms = new CounterKey[kindValues.length];
            for (int i = 0; i < dms.length; i++) {
                dms[i] = DebugContext.counter("DataPatches-%s", kindValues[i]);
            }

            for (DataPatch dp : ldp) {
                JavaKind kind = JavaKind.Illegal;
                if (dp.reference instanceof ConstantReference) {
                    VMConstant constant = ((ConstantReference) dp.reference).getConstant();
                    if (constant instanceof JavaConstant) {
                        kind = ((JavaConstant) constant).getJavaKind();
                    }
                }
                dms[kind.ordinal()].add(debug, 1);
            }

            DebugContext.counter("CompilationResults").increment(debug);
            DebugContext.counter("InfopointsEmitted").add(debug, compilationResult.getInfopoints().size());
            DebugContext.counter("DataPatches").add(debug, ldp.size());
        }

        debug.dump(DebugContext.BASIC_LEVEL, compilationResult, "After code generation");
    }
}
