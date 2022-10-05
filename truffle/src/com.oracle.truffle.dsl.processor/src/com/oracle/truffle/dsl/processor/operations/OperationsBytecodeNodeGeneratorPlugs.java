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

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.generator.BitSet;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.BoxingSplit;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.MultiStateBitSet;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.ReportPolymorphismAction;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.StateBitSet;
import com.oracle.truffle.dsl.processor.generator.NodeGeneratorPlugs;
import com.oracle.truffle.dsl.processor.generator.StaticConstants;
import com.oracle.truffle.dsl.processor.generator.TypeSystemCodeGenerator;
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
    private final CustomInstruction cinstr;
    private final StaticConstants staticConstants;

    private final ProcessorContext context;
    private final TruffleTypes types;
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
    }

    OperationsBytecodeNodeGeneratorPlugs(OperationsData m, Set<String> innerTypeNames, Set<String> methodNames, CustomInstruction cinstr, StaticConstants staticConstants,
                    boolean uncached) {
        this.m = m;
        OperationsBytecodeCodeGenerator.populateVariables(dummyVariables, m);
        this.innerTypeNames = innerTypeNames;
        this.methodNames = methodNames;
        this.cinstr = cinstr;
        this.staticConstants = staticConstants;

        this.data = cinstr.getData();

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
            if (m.enableYield) {
                builder.string("$stackFrame");
                builder.string("$localFrame");
            } else {
                builder.string("$frame");
            }
        }

        builder.string("$this");
        builder.string("$bc");
        builder.string("$bci");
        builder.string("$sp");
        builder.string("$consts");
        builder.string("$children");

        if (cinstr.isVariadic()) {
            builder.string("$numVariadics");
        }

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
    public CodeTree createBitSetReference(BitSet bits, boolean write) {
        return cinstr.createStateBitsIndex(dummyVariables, cinstr.addStateBits(bits), write);
    }

    @Override
    public CodeTree transformValueBeforePersist(CodeTree tree) {
        return CodeTreeBuilder.createBuilder().cast(getBitSetType(null)).startParantheses().tree(tree).end().build();
    }

    private static final String CHILD_OFFSET_NAME = "childArrayOffset_";
    private static final String CONST_OFFSET_NAME = "constArrayOffset_";

    private CodeTree createArrayReference(FrameState frame, Object refObject, boolean doCast, TypeMirror castTarget, boolean isChild, boolean write) {
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

        if (write) {
            b.variable(targetField).string("[");
        } else {
            b.startCall("UFA", "unsafeObjectArrayRead");
            b.variable(targetField);
        }

        b.startGroup();

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

        b.string(" + " + index).end(); // group
        if (write) {
            b.string("]");
        } else {
            b.end(); // call
        }

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
        return createArrayReference(frame, refObject, !write, fieldType, isChild, write);
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
        return createArrayReference(frame, execution, forRead, execution.getNodeType(), true, !forRead);
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
            base = createArrayReference(frame, refObject, innerForRead, mir, isChild, !forRead);
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
        return values.toArray(new CodeTree[values.size()]);
    }

    @Override
    public void initializeFrameState(FrameState frameState, CodeTreeBuilder builder) {
        // todo: should we have the outer (local) frame as the "frame" ? probably
        frameState.set("frameValue", new LocalVariable(types.VirtualFrame, m.enableYield ? "$localFrame" : "$frame", null));
        builder.declaration("int", CHILD_OFFSET_NAME, (CodeTree) null);
        builder.declaration("int", CONST_OFFSET_NAME, (CodeTree) null);
        frameState.setBoolean("definedOffsets", true);
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

        return false;
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
            return createArrayReference(frameState, CustomInstruction.MARKER_LOCAL_REFS, true, types.LocalSetterRange, false, false);
        }

        if (execution.getName().startsWith("$localRef")) {
            return createArrayReference(frameState, CustomInstruction.MARKER_LOCAL_REF_PREFIX + execution.getName().substring(9), true, types.LocalSetter, false, false);
        }

        if (execution.getName().startsWith("$immediate")) {
            int index = Integer.parseInt(execution.getName().substring(10));
            return createArrayReference(frameState, CustomInstruction.MARKER_IMMEDIATEE_PREFIX + index, true, data.getMainProperties().immediateTypes.get(index), false, false);
        }

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (execution.getName().equals("$variadicChild")) {
            b.startCall("do_loadVariadicArguments");
            b.string(m.enableYield ? "$stackFrame" : "$frame");
            b.string("$sp");
            b.string("$numVariadics");
            b.end();
            return b.build();
        }

        int childIndex = execution.getIndex();
        int offset = cinstr.numPopStatic() - childIndex;

        FrameKind resultKind = getFrameType(method.getReturnType().getKind());

        b.startCall("expect" + resultKind.getFrameName());
        b.string(m.enableYield ? "$stackFrame" : "$frame");
        if (cinstr.isVariadic()) {
            b.string("$sp - " + offset + " - $numVariadics");
        } else {
            b.string("$sp - " + offset);
        }
        if (m.getOperationsContext().hasBoxingElimination()) {
            b.string("$bc");
            b.string("$bci");
            b.tree(cinstr.createPopIndexedIndex(dummyVariables, childIndex, false));
        }
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

                b.startAssign("short primitiveTagBits").string("(short) (").tree(OperationGeneratorUtils.createReadOpcode(dummyVariables.bc, dummyVariables.bci)).string(" & 0xe000)").end();

                // only quicken/unquicken for instructions that have quickened versions
                boolean elseIf = false;
                for (QuickenedInstruction qinstr : quickened) {
                    if (qinstr.getActiveSpecs().contains(specialization)) {
                        elseIf = b.startIf(elseIf);
                        b.tree(multiState.createIs(frameState, qinstr.getActiveSpecs().toArray(), specializationStates.toArray()));
                        b.end().startBlock();
                        // {
                        b.tree(OperationGeneratorUtils.createWriteOpcode(dummyVariables.bc, dummyVariables.bci, "(short) (" + qinstr.opcodeIdField.getName() + " | primitiveTagBits)"));
                        // }
                        b.end();
                    }
                }

                if (elseIf) {
                    b.startElseBlock();
                }

                // quicken to generic
                b.tree(OperationGeneratorUtils.createWriteOpcode(dummyVariables.bc, dummyVariables.bci, "(short) (" + cinstr.opcodeIdField.getName() + " | primitiveTagBits)"));

                if (elseIf) {
                    b.end();
                }
            }
        }
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
    public String createExpectTypeMethodName(TypeSystemData typeSystem, TypeMirror type) {
        String name = "result_expect" + ElementUtils.getTypeId(ElementUtils.boxType(type));
        OperationGeneratorUtils.createHelperMethod(m.getOperationsContext().outerType, name, () -> {
            CodeExecutableElement el = new CodeExecutableElement(Set.of(Modifier.PRIVATE, Modifier.STATIC), type, name);

            el.addParameter(new CodeVariableElement(context.getType(Object.class), "value"));
            el.addThrownType(types.UnexpectedResultException);

            CodeTreeBuilder b = el.createBuilder();

            b.startIf().string("value").instanceOf(ElementUtils.boxType(type)).end().startBlock();
            b.startReturn().cast(type).string("value").end();
            b.end().startElseBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.startThrow().startNew(types.UnexpectedResultException).string("value").end(2);
            b.end();

            return el;
        });

        return name;
    }

    public CodeTree createCallExecute(FrameState frameState, ExecutableElement executableElement, CodeTree[] codeTrees) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (codeTrees.length > 1) {
            throw new AssertionError(Arrays.toString(codeTrees));
        }

        b.startCall(transformNodeMethodName(executableElement.getSimpleName().toString()));
        addNodeCallParameters(b, false, false);
        b.end();

        return b.build();
    }

    public String createExecuteAndSpecializeName(String result) {
        CustomInstruction instr = cinstr;
        if (cinstr instanceof QuickenedInstruction) {
            instr = ((QuickenedInstruction) cinstr).getOrig();
        }

        return instr.getUniqueName() + "_executeAndSpecialize_";
    }
}
