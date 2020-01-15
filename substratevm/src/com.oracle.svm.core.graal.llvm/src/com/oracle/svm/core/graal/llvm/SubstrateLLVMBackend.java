/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.graal.llvm.LLVMOptions.IncludeLLVMDebugInfo;
import static com.oracle.svm.core.util.VMError.unimplemented;

import java.util.Collections;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.llvm.LLVMCompilerBackend;
import org.graalvm.compiler.core.llvm.LLVMGenerationProvider;
import org.graalvm.compiler.core.llvm.LLVMGenerationResult;
import org.graalvm.compiler.core.llvm.LLVMGenerator;
import org.graalvm.compiler.core.llvm.LLVMIRBuilder;
import org.graalvm.compiler.core.llvm.NodeLLVMBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.util.Providers;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMContextRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateLLVMBackend extends SubstrateBackend implements LLVMGenerationProvider {
    public SubstrateLLVMBackend(Providers providers) {
        super(providers);
    }

    @Override
    public Phase newAddressLoweringPhase(CodeCacheProvider codeCache) {
        return new AddressLoweringPhase(new LLVMAddressLowering());
    }

    @Override
    public CompilationResult createJNITrampolineMethod(ResolvedJavaMethod method, CompilationIdentifier identifier,
                    RegisterValue threadArg, int threadIsolateOffset, RegisterValue methodIdArg, int methodObjEntryPointOffset) {

        CompilationResult result = new CompilationResult(identifier);
        LLVMGenerationResult genResult = new LLVMGenerationResult(method);
        LLVMContextRef context = LLVM.LLVMContextCreate();
        SubstrateLLVMGenerator generator = new SubstrateLLVMGenerator(getProviders(), genResult, method, context, 0);
        LLVMIRBuilder builder = generator.getBuilder();

        builder.addMainFunction(generator.getLLVMFunctionType(method, true));
        builder.setAttribute(builder.getMainFunction(), LLVM.LLVMAttributeFunctionIndex, "naked");

        LLVMBasicBlockRef block = builder.appendBasicBlock("main");
        builder.positionAtEnd(block);

        long startPatchpointId = LLVMIRBuilder.nextPatchpointId.getAndIncrement();
        builder.buildStackmap(builder.constantLong(startPatchpointId));

        LLVMValueRef jumpAddressAddress;
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            LLVMValueRef thread = builder.buildInlineGetRegister(threadArg.getRegister().name);
            LLVMValueRef heapBaseAddress = builder.buildGEP(builder.buildIntToPtr(thread, builder.rawPointerType()), builder.constantInt(threadIsolateOffset));
            LLVMValueRef heapBase = builder.buildLoad(heapBaseAddress, builder.rawPointerType());
            LLVMValueRef methodId = builder.buildInlineGetRegister(methodIdArg.getRegister().name);
            LLVMValueRef methodBase = builder.buildGEP(builder.buildIntToPtr(heapBase, builder.rawPointerType()), builder.buildPtrToInt(methodId, builder.longType()));
            jumpAddressAddress = builder.buildGEP(methodBase, builder.constantInt(methodObjEntryPointOffset));
        } else {
            LLVMValueRef methodBase = builder.buildInlineGetRegister(methodIdArg.getRegister().name);
            jumpAddressAddress = builder.buildGEP(builder.buildIntToPtr(methodBase, builder.rawPointerType()), builder.constantInt(methodObjEntryPointOffset));
        }
        LLVMValueRef jumpAddress = builder.buildLoad(jumpAddressAddress, builder.rawPointerType());
        builder.buildInlineJump(jumpAddress);
        builder.buildUnreachable();

        genResult.setBitcode(generator.getBuilder().getBitcode());

        byte[] bitcode = genResult.getBitcode();
        result.setTargetCode(bitcode, bitcode.length);
        result.setMethods(method, Collections.emptySet());
        result.recordInfopoint(NumUtil.safeToInt(startPatchpointId), null, InfopointReason.METHOD_START);
        return result;
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult, boolean isDefault, OptionValues options) {
        throw unimplemented();
    }

    @Override
    public void emitBackEnd(StructuredGraph graph, Object stub, ResolvedJavaMethod installedCodeOwner, CompilationResult compilationResult, CompilationResultBuilderFactory factory,
                    RegisterConfig config, LIRSuites lirSuites) {
        LLVMCompilerBackend.emitBackEnd(this, graph, compilationResult);
    }

    @Override
    public LLVMGenerator newLLVMGenerator(LLVMGenerationResult result) {
        LLVMContextRef context = LLVM.LLVMContextCreate();
        return new SubstrateLLVMGenerator(getProviders(), result, result.getMethod(), context, IncludeLLVMDebugInfo.getValue());
    }

    @Override
    public NodeLLVMBuilder newNodeLLVMBuilder(StructuredGraph graph, LLVMGenerator generator) {
        return new SubstrateNodeLLVMBuilder(graph, generator, getRuntimeConfiguration());
    }

    @Override
    public LLVMGenerationResult newLLVMGenerationResult(ResolvedJavaMethod method) {
        return new SubstrateLLVMGenerationResult(method);
    }
}
