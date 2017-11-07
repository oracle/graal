/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.LLVMLivenessAnalysis.LLVMLivenessAnalysisResult;
import com.oracle.truffle.llvm.parser.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute.Kind;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute.KnownAttribute;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMException;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LazyToTruffleConverter;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

public class LazyToTruffleConverterImpl implements LazyToTruffleConverter {
    private final LLVMParserRuntime runtime;
    private final LLVMContext context;
    private final NodeFactory nodeFactory;
    private final FunctionDefinition method;
    private final Source source;
    private final FrameDescriptor frame;
    private final Map<InstructionBlock, List<Phi>> phis;
    private final Map<String, Integer> labels;

    LazyToTruffleConverterImpl(LLVMParserRuntime runtime, LLVMContext context, NodeFactory nodeFactory, FunctionDefinition method, Source source, FrameDescriptor frame,
                    Map<InstructionBlock, List<Phi>> phis,
                    Map<String, Integer> labels) {
        this.runtime = runtime;
        this.context = context;
        this.nodeFactory = nodeFactory;
        this.method = method;
        this.source = source;
        this.frame = frame;
        this.phis = phis;
        this.labels = labels;
    }

    @Override
    public RootCallTarget convert() {
        CompilerAsserts.neverPartOfCompilation();

        LLVMLivenessAnalysisResult liveness = LLVMLivenessAnalysis.computeLiveness(frame, context, phis, method);
        LLVMBitcodeFunctionVisitor visitor = new LLVMBitcodeFunctionVisitor(runtime, frame, labels, phis, nodeFactory, method.getParameters().size(),
                        new LLVMSymbolReadResolver(runtime, method, frame, labels), method, liveness);
        method.accept(visitor);
        FrameSlot[][] nullableBeforeBlock = getNullableFrameSlots(liveness.getNullableBeforeBlock());
        FrameSlot[][] nullableAfterBlock = getNullableFrameSlots(liveness.getNullableAfterBlock());
        LLVMExpressionNode body = nodeFactory.createFunctionBlockNode(runtime, frame.findFrameSlot(LLVMException.FRAME_SLOT_ID), visitor.getBlocks(), nullableBeforeBlock, nullableAfterBlock);

        List<LLVMExpressionNode> copyArgumentsToFrame = copyArgumentsToFrame();
        LLVMExpressionNode[] copyArgumentsToFrameArray = copyArgumentsToFrame.toArray(new LLVMExpressionNode[copyArgumentsToFrame.size()]);
        SourceSection sourceSection = method.getSourceSection();
        RootNode rootNode = nodeFactory.createFunctionStartNode(runtime, body, copyArgumentsToFrameArray, sourceSection, frame, method, source);

        final LLVMSourceLocation sourceScope = method.getLexicalScope();
        if (sourceScope != null) {
            context.getSourceContext().registerSourceScope(rootNode.getName(), sourceScope);
        }

        return Truffle.getRuntime().createCallTarget(rootNode);
    }

    private FrameSlot[][] getNullableFrameSlots(BitSet[] nullableBeforeBlock) {
        List<? extends FrameSlot> frameSlots = frame.getSlots();
        FrameSlot[][] result = new FrameSlot[nullableBeforeBlock.length][];
        for (int i = 0; i < nullableBeforeBlock.length; i++) {
            BitSet nullable = nullableBeforeBlock[i];
            int bitIndex = -1;

            ArrayList<FrameSlot> nullableBefore = new ArrayList<>();
            while ((bitIndex = nullable.nextSetBit(bitIndex + 1)) >= 0) {
                FrameSlot frameSlot = frameSlots.get(bitIndex);
                nullableBefore.add(frameSlot);
            }
            result[i] = nullableBefore.toArray(new FrameSlot[0]);
        }
        return result;
    }

    private List<LLVMExpressionNode> copyArgumentsToFrame() {
        List<FunctionParameter> parameters = method.getParameters();
        List<LLVMExpressionNode> formalParamInits = new ArrayList<>();
        LLVMExpressionNode stackPointerNode = nodeFactory.createFunctionArgNode(0, PrimitiveType.I64);
        formalParamInits.add(nodeFactory.createFrameWrite(runtime, PrimitiveType.I64, stackPointerNode, frame.findFrameSlot(LLVMStack.FRAME_ID), null));

        int argIndex = 1;
        if (method.getType().getReturnType() instanceof StructureType) {
            argIndex++;
        }
        for (FunctionParameter parameter : parameters) {
            LLVMExpressionNode parameterNode = nodeFactory.createFunctionArgNode(argIndex++, parameter.getType());
            FrameSlot slot = frame.findFrameSlot(parameter.getName());
            if (isStructByValue(parameter)) {
                Type type = ((PointerType) parameter.getType()).getPointeeType();
                int size = runtime.getContext().getByteSize(type);
                int alignment = runtime.getContext().getByteAlignment(type);
                formalParamInits.add(nodeFactory.createFrameWrite(runtime, parameter.getType(), nodeFactory.createCopyStructByValue(runtime, type, size, alignment, parameterNode), slot, null));
            } else {
                formalParamInits.add(nodeFactory.createFrameWrite(runtime, parameter.getType(), parameterNode, slot, null));
            }
        }
        return formalParamInits;
    }

    private static boolean isStructByValue(FunctionParameter parameter) {
        if (parameter.getType() instanceof PointerType && parameter.getParameterAttribute() != null) {
            for (Attribute a : parameter.getParameterAttribute().getAttributes()) {
                if (a instanceof KnownAttribute && ((KnownAttribute) a).getAttr() == Kind.BYVAL) {
                    return true;
                }
            }
        }
        return false;
    }
}
