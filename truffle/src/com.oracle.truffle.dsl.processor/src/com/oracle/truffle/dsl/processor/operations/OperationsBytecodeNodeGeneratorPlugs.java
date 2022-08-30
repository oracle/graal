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
package com.oracle.truffle.dsl.processor.operations;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.isAssignable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.generator.BitSet;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.BoxingSplit;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.MultiStateBitSet;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.ReportPolymorphismAction;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.StateBitSet;
import com.oracle.truffle.dsl.processor.generator.NodeGeneratorPlugs;
import com.oracle.truffle.dsl.processor.generator.StaticConstants;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.ExecutableTypeData;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.FrameKind;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.ExecutionVariables;
import com.oracle.truffle.dsl.processor.operations.instructions.QuickenedInstruction;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

public final class OperationsBytecodeNodeGeneratorPlugs implements NodeGeneratorPlugs {
    private final Set<String> innerTypeNames;
    private final Set<String> methodNames;
    private final boolean isVariadic;
    private final CustomInstruction cinstr;
    private final StaticConstants staticConstants;

    private final ProcessorContext context;
    private final TruffleTypes types;
    private final Object resultUnboxedState;
    private List<Object> specializationStates;

    private MultiStateBitSet multiState;
    private List<BoxingSplit> boxingSplits;

    private OperationsData m;
    private final SingleOperationData data;

    private final ExecutionVariables dummyVariables = new ExecutionVariables();
    private NodeData nodeData;
    private Predicate<SpecializationData> useSpecializationClass;
    private final boolean uncached;

    {
        context = ProcessorContext.getInstance();
        types = context.getTypes();
        OperationsBytecodeCodeGenerator.populateVariables(dummyVariables);
    }

    OperationsBytecodeNodeGeneratorPlugs(OperationsData m, Set<String> innerTypeNames, Set<String> methodNames, boolean isVariadic, CustomInstruction cinstr, StaticConstants staticConstants,
                    boolean uncached) {
        this.m = m;
        this.innerTypeNames = innerTypeNames;
        this.methodNames = methodNames;
        this.isVariadic = isVariadic;
        this.cinstr = cinstr;
        this.staticConstants = staticConstants;

        this.data = cinstr.getData();

        if (cinstr.numPushedValues == 0 || data.isShortCircuit()) {
            resultUnboxedState = null;
        } else {
            resultUnboxedState = new Object() {
                @Override
                public String toString() {
                    return "RESULT-UNBOXED";
                }
            };
        }

        this.uncached = uncached;
    }

    public void setUseSpecializationClass(Predicate<SpecializationData> useSpecializationClass) {
        this.useSpecializationClass = useSpecializationClass;
    }

    public void setNodeData(NodeData node) {
        this.nodeData = node;
        for (SpecializationData spec : node.getSpecializations()) {
            for (CacheExpression cache : spec.getCaches()) {
                createCacheReference(null, spec, cache, cache.getSharedGroup(), false);
            }
        }
    }

    @Override
    public void addAdditionalStateBits(List<Object> stateObjects) {
        if (!stateObjects.isEmpty()) {
            throw new AssertionError("stateObjects must be empty");
        }
        if (resultUnboxedState != null) {
            stateObjects.add(resultUnboxedState);
        }
    }

    @Override
    public void setStateObjects(List<Object> stateObjects) {
        this.specializationStates = stateObjects.stream().filter(x -> x instanceof SpecializationData).collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void setBoxingSplits(List<BoxingSplit> boxingSplits) {
        this.boxingSplits = boxingSplits;
    }

    @Override
    public void setMultiState(MultiStateBitSet multiState) {
        this.multiState = multiState;
    }

    @Override
    public int getRequiredStateBits(TypeSystemData typesData, Object object) {
        return 1;
    }

    @Override
    public String transformNodeMethodName(String name) {
        String result = cinstr.getUniqueName() + "_" + name + "_";
        methodNames.add(result);
        return result;
    }

    @Override
    public String transformNodeInnerTypeName(String name) {
        if (cinstr instanceof QuickenedInstruction) {
            return ((QuickenedInstruction) cinstr).getOrig().getUniqueName() + "_" + name;
        }
        String result = cinstr.getUniqueName() + "_" + name;
        innerTypeNames.add(result);
        return result;
    }

    @Override
    public void addNodeCallParameters(CodeTreeBuilder builder, boolean isBoundary, boolean isRemoveThis) {
        if (!isBoundary) {
            builder.string("$frame");
        }

        builder.string("$this");
        builder.string("$bc");
        builder.string("$bci");
        builder.string("$sp");
        builder.string("$consts");
        builder.string("$children");

        for (int i = 0; i < m.getNumTosSlots(); i++) {
            builder.string("$tos_" + i);
        }
    }

    @Override
    public Boolean needsFrameToExecute(List<SpecializationData> specializations) {
        return false;
    }

    public boolean shouldIncludeValuesInCall() {
        return true;
    }

    @Override
    public int getMaxStateBits(int defaultValue) {
        return 16;
    }

    @Override
    public TypeMirror getBitSetType(TypeMirror defaultType) {
        return new CodeTypeMirror(TypeKind.SHORT);
    }

    @Override
    public CodeTree createBitSetReference(BitSet bits) {
        return cinstr.createStateBitsIndex(dummyVariables, cinstr.addStateBits(bits));
    }

    @Override
    public CodeTree transformValueBeforePersist(CodeTree tree) {
        return CodeTreeBuilder.createBuilder().cast(getBitSetType(null)).startParantheses().tree(tree).end().build();
    }

    private static final String CHILD_OFFSET_NAME = "childArrayOffset_";
    private static final String CONST_OFFSET_NAME = "constArrayOffset_";

    private CodeTree createArrayReference(FrameState frame, Object refObject, boolean doCast, TypeMirror castTarget, boolean isChild) {
        if (refObject == null) {
            throw new IllegalArgumentException("refObject is null");
        }

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        int index;
        if (isChild) {
            index = cinstr.addChild(refObject);
        } else {
            index = cinstr.addConstant(refObject, null);
        }

        String offsetName = isChild ? CHILD_OFFSET_NAME : CONST_OFFSET_NAME;

        if (doCast) {
            b.startParantheses();
            b.cast(castTarget);
        }

        VariableElement targetField;
        if (isChild) {
            targetField = dummyVariables.children;
        } else {
            targetField = dummyVariables.consts;
        }

        b.variable(targetField).string("[");

        if (frame == null || !frame.getBoolean("definedOffsets", false)) {
            if (isChild) {
                b.tree(cinstr.createChildIndex(dummyVariables, 0));
            } else {
                b.tree(cinstr.createConstantIndex(dummyVariables, 0));
            }
        } else if (frame.getBoolean("has_" + offsetName, false)) {
            b.string(offsetName);
        } else {
            frame.setBoolean("has_" + offsetName, true);
            b.string("(" + offsetName + " = ");
            if (isChild) {
                b.tree(cinstr.createChildIndex(dummyVariables, 0));
            } else {
                b.tree(cinstr.createConstantIndex(dummyVariables, 0));
            }
            b.string(")");

        }

        b.string(" + " + index + "]");

        if (doCast) {
            b.end();
        }

        return b.build();
    }

    @Override
    public ReportPolymorphismAction createReportPolymorhoismAction(ReportPolymorphismAction original) {
        // todo: maybe this would be needed at some point?
        return new ReportPolymorphismAction(false, false);
    }

    @Override
    public CodeTree createSpecializationFieldReference(FrameState frame, SpecializationData s, String fieldName, TypeMirror fieldType, boolean write) {
        boolean specClass = useSpecializationClass.test(s);
        Object refObject = specClass ? s : fieldName;
        boolean isChild = specClass ? specializationClassIsNode(s) : ElementUtils.isAssignable(fieldType, types.Node);
        return createArrayReference(frame, refObject, !write, fieldType, isChild);
    }

    /* Specialization class needs to be a Node in such a case. */
    private boolean specializationClassIsNode(SpecializationData specialization) {
        for (CacheExpression cache : specialization.getCaches()) {
            TypeMirror type = cache.getParameter().getType();
            if (isAssignable(type, types.NodeInterface)) {
                return true;
            } else if (isNodeInterfaceArray(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNodeInterfaceArray(TypeMirror type) {
        if (type == null) {
            return false;
        }
        return type.getKind() == TypeKind.ARRAY && isAssignable(((ArrayType) type).getComponentType(), types.NodeInterface);
    }

    @Override
    public CodeTree createNodeFieldReference(FrameState frame, NodeExecutionData execution, String nodeFieldName, boolean forRead) {
        if (nodeFieldName.startsWith("$child")) {
            return CodeTreeBuilder.singleString("__INVALID__");
        }
        return createArrayReference(frame, execution, forRead, execution.getNodeType(), true);
    }

    @Override
    public CodeTree createCacheReference(FrameState frame, SpecializationData specialization, CacheExpression cache, String sharedName, boolean forRead) {
        Object refObject = null;
        TypeMirror mir = null;
        String fieldName = null;
        boolean innerForRead = forRead;
        CodeTree base = null;
        boolean isChild;

        if (sharedName != null) {
            refObject = sharedName;
            mir = cache.getParameter().getType();
            isChild = ElementUtils.isAssignable(mir, types.Node);
        } else if (cache.getSharedGroup() != null) {
            refObject = cache.getSharedGroup();
            mir = cache.getParameter().getType();
            isChild = ElementUtils.isAssignable(mir, types.Node);
        } else if (useSpecializationClass.test(specialization)) {
            LocalVariable specLocal = frame != null
                            ? frame.get(FlatNodeGenFactory.createSpecializationLocalName(specialization))
                            : null;
            fieldName = cache.getParameter().getLocalName() + "_";
            isChild = true;
            if (specLocal != null) {
                base = specLocal.createReference();
            } else {
                refObject = specialization;
                mir = new GeneratedTypeMirror("", cinstr.getUniqueName() + "_" + specialization.getId() + "Data");
                innerForRead = true;
            }
        } else {
            refObject = cache;
            mir = cache.getParameter().getType();
            isChild = ElementUtils.isAssignable(mir, types.Node);
        }

        if (base == null) {
            base = createArrayReference(frame, refObject, innerForRead, mir, isChild);
        }

        if (fieldName == null) {
            return base;
        } else {
            return CodeTreeBuilder.createBuilder().tree(base).string("." + fieldName).build();
        }
    }

    public int getStackOffset(LocalVariable value) {
        String name = value.getName();
        while (name.endsWith("_")) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.startsWith("arg") && name.endsWith("Value")) {
            return cinstr.numPopStatic() - Integer.parseInt(name.substring(3, name.length() - 5));
        }
        if (name.startsWith("child") && name.endsWith("Value")) {
            return cinstr.numPopStatic() - Integer.parseInt(name.substring(5, name.length() - 5));
        }
        throw new UnsupportedOperationException("" + value);
    }

    @Override
    public CodeTree createThrowUnsupportedChild(NodeExecutionData execution) {
        return CodeTreeBuilder.singleString("null");
    }

    @Override
    public CodeTree[] createThrowUnsupportedValues(FrameState frameState, List<CodeTree> values, CodeTreeBuilder parent, CodeTreeBuilder builder) {
        if (regularReturn()) {
            return values.toArray(new CodeTree[values.size()]);
        }
        CodeTree[] result = new CodeTree[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = CodeTreeBuilder.singleString("$frame.getValue($sp - " + (cinstr.numPopStatic() - i) + ")");
        }

        return result;
    }

    @Override
    public void initializeFrameState(FrameState frameState, CodeTreeBuilder builder) {
        frameState.set("frameValue", new LocalVariable(types.VirtualFrame, "$frame", null));
        builder.declaration("int", CHILD_OFFSET_NAME, (CodeTree) null);
        builder.declaration("int", CONST_OFFSET_NAME, (CodeTree) null);
        frameState.setBoolean("definedOffsets", true);
    }

    private void createPushResult(FrameState frameState, CodeTreeBuilder b, CodeTree specializationCall, TypeMirror retType) {
        if (cinstr.numPushedValues == 0) {
            b.statement(specializationCall);
            b.returnStatement();
            return;
        }

        if (data.isShortCircuit()) {
            b.startReturn();
            b.tree(specializationCall);
            b.end();
            return;
        }

        assert cinstr.numPushedValues == 1;

        int destOffset = cinstr.numPopStatic();

        CodeTree value;
        String typeName;
        if (retType.getKind() == TypeKind.VOID) {
            // we need to push something, lets just push a `null`.
            // maybe this should be an error? DSL just returns default value

            b.statement(specializationCall);
            value = CodeTreeBuilder.singleString("null");
            typeName = "Object";
        } else {
            value = specializationCall;
            typeName = getFrameType(retType.getKind()).getFrameName();
        }

        CodeTree isResultBoxed = multiState.createNotContains(frameState, new Object[]{resultUnboxedState});

        if (uncached || data.isDisableBoxingElimination() || typeName.equals("Object")) {
            b.startStatement();
            b.startCall("UFA", "unsafeSetObject");
            b.string("$frame");
            b.string("$sp - " + destOffset);
            b.tree(value);
            b.end(2);
        } else {
            b.declaration(retType, "value", value);
            b.startIf().tree(isResultBoxed).end().startBlock();
            // {
            b.startStatement();
            b.startCall("UFA", "unsafeSetObject");
            b.string("$frame");
            b.string("$sp - " + destOffset);
            b.string("value");
            b.end(2);
            // }
            b.end().startElseBlock();
            // {
            b.startStatement();
            b.startCall("UFA", "unsafeSet" + typeName);
            b.string("$frame");
            b.string("$sp - " + destOffset);
            b.string("value");
            b.end(2);
            // }
            b.end();
        }

        b.returnStatement();

    }

    @Override
    public boolean createCallSpecialization(FrameState frameState, SpecializationData specialization, CodeTree specializationCall, CodeTreeBuilder b, boolean inBoundary, CodeTree[] bindings) {

        // if (m.isTracing()) {
        // b.startStatement().startCall("tracer", "traceSpecialization");
        // b.string("$bci");
        // b.variable(cinstr.opcodeIdField);
        // b.string("" + specialization.getIntrospectionIndex());
        // for (int i = 0; i < bindings.length; i++) {
        // Parameter parameter = specialization.getParameters().get(i);
        // if (parameter.getSpecification().isSignature()) {
        // b.tree(bindings[i]);
        // }
        // }
        // b.end(2);
        // }

        if (inBoundary || regularReturn()) {
            if (ElementUtils.isVoid(specialization.getMethod().getReturnType())) {
                b.statement(specializationCall);
                b.returnStatement();
            } else {
                b.startReturn().tree(specializationCall).end();
            }
        } else {
            createPushResult(frameState, b, specializationCall, specialization.getMethod().getReturnType());
        }

        return true;
    }

    private boolean regularReturn() {
        return isVariadic || data.isShortCircuit();
    }

    private int ensCall = 0;

    @Override
    public boolean createCallExecuteAndSpecialize(FrameState frameState, CodeTreeBuilder builder, CodeTree call) {
        String easName = transformNodeMethodName("executeAndSpecialize");
        if (cinstr instanceof QuickenedInstruction) {
            QuickenedInstruction qinstr = (QuickenedInstruction) cinstr;

            // unquicken call parent EAS
            builder.tree(OperationGeneratorUtils.createWriteOpcode(dummyVariables.bc, dummyVariables.bci, qinstr.getOrig().opcodeIdField));
            easName = qinstr.getOrig().getUniqueName() + "_executeAndSpecialize_";
        }

        if (OperationGeneratorFlags.LOG_EXECUTE_AND_SPECIALIZE_CALLS) {
            builder.statement("System.out.printf(\" [!!] calling E&S @ %04x : " + cinstr.name + " " + (ensCall++) + " \", $bci)");
            builder.startStatement().startCall("System.out.println").startCall("java.util.Arrays.asList");
            frameState.addReferencesTo(builder);
            builder.end(3);
        }

        if (regularReturn()) {
            builder.startReturn();
        } else {
            builder.startStatement();
        }

        builder.startCall(easName);
        addNodeCallParameters(builder, false, false);
        frameState.addReferencesTo(builder);
        builder.end(2);

        if (!regularReturn()) {
            builder.returnStatement();
        }

        return true;
    }

    @Override
    public void createCallBoundaryMethod(CodeTreeBuilder builder, FrameState frameState, CodeExecutableElement boundaryMethod, Consumer<CodeTreeBuilder> addArguments) {
        if (regularReturn()) {
            builder.startReturn().startCall(boundaryMethod.getSimpleName().toString());
            addNodeCallParameters(builder, true, false);
            addArguments.accept(builder);
            builder.end(2);
            return;
        }

        CodeTreeBuilder callBuilder = builder.create();

        callBuilder.startCall(boundaryMethod.getSimpleName().toString());
        addNodeCallParameters(callBuilder, true, false);
        addArguments.accept(callBuilder);
        callBuilder.end();

        createPushResult(frameState, builder, callBuilder.build(), boundaryMethod.getReturnType());
    }

    @Override
    public boolean createCallWrapInAMethod(FrameState frameState, CodeTreeBuilder parentBuilder, CodeExecutableElement method, Runnable addStateParameters) {
        boolean needsReturn;
        if (regularReturn() && method.getReturnType().getKind() != TypeKind.VOID) {
            parentBuilder.startReturn();
            needsReturn = false;
        } else {
            parentBuilder.startStatement();
            needsReturn = true;
        }

        parentBuilder.startCall(method.getSimpleName().toString());
        addNodeCallParameters(parentBuilder, false, false);
        addStateParameters.run();
        frameState.addReferencesTo(parentBuilder);
        parentBuilder.end(2);

        if (needsReturn) {
            parentBuilder.returnStatement();
        }

        return true;
    }

    private FrameKind getFrameType(TypeKind type) {
        if (!m.getBoxingEliminatedTypes().contains(type)) {
            return FrameKind.OBJECT;
        }

        return OperationsData.convertToFrameType(type);
    }

    @Override
    public CodeTree createCallChildExecuteMethod(NodeExecutionData execution, ExecutableTypeData method, FrameState frameState) {
        if (execution.getName().startsWith("$localRefArray")) {
            return createArrayReference(frameState, CustomInstruction.MARKER_LOCAL_REFS, true, types.LocalSetterRange, false);
        }

        if (execution.getName().startsWith("$localRef")) {
            return createArrayReference(frameState, CustomInstruction.MARKER_LOCAL_REF_PREFIX + execution.getName().substring(9), true, types.LocalSetter, false);
        }

        int childIndex = execution.getIndex();
        int offset = cinstr.numPopStatic() - childIndex;

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        FrameKind resultKind = getFrameType(method.getReturnType().getKind());

        b.startCall("expect" + resultKind.getFrameName());
        b.string("$frame");
        b.string("$sp - " + offset);
        b.end();

        return b.build();
    }

    @Override
    public void createSpecialize(FrameState frameState, SpecializationData specialization, CodeTreeBuilder b) {

        if (OperationGeneratorFlags.LOG_EXECUTE_AND_SPECIALIZE_CALLS) {
            b.statement("System.out.println(\" [!!] finished E&S - " + specialization.getId() + " \")");
        }

        // quickening
        if (!(cinstr instanceof QuickenedInstruction)) {
            List<QuickenedInstruction> quickened = cinstr.getQuickenedVariants();

            if (!quickened.isEmpty()) {
                // only quicken/unquicken for instructions that have quickened versions
                boolean elseIf = false;
                for (QuickenedInstruction qinstr : quickened) {
                    if (qinstr.getActiveSpecs().contains(specialization)) {
                        elseIf = b.startIf(elseIf);
                        b.tree(multiState.createIs(frameState, qinstr.getActiveSpecs().toArray(), specializationStates.toArray()));
                        b.end().startBlock();
                        // {
                        b.tree(OperationGeneratorUtils.createWriteOpcode(dummyVariables.bc, dummyVariables.bci, qinstr.opcodeIdField));
                        // }
                        b.end();
                    }
                }

                if (elseIf) {
                    b.startElseBlock();
                }

                // quicken to generic
                b.tree(OperationGeneratorUtils.createWriteOpcode(dummyVariables.bc, dummyVariables.bci, cinstr.opcodeIdField));

                if (elseIf) {
                    b.end();
                }
            }
        }

        // boxing elimination
        if (!regularReturn() && cinstr.numPopStatic() > 0) {
            boolean elseIf = false;
            boolean[] needsElse = new boolean[]{true};

            for (int i = 0; i < cinstr.numPopStatic(); i++) {
                b.declaration("int", "type" + i, (CodeTree) null);
            }

            if (boxingSplits != null && !boxingSplits.isEmpty()) {
                for (BoxingSplit split : boxingSplits) {
                    elseIf = createBoxingSplitUnboxingThing(b, frameState, elseIf, specialization, split.getGroup().collectSpecializations(), split.getPrimitiveSignature(), needsElse);
                }
            } else {
                TypeMirror[] primMirrors = new TypeMirror[cinstr.numPopStatic()];
                List<SpecializationData> specs = cinstr.getData().getNodeData().getSpecializations();
                for (SpecializationData spec : specs) {
                    if (spec.isFallback()) {
                        continue;
                    }
                    for (int i = 0; i < primMirrors.length; i++) {
                        TypeMirror paramType = spec.getParameters().get(i).getType();
                        if (primMirrors[i] == null) {
                            primMirrors[i] = paramType;
                        } else if (!ElementUtils.typeEquals(primMirrors[i], paramType)) {
                            // we only care about primitive types, so we do not care about type
                            // compatibility
                            primMirrors[i] = ProcessorContext.getInstance().getType(Object.class);
                        }
                    }
                }

                elseIf = createBoxingSplitUnboxingThing(b, frameState, elseIf, specialization, specs, primMirrors, needsElse);
            }

            if (needsElse[0]) {
                if (elseIf) {
                    b.startElseBlock();
                }

                for (int i = 0; i < cinstr.numPopStatic(); i++) {
                    b.startAssign("type" + i).tree(OperationGeneratorUtils.toFrameTypeConstant(FrameKind.OBJECT)).end();
                }

                if (elseIf) {
                    b.end();
                }
            }

            for (int i = 0; i < cinstr.numPopStatic(); i++) {
                b.tree(OperationGeneratorUtils.callSetResultBoxed(cinstr.createPopIndexedIndex(dummyVariables, i), CodeTreeBuilder.singleString("type" + i)));
            }
        }

    }

    // creates the if / else cascade for determining the type required for doSetResultBoxed
    private boolean createBoxingSplitUnboxingThing(CodeTreeBuilder b, FrameState frameState, boolean elseIf, SpecializationData specialization, List<SpecializationData> specializations,
                    TypeMirror[] primitiveMirrors, boolean[] needsElse) {
        if (!specializations.contains(specialization)) {
            return elseIf;
        }

        CodeTree tree = multiState.createContainsOnly(frameState, 0, -1, specializations.toArray(), specializationStates.toArray());
        if (!tree.isEmpty()) {
            b.startIf(elseIf);
            b.tree(tree).end().startBlock();
        } else {
            needsElse[0] = false;
        }

        TypeSystemData tsData = cinstr.getData().getNodeData().getTypeSystem();
        for (int i = 0; i < cinstr.numPopStatic(); i++) {
            TypeMirror targetType = i < primitiveMirrors.length ? primitiveMirrors[i] : context.getType(Object.class);
            if (!tsData.hasImplicitSourceTypes(targetType)) {
                FrameKind frameType = getFrameType(targetType.getKind());
                b.startAssign("type" + i).tree(OperationGeneratorUtils.toFrameTypeConstant(frameType)).end();
            } else {
                boolean elseIf2 = false;
                List<TypeMirror> originalSourceTypes = new ArrayList<>(tsData.lookupSourceTypes(targetType));
                for (TypeMirror sourceType : originalSourceTypes) {
                    FrameKind frameType = getFrameType(sourceType.getKind());
                    if (frameType == FrameKind.OBJECT) {
                        continue;
                    }

                    TypeGuard typeGuard = new TypeGuard(targetType, i);

                    elseIf2 = b.startIf(elseIf2);

                    b.tree(multiState.createContainsOnly(frameState, originalSourceTypes.indexOf(sourceType), 1, new Object[]{typeGuard}, new Object[]{typeGuard}));

                    b.end().startBlock();
                    // {
                    b.startAssign("type" + i).tree(OperationGeneratorUtils.toFrameTypeConstant(frameType)).end();
                    // }
                    b.end();
                }

                if (elseIf2) {
                    b.startElseBlock();
                }
                b.startAssign("type" + i).tree(OperationGeneratorUtils.toFrameTypeConstant(FrameKind.OBJECT)).end();
                if (elseIf2) {
                    b.end();
                }
            }
        }

        if (!tree.isEmpty()) {
            b.end();
        }

        return true;
    }

    public CodeTree createSetResultBoxed(CodeVariableElement varUnboxed) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startIf().variable(varUnboxed).end().startBlock();
        b.tree(multiState.createSet(FrameState.createEmpty(), new Object[]{resultUnboxedState}, true, true));
        b.end().startElseBlock();
        b.tree(multiState.createSet(FrameState.createEmpty(), new Object[]{resultUnboxedState}, false, true));
        b.end();
        return b.build();
    }

    @SuppressWarnings("unchecked")
    public CodeTree createGetSpecializationBits() {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        FrameState frame = FrameState.createEmpty();

        b.tree(multiState.createLoad(frame, specializationStates.toArray()));

        b.declaration("boolean[]", "result", "new boolean[" + specializationStates.size() + "]");

        for (int i = 0; i < specializationStates.size(); i++) {
            SpecializationData specData = (SpecializationData) specializationStates.get(i);
            b.startAssign("result[" + i + "]");
            b.tree(multiState.createContains(frame, new Object[]{specData}));
            Set<SpecializationData> excludedBy = specData.getExcludedBy();

            if (!excludedBy.isEmpty()) {
                b.string(" && ");
                b.tree(multiState.createNotContains(frame, excludedBy.toArray()));
            }

            b.end();
        }

        b.startReturn().string("result").end();

        return b.build();
    }

    public boolean needsRewrites() {
        return true;
    }

    @Override
    public List<SpecializationData> filterSpecializations(List<SpecializationData> implementedSpecializations) {
        if (!(cinstr instanceof QuickenedInstruction)) {
            return implementedSpecializations;
        }

        QuickenedInstruction qinstr = (QuickenedInstruction) cinstr;
        return qinstr.getActiveSpecs();
    }

    @Override
    public boolean isStateGuaranteed(boolean stateGuaranteed) {
        if (stateGuaranteed) {
            return true;
        }

        if (cinstr instanceof QuickenedInstruction && ((QuickenedInstruction) cinstr).getActiveSpecs().size() == 1) {
            return true;
        }

        return false;
    }

    public void finishUp() {
        if (data.isShortCircuit()) {
            cinstr.setBoxingEliminationData(CodeTreeBuilder.singleString("0"), 0);
        } else if (cinstr.numPushedValues > 0) {
            int offset = -1;
            BitSet targetSet = null;
            for (StateBitSet set : multiState.getSets()) {
                if (set.contains(resultUnboxedState)) {
                    targetSet = set;
                    offset = Arrays.asList(set.getObjects()).indexOf(resultUnboxedState);
                    break;
                }
            }

            if (offset < 0 || targetSet == null) {
                throw new AssertionError();
            }

            cinstr.setBoxingEliminationData(cinstr.createStateBitsOffset(cinstr.addStateBits(targetSet)), 1 << offset);
        }
    }

    public StaticConstants createConstants() {
        return staticConstants;
    }

    @Override
    public CodeTree createGetLock() {
        return CodeTreeBuilder.singleString("$this.getLockAccessor()");
    }

    @Override
    public CodeTree createSuperInsert(CodeTree value) {
        return CodeTreeBuilder.createBuilder().startCall("$this.insertAccessor").tree(value).end().build();
    }

    @Override
    public CodeTree createReturnUnexpectedResult(FrameState frameState, ExecutableTypeData forType, boolean needsCast) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        TypeMirror retType = context.getType(Object.class);
        createPushResult(frameState, b, CodeTreeBuilder.singleString((needsCast ? "((UnexpectedResultException) ex)" : "ex") + ".getResult()"), retType);

        return b.build();
    }
}
