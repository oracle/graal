/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.operations.generator;

import java.util.List;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.ChildExecutionResult;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.generator.NodeGeneratorPlugs;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel;

public class OperationNodeGeneratorPlugs implements NodeGeneratorPlugs {

    private final ProcessorContext context;
    private final TypeMirror nodeType;
    private final InstructionModel instr;
    private final boolean isBoxingOperations;

    public OperationNodeGeneratorPlugs(ProcessorContext context, TypeMirror nodeType, InstructionModel instr) {
        this.context = context;
        this.nodeType = nodeType;
        this.instr = instr;

        this.isBoxingOperations = nodeType.toString().endsWith("BoxingOperationsGen");
    }

    public List<? extends VariableElement> additionalArguments() {
        return List.of(
                        new CodeVariableElement(nodeType, "$root"),
                        new CodeVariableElement(context.getType(Object[].class), "$objs"),
                        new CodeVariableElement(context.getType(int.class), "$bci"),
                        new CodeVariableElement(context.getType(int.class), "$sp"));
    }

    public ChildExecutionResult createExecuteChild(FlatNodeGenFactory factory, CodeTreeBuilder builder, FrameState originalFrameState, FrameState frameState, NodeExecutionData execution,
                    LocalVariable targetValue) {

        CodeTreeBuilder b = builder.create();
        CodeTree frame = frameState.get(TemplateMethod.FRAME_NAME).createReference();

        b.string(targetValue.getName(), " = ");

        int index = execution.getIndex();

        boolean th = buildChildExecution(b, frame, index, targetValue.getTypeMirror());

        return new ChildExecutionResult(b.build(), th);
    }

    private boolean buildChildExecution(CodeTreeBuilder b, CodeTree frame, int idx, TypeMirror resultType) {
        int index = idx;

        if (index < instr.signature.valueCount) {

            String slotString = "$sp - " + (instr.signature.valueCount - index);

            boolean canThrow;

            if (instr.signature.valueBoxingElimination[index]) {
                if (ElementUtils.isObject(resultType)) {
                    b.startCall("doPopObject");
                    canThrow = false;
                } else {
                    b.startCall("doPopPrimitive" + ElementUtils.firstLetterUpperCase(resultType.toString()));
                    canThrow = true;
                }

                b.tree(frame);
                b.string("$root");
                b.string(slotString);
                b.string("this.op_childValue" + index + "_boxing_");
                b.string("$objs");
                b.end();

                return canThrow;
            } else {
                TypeMirror targetType = instr.signature.valueTypes[index];
                if (!ElementUtils.isObject(targetType)) {
                    b.cast(targetType);
                }
                b.startCall(frame, "getObject");
                b.string(slotString);
                b.end();
                return false;
            }
        }

        index -= instr.signature.valueCount;

        if (index < instr.signature.localSetterCount) {
            b.string("this.op_localSetter" + index + "_");
            return false;
        }

        index -= instr.signature.localSetterCount;

        if (index < instr.signature.localSetterRangeCount) {
            b.string("this.op_localSetterRange" + index + "_");
            return false;
        }

        throw new AssertionError("index=" + index + ", signature=" + instr.signature);
    }

    public void createNodeChildReferenceForException(FlatNodeGenFactory flatNodeGenFactory, FrameState frameState, CodeTreeBuilder builder, List<CodeTree> values, NodeExecutionData execution,
                    NodeChildData child, LocalVariable var) {
        builder.string("null");
    }

    public CodeTree createTransferToInterpreterAndInvalidate() {
        if (isBoxingOperations) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.statement("$root.transferToInterpreterAndInvalidate()");
            return b.build();
        }
        return NodeGeneratorPlugs.super.createTransferToInterpreterAndInvalidate();
    }
}
