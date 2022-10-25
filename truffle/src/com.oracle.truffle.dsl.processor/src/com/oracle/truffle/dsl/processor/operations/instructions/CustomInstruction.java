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
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsBytecodeNodeGeneratorPlugs;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.ParameterKind;

public class CustomInstruction extends Instruction {

    private final SingleOperationData data;
    protected ExecutableElement[] executeMethods;
    protected ExecutableElement uncachedExecuteMethod;
    private OperationsBytecodeNodeGeneratorPlugs plugs;
    private CodeExecutableElement prepareAOTMethod;
    private CodeExecutableElement getSpecializationBits;
    private final List<QuickenedInstruction> quickenedVariants = new ArrayList<>();

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

    public void setExecuteMethod(ExecutableElement[] executeMethods) {
        this.executeMethods = executeMethods;
    }

    public CustomInstruction(OperationsContext ctx, String name, int id, SingleOperationData data) {
        super(ctx, name, id, data.getMainProperties().returnsValue ? 1 : 0);
        this.data = data;
        initializePops();
    }

    public static final String MARKER_LOCAL_REFS = "LocalSetterRange";
    public static final String MARKER_LOCAL_REF_PREFIX = "LocalSetter_";
    public static final String MARKER_IMMEDIATEE_PREFIX = "Immediate_";

    private int[] localRefs = null;
    private int[] immediates = null;

    protected void initializePops() {
        MethodProperties props = data.getMainProperties();

        if (props.isVariadic) {
            setVariadic();
            for (int i = 0; i < props.numStackValues - 1; i++) {
                addPopIndexed("arg" + i);
            }
        } else {
            for (int i = 0; i < props.numStackValues; i++) {
                addPopIndexed("arg" + i);
            }
        }

        if (props.numLocalReferences == -1) {
            localRefs = new int[1];
            localRefs[0] = addConstant(MARKER_LOCAL_REFS, new CodeTypeMirror.ArrayCodeTypeMirror(types.OperationLocal));
        } else {
            localRefs = new int[props.numLocalReferences];
            for (int i = 0; i < props.numLocalReferences; i++) {
                localRefs[i] = addConstant(MARKER_LOCAL_REF_PREFIX + i, types.OperationLocal);
            }
        }

        if (props.immediateTypes.size() > 0) {
            immediates = new int[props.immediateTypes.size()];
            for (int i = 0; i < props.immediateTypes.size(); i++) {
                immediates[i] = addConstant(MARKER_IMMEDIATEE_PREFIX + i, props.immediateTypes.get(i));
            }
        }
    }

    protected CustomInstruction(OperationsContext ctx, String name, int id, SingleOperationData data, int pushCount) {
        super(ctx, name, id, pushCount);
        this.data = data;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        return createExecuteImpl(vars, false);
    }

    @Override
    public CodeTree createExecuteUncachedCode(ExecutionVariables vars) {
        return createExecuteImpl(vars, true);
    }

    private void addLocalRefs(CodeTreeBuilder b, ExecutionVariables vars) {
        if (data.getMainProperties().numLocalReferences == -1) {
            b.startGroup().cast(types.LocalSetterRange).variable(vars.consts).string("[").tree(createConstantIndex(vars, localRefs[0])).string("]").end();
        } else {
            for (int i = 0; i < data.getMainProperties().numLocalReferences; i++) {
                b.startGroup().cast(types.LocalSetter).variable(vars.consts).string("[").tree(createConstantIndex(vars, localRefs[i])).string("]").end();
            }
        }
    }

    @Override
    public boolean splitOnBoxingElimination() {
        return !data.isAlwaysBoxed() && data.getMainProperties().returnsValue;
    }

    @Override
    public boolean alwaysBoxed() {
        return data.isAlwaysBoxed();
    }

    @Override
    public List<FrameKind> getBoxingEliminationSplits() {
        List<FrameKind> result = new ArrayList<>();
        result.add(FrameKind.OBJECT);
        result.addAll(data.getPossiblePrimitiveTypes());

        return result;
    }

    private CodeTree createExecuteImpl(ExecutionVariables vars, boolean uncached) {

        String exName = getUniqueName() + "_entryPoint_" + (uncached ? "uncached" : (!alwaysBoxed() && ctx.hasBoxingElimination() ? vars.specializedKind : ""));
        OperationGeneratorUtils.createHelperMethod(ctx.outerType, exName, () -> {
            CodeExecutableElement el = new CodeExecutableElement(Set.of(Modifier.STATIC, Modifier.PRIVATE), context.getType(void.class), exName);
            el.getParameters().addAll(executeMethods[0].getParameters());

            CodeTreeBuilder b = el.createBuilder();

            if (numPushedValues > 0) {

                if (isVariadic()) {
                    b.declaration("int", "destSlot", "$sp - " + (data.getMainProperties().numStackValues - 1) + " - $numVariadics");
                } else {
                    b.declaration("int", "destSlot", "$sp - " + data.getMainProperties().numStackValues);
                }

                if (!uncached && ctx.hasBoxingElimination() && !alwaysBoxed()) {
                    int i = ctx.getBoxingKinds().indexOf(vars.specializedKind);

                    if (i == -1) {
                        throw new AssertionError("kind=" + vars.specializedKind + " name=" + name + "bk=" + ctx.getBoxingKinds());
                    }

                    boolean needsUnexpectedResult = executeMethods[i] != null && executeMethods[i].getThrownTypes().size() > 0;

                    if (needsUnexpectedResult) {
                        b.startTryBlock();
                    }

                    if (executeMethods[i] != null) {
                        b.startStatement().startCall("UFA", "unsafeSet" + vars.specializedKind.getFrameName());

                        b.variable(vars.stackFrame);
                        b.string("destSlot");

                        b.startStaticCall(executeMethods[i]);
                        b.variables(executeMethods[i].getParameters());
                        b.end();

                        b.end(2);

                        b.returnStatement();
                    } else {
                        b.tree(GeneratorUtils.createShouldNotReachHere());
                    }

                    b.end();

                    if (needsUnexpectedResult) {
                        b.end().startCatchBlock(types.UnexpectedResultException, "ex");

                        b.startStatement().startCall("UFA", "unsafeSetObject");
                        b.variable(vars.stackFrame);
                        b.string("destSlot");
                        b.string("ex.getResult()");

                        b.end(2);

                        b.end();
                    }

                } else {
                    ExecutableElement target = uncached ? uncachedExecuteMethod : executeMethods[0];

                    b.startStatement().startCall("UFA", "unsafeSetObject");

                    b.variable(vars.stackFrame);
                    b.string("destSlot");

                    b.startStaticCall(target);
                    b.variables(el.getParameters());

                    if (uncached) {
                        for (int i = 0; i < numPopStatic(); i++) {
                            int offset = numPopStatic() - i;
                            b.startCall("UFA", "unsafeUncheckedGetObject");
                            b.variable(vars.stackFrame);
                            if (isVariadic()) {
                                b.string("$sp - " + offset + " - $numVariadics");
                            } else {
                                b.string("$sp - " + offset);
                            }
                            b.end();
                        }

                        if (isVariadic()) {
                            b.startCall("do_loadVariadicArguments");
                            b.variable(vars.stackFrame);
                            b.variable(vars.sp);
                            b.string("$numVariadics");
                            b.end();
                        }

                        addLocalRefs(b, vars);
                    }

                    b.end();

                    b.end(2);
                }
            } else {

                b.startStatement().startStaticCall(executeMethods[0]);
                b.variables(executeMethods[0].getParameters());
                b.end(2);
            }

            return el;
        });

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        createTracerCode(vars, b);

        if (isVariadic()) {
            b.startAssign("int numVariadics").tree(createVariadicIndex(vars, false)).end();
        }

        b.startStatement();
        b.startCall(exName);
        b.variable(vars.stackFrame);
        if (ctx.getData().enableYield) {
            b.variable(vars.localFrame);
        }
        b.string("$this");
        b.variable(vars.bc);
        b.variable(vars.bci);
        b.variable(vars.sp);
        b.variable(vars.consts);
        b.variable(vars.children);

        if (isVariadic()) {
            b.string("numVariadics");
        }
        b.end(2);

        int stackDelta = numPushedValues - data.getMainProperties().numStackValues + (isVariadic() ? 1 : 0);

        if (isVariadic() || stackDelta != 0) {
            b.startStatement().variable(vars.sp).string(" += ");
            if (stackDelta != 0) {
                b.string("" + stackDelta);
            }
            if (isVariadic()) {
                b.string(" - numVariadics");
            }
            b.end();
        }

        b.startAssign(vars.bci).variable(vars.bci).string(" + ").tree(createLength()).end();
        b.statement("continue loop");

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
        if (ctx.getData().enableYield) {
            b.string("null");
        }
        b.string("$this");
        b.string("$bc");
        b.variable(vars.bci);
        b.string("-1");
        b.string("$consts");
        b.string("$children");
        if (isVariadic()) {
            b.tree(createVariadicIndex(vars, false));
        }
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

    public void setUncachedExecuteMethod(ExecutableElement uncachedExecuteMethod) {
        this.uncachedExecuteMethod = uncachedExecuteMethod;
    }

    @Override
    public boolean neverWrapInMethod() {
        // there is no need to wrap custom instructions in methods, since they already
        // have their own entry-point methods
        return true;
    }
}
