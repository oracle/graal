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

import static com.oracle.svm.core.graal.code.SubstrateBackend.getJavaFrameAnchor;
import static com.oracle.svm.core.graal.code.SubstrateBackend.hasJavaFrameAnchor;

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.llvm.LLVMGenerator;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMVariable;
import org.graalvm.compiler.core.llvm.NodeLLVMBuilder;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.code.SubstrateDebugInfoBuilder;
import com.oracle.svm.core.graal.code.SubstrateNodeLIRBuilder;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMTypeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateNodeLLVMBuilder extends NodeLLVMBuilder implements SubstrateNodeLIRBuilder {
    private long nextCGlobalId = 0L;
    private final RuntimeConfiguration runtimeConfiguration;

    protected SubstrateNodeLLVMBuilder(StructuredGraph graph, LLVMGenerator gen, RuntimeConfiguration runtimeConfiguration) {
        super(graph, gen, SubstrateDebugInfoBuilder::new);
        this.runtimeConfiguration = runtimeConfiguration;

        gen.getBuilder().setPersonalityFunction(gen.getFunction(LLVMFeature.getPersonalityStub()));

        for (String alias : getGenerator().getAliases()) {
            builder.addAlias(alias);
        }
    }

    @Override
    public void emitCGlobalDataLoadAddress(CGlobalDataLoadAddressNode node) {
        CGlobalDataInfo dataInfo = node.getDataInfo();

        String symbolName = (dataInfo.getData().symbolName != null) ? dataInfo.getData().symbolName : "global_" + builder.getFunctionName() + "#" + nextCGlobalId++;
        ((SubstrateLLVMGenerationResult) gen.getLLVMResult()).recordCGlobal(new CGlobalDataReference(dataInfo), symbolName);

        setResult(node, builder.buildPtrToInt(builder.getExternalSymbol(symbolName), builder.longType()));
    }

    @Override
    public Variable emitReadReturnAddress() {
        LLVMValueRef returnAddress = getLIRGeneratorTool().getBuilder().buildReturnAddress(getLIRGeneratorTool().getBuilder().constantInt(0));
        return new LLVMVariable(returnAddress);
    }

    private SubstrateLLVMGenerator getGenerator() {
        return (SubstrateLLVMGenerator) gen;
    }

    @Override
    protected int getParamIndex(int index) {
        int offset = (getGenerator().isEntryPoint() ? 0 : LLVMFeature.SPECIAL_REGISTER_COUNT);
        return offset + index;
    }

    @Override
    protected LLVMValueRef[] getCallArguments(NodeInputList<ValueNode> arguments, CallingConvention.Type callType, ResolvedJavaMethod targetMethod) {
        LLVMValueRef[] args = super.getCallArguments(arguments, callType, targetMethod);
        return gen.getCallArguments(args, callType, targetMethod);
    }

    @Override
    protected LLVMTypeRef[] getUnknownCallArgumentTypes(NodeInputList<ValueNode> arguments, LoweredCallTargetNode callTarget) {
        LLVMTypeRef[] types = super.getUnknownCallArgumentTypes(arguments, callTarget);
        return gen.getUnknownCallArgumentTypes(types, callTarget.callType());
    }

    @Override
    protected LLVMValueRef emitCondition(LogicNode condition) {
        if (condition instanceof SafepointCheckNode) {
            LLVMValueRef threadData = getGenerator().getSpecialRegister(LLVMFeature.THREAD_POINTER_INDEX);
            threadData = builder.buildIntToPtr(threadData, builder.rawPointerType());
            LLVMValueRef safepointCounterAddr = builder.buildGEP(threadData, builder.constantInt(Math.toIntExact(Safepoint.getThreadLocalSafepointRequestedOffset())));
            LLVMValueRef safepointCount = builder.buildLoad(safepointCounterAddr, builder.intType());
            safepointCount = builder.buildSub(safepointCount, builder.constantInt(1));
            if (ThreadingSupportImpl.isRecurringCallbackSupported()) {
                builder.buildStore(safepointCount, safepointCounterAddr);
            }
            return builder.buildICmp(Condition.LE, safepointCount, builder.constantInt(0));
        }
        return super.emitCondition(condition);
    }

    @Override
    protected LLVMValueRef emitCall(Invoke invoke, LoweredCallTargetNode callTarget, LLVMValueRef callee, long patchpointId, LLVMValueRef... args) {
        if (!hasJavaFrameAnchor(callTarget)) {
            assert SubstrateBackend.getNewThreadStatus(callTarget) == StatusSupport.STATUS_ILLEGAL;
            return super.emitCall(invoke, callTarget, callee, patchpointId, args);
        }
        assert StatusSupport.isValidStatus(SubstrateBackend.getNewThreadStatus(callTarget));

        LLVMValueRef anchor = llvmOperand(getJavaFrameAnchor(callTarget));
        anchor = builder.buildIntToPtr(anchor, builder.rawPointerType());

        LLVMValueRef lastSPAddr = builder.buildGEP(anchor, builder.constantInt(runtimeConfiguration.getJavaFrameAnchorLastSPOffset()));
        Register stackPointer = gen.getRegisterConfig().getFrameRegister();
        builder.buildStore(builder.buildReadRegister(builder.register(stackPointer.name)), lastSPAddr);

        if (SubstrateOptions.MultiThreaded.getValue()) {
            LLVMValueRef threadLocalArea = getGenerator().getSpecialRegister(LLVMFeature.THREAD_POINTER_INDEX);
            LLVMValueRef statusIndex = builder.constantInt(runtimeConfiguration.getVMThreadStatusOffset());
            LLVMValueRef statusAddress = builder.buildGEP(builder.buildIntToPtr(threadLocalArea, builder.rawPointerType()), statusIndex);
            builder.buildStore(builder.constantInt(SubstrateBackend.getNewThreadStatus(callTarget)), statusAddress);
        }

        LLVMValueRef wrapper = builder.createJNIWrapper(callee, patchpointId, args.length, runtimeConfiguration.getJavaFrameAnchorLastIPOffset());

        LLVMValueRef[] newArgs = new LLVMValueRef[args.length + 2];
        newArgs[0] = anchor;
        newArgs[1] = callee;
        System.arraycopy(args, 0, newArgs, 2, args.length);
        return super.emitCall(invoke, callTarget, wrapper, patchpointId, newArgs);
    }

    @Override
    public void emitReadExceptionObject(ValueNode node) {
        super.emitReadExceptionObject(node);

        LLVMValueRef retrieveExceptionFunction = gen.getRetrieveExceptionFunction();
        LLVMValueRef[] arguments = gen.getCallArguments(new LLVMValueRef[0], SubstrateCallingConventionType.JavaCall, null);
        LLVMValueRef exception = builder.buildCall(retrieveExceptionFunction, arguments);
        setResult(node, exception);
    }
}
