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
package com.oracle.truffle.dsl.processor.operations.instructions;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.ExecutableElement;

import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.operations.OperationsBytecodeNodeGeneratorPlugs;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.ParameterKind;

public class CustomInstruction extends Instruction {

    private final SingleOperationData data;
    protected ExecutableElement executeMethod;
    private OperationsBytecodeNodeGeneratorPlugs plugs;
    private CodeExecutableElement prepareAOTMethod;
    private CodeExecutableElement getSpecializationBits;
    private final List<QuickenedInstruction> quickenedVariants = new ArrayList<>();
    private CodeTree boxingEliminationBitOffset;
    private int boxingEliminationBitMask;

    public SingleOperationData getData() {
        return data;
    }

    public String getUniqueName() {
        return data.getName();
    }

    public List<String> getSpecializationNames() {
        List<String> result = new ArrayList<>();
        for (SpecializationData spec : data.getNodeData().getSpecializations()) {
            result.add(spec.getId());
        }
        return result;
    }

    public void setExecuteMethod(ExecutableElement executeMethod) {
        this.executeMethod = executeMethod;
    }

    public CustomInstruction(String name, int id, SingleOperationData data) {
        super(name, id, data.getMainProperties().returnsValue ? 1 : 0);
        this.data = data;
        initializePops();
    }

    public static final String MARKER_LOCAL_REFS = "LocalSetterRange";
    public static final String MARKER_LOCAL_REF_PREFIX = "LocalSetter_";

    protected void initializePops() {
        MethodProperties props = data.getMainProperties();

        if (props.isVariadic) {
            setVariadic();
            for (int i = 0; i < props.numStackValues - 1; i++) {
                addPopSimple("arg" + i);
            }
        } else {
            for (int i = 0; i < props.numStackValues; i++) {
                addPopIndexed("arg" + i);
            }
        }

        if (props.numLocalReferences == -1) {
            addConstant(MARKER_LOCAL_REFS, new CodeTypeMirror.ArrayCodeTypeMirror(types.OperationLocal));
        } else {
            for (int i = 0; i < props.numLocalReferences; i++) {
                addConstant(MARKER_LOCAL_REF_PREFIX + i, types.OperationLocal);
            }
        }
    }

    protected CustomInstruction(String name, int id, SingleOperationData data, int pushCount) {
        super(name, id, pushCount);
        this.data = data;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        createTracerCode(vars, b);

        if (data.getMainProperties().isVariadic) {

            b.declaration("int", "numVariadics", createVariadicIndex(vars));

            int additionalInputs = data.getMainProperties().numStackValues - 1;

            int inputIndex = 0;
            CodeTree[] inputTrees = new CodeTree[data.getMainProperties().parameters.size()];
            for (ParameterKind kind : data.getMainProperties().parameters) {
                String inputName = "input_" + inputIndex;
                switch (kind) {
                    case STACK_VALUE:
                        b.declaration("Object", inputName, "$frame.getObject($sp - numVariadics - " + (additionalInputs + inputIndex) + ")");
                        inputTrees[inputIndex++] = CodeTreeBuilder.singleString(inputName);
                        break;
                    case VARIADIC:
                        b.declaration("Object[]", inputName, "new Object[numVariadics]");
                        b.startFor().string("int varIndex = 0; varIndex < numVariadics; varIndex++").end().startBlock();
                        b.startStatement().string(inputName, "[varIndex] = $frame.getObject($sp - numVariadics + varIndex)").end();
                        b.end();
                        inputTrees[inputIndex++] = CodeTreeBuilder.singleString(inputName);
                        break;
                    case VIRTUAL_FRAME:
                        inputTrees[inputIndex++] = CodeTreeBuilder.singleVariable(vars.frame);
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected value: " + kind);
                }
            }

            if (numPushedValues > 0) {
                b.startAssign("Object result");
            } else {
                b.startStatement();
            }

            b.startStaticCall(executeMethod);
            b.variable(vars.frame);
            b.string("$this");
            b.variable(vars.bc);
            b.variable(vars.bci);
            b.variable(vars.sp);
            b.variable(vars.consts);
            b.variable(vars.children);
            b.trees(inputTrees);
            b.end(2);

            b.startAssign(vars.sp).variable(vars.sp).string(" - " + additionalInputs + " - numVariadics + " + numPushedValues).end();

            if (numPushedValues > 0) {
                b.startStatement().startCall(vars.frame, "setObject");
                b.string("$sp - 1");
                b.string("result");
                b.end(2);
            }

        } else {
            b.startStatement();
            b.startStaticCall(executeMethod);
            b.variable(vars.frame);
            b.string("$this");
            b.variable(vars.bc);
            b.variable(vars.bci);
            b.variable(vars.sp);
            b.variable(vars.consts);
            b.variable(vars.children);
            b.end(2);

            b.startAssign(vars.sp).variable(vars.sp).string(" - " + data.getMainProperties().numStackValues + " + " + numPushedValues).end();
        }

        return b.build();
    }

    @Override
    protected CodeTree createConstantInitCode(BuilderVariables vars, EmitArguments args, Object marker, int index) {
        if (marker.equals(MARKER_LOCAL_REFS)) {
            return CodeTreeBuilder.createBuilder().startStaticCall(types.LocalSetterRange, "create").startCall("getLocalIndices").tree(args.constants[index]).end(2).build();
        }

        if (marker instanceof String && ((String) marker).startsWith(MARKER_LOCAL_REF_PREFIX)) {
            return CodeTreeBuilder.createBuilder().startStaticCall(types.LocalSetter, "create").startCall("getLocalIndex").tree(args.constants[index]).end(2).build();
        }

        return super.createConstantInitCode(vars, args, marker, index);
    }

    protected void createTracerCode(ExecutionVariables vars, CodeTreeBuilder b) {
        if (vars.tracer != null) {
            b.startStatement().startCall(vars.tracer, "traceActiveSpecializations");
            b.variable(vars.bci);
            b.variable(opcodeIdField);

            b.startStaticCall(getSpecializationBits);
            b.variable(vars.bc);
            b.variable(vars.bci);
            b.end();

            b.end(2);
        }
    }

    public void setPrepareAOTMethod(CodeExecutableElement prepareAOTMethod) {
        this.prepareAOTMethod = prepareAOTMethod;
    }

    public void setGetSpecializationBits(CodeExecutableElement getSpecializationBits) {
        this.getSpecializationBits = getSpecializationBits;
    }

    public void setBoxingEliminationData(CodeTree boxingEliminationBitOffset, int boxingEliminationBitMask) {
        this.boxingEliminationBitOffset = boxingEliminationBitOffset;
        this.boxingEliminationBitMask = boxingEliminationBitMask;
    }

    @Override
    public BoxingEliminationBehaviour boxingEliminationBehaviour() {
        return numPushedValues > 0 ? BoxingEliminationBehaviour.SET_BIT : BoxingEliminationBehaviour.DO_NOTHING;
    }

    @Override
    public CodeTree boxingEliminationBitOffset() {
        return boxingEliminationBitOffset == null ? CodeTreeBuilder.singleString("0") : boxingEliminationBitOffset;
    }

    @Override
    public int boxingEliminationBitMask() {
        return boxingEliminationBitMask;
    }

    public OperationsBytecodeNodeGeneratorPlugs getPlugs() {
        return plugs;
    }

    public void setPlugs(OperationsBytecodeNodeGeneratorPlugs plugs) {
        this.plugs = plugs;
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        if (prepareAOTMethod == null) {
            return null;
        }

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startStatement().startStaticCall(prepareAOTMethod);
        b.string("null");
        b.string("$this");
        b.string("$bc");
        b.variable(vars.bci);
        b.string("-1");
        b.string("$consts");
        b.string("$children");
        b.tree(language);
        b.tree(root);
        b.end(2);

        return b.build();
    }

    public void addQuickenedVariant(QuickenedInstruction quick) {
        quickenedVariants.add(quick);
    }

    public List<QuickenedInstruction> getQuickenedVariants() {
        return quickenedVariants;
    }
}
