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
import static org.graalvm.compiler.core.llvm.LLVMIRBuilder.typeOf;

import org.bytedeco.javacpp.LLVM.LLVMBasicBlockRef;
import org.bytedeco.javacpp.LLVM.LLVMTypeRef;
import org.bytedeco.javacpp.LLVM.LLVMValueRef;
import org.bytedeco.javacpp.LLVM;
import org.graalvm.compiler.core.llvm.LLVMGenerator;
import org.graalvm.compiler.core.llvm.LLVMIRBuilder;
import org.graalvm.compiler.core.llvm.LLVMUtils;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMVariable;
import org.graalvm.compiler.core.llvm.NodeLLVMBuilder;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.graal.code.SubstrateDebugInfoBuilder;
import com.oracle.svm.core.graal.code.SubstrateNodeLIRBuilder;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.VMThreads;

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
    }

    @Override
    public void emitCGlobalDataLoadAddress(CGlobalDataLoadAddressNode node) {
        CGlobalDataInfo dataInfo = node.getDataInfo();

        String symbolName = (dataInfo.isSymbolReference()) ? dataInfo.getData().symbolName : "global_" + builder.getFunctionName() + "#" + nextCGlobalId++;
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

    protected LLVMValueRef emitCondition(LogicNode condition) {
        if (condition instanceof SafepointCheckNode) {
            LLVMValueRef threadData = getGenerator().getSpecialRegister(LLVMFeature.THREAD_POINTER_INDEX);
            threadData = gen.getBuilder().buildIntToPtr(threadData, gen.getBuilder().rawPointerType());
            LLVMValueRef safepointCounterAddr = gen.getBuilder().buildGEP(threadData, gen.getBuilder().constantInt(Math.toIntExact(Safepoint.getThreadLocalSafepointRequestedOffset())));
            LLVMValueRef safepointCount = gen.getBuilder().buildAtomicSub(safepointCounterAddr, gen.getBuilder().constantInt(1));
            return gen.getBuilder().buildIsNull(safepointCount);
        }
        return super.emitCondition(condition);
    }

    @Override
    protected LLVMValueRef emitCall(Invoke i, LoweredCallTargetNode callTarget, LLVMValueRef callee, long patchpointId, LLVMValueRef... args) {
        if (!hasJavaFrameAnchor(callTarget)) {
            return super.emitCall(i, callTarget, callee, patchpointId, args);
        }

        LLVMValueRef anchor = llvmOperand(getJavaFrameAnchor(callTarget));
        anchor = builder.buildIntToPtr(anchor, builder.rawPointerType());

        LLVMValueRef lastSPAddr = builder.buildGEP(anchor, builder.constantInt(runtimeConfiguration.getJavaFrameAnchorLastSPOffset()));
        Register stackPointer = gen.getRegisterConfig().getFrameRegister();
        builder.buildStore(builder.buildReadRegister(builder.register(stackPointer.name)), lastSPAddr);

        if (SubstrateOptions.MultiThreaded.getValue()) {
            LLVMValueRef threadLocalArea = getGenerator().getSpecialRegister(LLVMFeature.THREAD_POINTER_INDEX);
            LLVMValueRef statusIndex = builder.constantInt(runtimeConfiguration.getVMThreadStatusOffset());
            LLVMValueRef statusAddress = builder.buildGEP(builder.buildIntToPtr(threadLocalArea, builder.rawPointerType()), statusIndex);
            builder.buildStore(builder.constantInt(VMThreads.StatusSupport.STATUS_IN_NATIVE), statusAddress);
        }

        LLVMValueRef wrapper = createJNIWrapper(callee, args.length, runtimeConfiguration.getJavaFrameAnchorLastIPOffset(), (Block) gen.getCurrentBlock());

        LLVMValueRef[] newArgs = new LLVMValueRef[args.length + 2];
        newArgs[0] = anchor;
        newArgs[1] = callee;
        System.arraycopy(args, 0, newArgs, 2, args.length);
        return super.emitCall(i, callTarget, wrapper, patchpointId, newArgs);
    }

    /*
     * Calling a native function from Java code requires filling the JavaFrameAnchor with the return
     * address of the call. This wrapper allows this by creating an intermediary call frame from
     * which the return address can be accessed. The parameters to this wrapper are the anchor, the
     * native callee, and the arguments to the callee.
     */
    public LLVMValueRef createJNIWrapper(LLVMValueRef callee, int numArgs, int anchorIPOffset, Block currentBlock) {
        LLVMTypeRef calleeType = LLVMIRBuilder.getElementType(typeOf(callee));
        LLVMTypeRef wrapperType = builder.prependArguments(calleeType, builder.rawPointerType(), typeOf(callee));
        LLVMValueRef transitionWrapper = builder.addFunction(LLVMUtils.JNI_WRAPPER_PREFIX + LLVMIRBuilder.intrinsicType(calleeType), wrapperType);
        LLVM.LLVMSetLinkage(transitionWrapper, LLVM.LLVMLinkOnceAnyLinkage);
        builder.setAttribute(transitionWrapper, LLVM.LLVMAttributeFunctionIndex, "gc-leaf-function");
        builder.setAttribute(transitionWrapper, LLVM.LLVMAttributeFunctionIndex, "noinline");

        LLVMBasicBlockRef block = builder.appendBasicBlock("main", transitionWrapper);
        builder.positionAtEnd(block);

        /* Fill in the JavaFrameAnchor */
        LLVMValueRef anchor = LLVMIRBuilder.getParam(transitionWrapper, 0);
        LLVMValueRef lastIPAddr = builder.buildGEP(anchor, builder.constantInt(anchorIPOffset));
        LLVMValueRef callIP = builder.buildReturnAddress(builder.constantInt(0));
        builder.buildStore(callIP, lastIPAddr);

        LLVMValueRef[] args = new LLVMValueRef[numArgs];
        for (int i = 0; i < numArgs; ++i) {
            args[i] = LLVMIRBuilder.getParam(transitionWrapper, i + 2);
        }
        LLVMValueRef target = LLVMIRBuilder.getParam(transitionWrapper, 1);
        LLVMValueRef ret = builder.buildCall(target, args);

        if (LLVMIRBuilder.isVoidType(LLVMIRBuilder.getReturnType(calleeType))) {
            builder.buildRetVoid();
        } else {
            builder.buildRet(ret);
        }

        builder.positionAtEnd(gen.getBlockEnd(currentBlock));
        return transitionWrapper;
    }
}
