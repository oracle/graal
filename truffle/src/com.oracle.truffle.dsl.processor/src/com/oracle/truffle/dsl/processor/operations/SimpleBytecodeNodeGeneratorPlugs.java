package com.oracle.truffle.dsl.processor.operations;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.BitSet;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.BoxingSplit;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.MultiStateBitSet;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.ReportPolymorphismAction;
import com.oracle.truffle.dsl.processor.generator.NodeGeneratorPlugs;
import com.oracle.truffle.dsl.processor.generator.StaticConstants;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.ExecutableTypeData;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.FrameKind;

public class SimpleBytecodeNodeGeneratorPlugs implements NodeGeneratorPlugs {

    private final OperationsData m;
    private final CustomInstruction cinstr;
    private final StaticConstants staticConstants;

    public SimpleBytecodeNodeGeneratorPlugs(OperationsData m, CustomInstruction cinstr, StaticConstants staticConstants) {
        this.m = m;
        this.cinstr = cinstr;
        this.staticConstants = staticConstants;
    }

    public void setNodeData(NodeData node) {
    }

    public String transformNodeMethodName(String name) {
        return name;
    }

    public String transformNodeInnerTypeName(String name) {
        return name;
    }

    public void addNodeCallParameters(CodeTreeBuilder builder, boolean isBoundary, boolean isRemoveThis) {
        if (!isBoundary) {
            if (m.enableYield) {
                builder.string("$stackFrame");
                builder.string("$localsFrame");
            } else {
                builder.string("$frame");
            }
        }
        builder.string("$bci");
        builder.string("$sp");
        if (cinstr.getData().getMainProperties().isVariadic) {
            builder.string("$numVariadics");
        }
    }

    public boolean shouldIncludeValuesInCall() {
        return true;
    }

    public int getMaxStateBits(int defaultValue) {
        return defaultValue;
    }

    public TypeMirror getBitSetType(TypeMirror defaultType) {
        return defaultType;
    }

    public CodeTree createBitSetReference(BitSet bits, boolean write) {
        return CodeTreeBuilder.singleString("this." + bits.getName() + "_");
    }

    public CodeTree transformValueBeforePersist(CodeTree tree) {
        return tree;
    }

    public CodeTree createNodeFieldReference(FrameState frame, NodeExecutionData execution, String nodeFieldName, boolean forRead) {
        return null;
    }

    public CodeTree createCacheReference(FrameState frame, SpecializationData specialization, CacheExpression cache, String sharedName, boolean forRead) {
        return null;
    }

    public CodeTree[] createThrowUnsupportedValues(FrameState frameState, List<CodeTree> values, CodeTreeBuilder parent, CodeTreeBuilder builder) {
        return values.toArray(new CodeTree[0]);
    }

    public void initializeFrameState(FrameState frameState, CodeTreeBuilder builder) {
    }

    public boolean createCallExecuteAndSpecialize(FrameState frameState, CodeTreeBuilder builder, CodeTree call) {
        return false;
    }

    public CodeTree createCallChildExecuteMethod(NodeExecutionData execution, ExecutableTypeData method, FrameState frameState) {
        String exName = execution.getName();
        if (exName.startsWith("$localRefArray")) {
            return CodeTreeBuilder.singleString("this.op_localRefArray_");
        }
        if (exName.startsWith("$localRef")) {
            return CodeTreeBuilder.singleString("this.op_localRef_" + exName.substring(9) + "_");
        }

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (exName.equals("$variadicChild")) {
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
            b.string("this.op_popIndexed_" + childIndex + "_");
        }
        b.end();

        return b.build();
    }

    private FrameKind getFrameType(TypeKind type) {
        if (!m.getBoxingEliminatedTypes().contains(type)) {
            return FrameKind.OBJECT;
        }

        return OperationsData.convertToFrameType(type);
    }

    public CodeTree createThrowUnsupportedChild(NodeExecutionData execution) {
        return CodeTreeBuilder.singleString("null");
    }

    public Boolean needsFrameToExecute(List<SpecializationData> specializations) {
        return true;
    }

    public void addAdditionalStateBits(List<Object> stateObjects) {
    }

    public void setStateObjects(List<Object> stateObjects) {
    }

    public void setMultiState(MultiStateBitSet multiState) {
    }

    public int getRequiredStateBits(TypeSystemData types, Object object) {
        throw new AssertionError();
    }

    public void createSpecialize(FrameState frameState, SpecializationData specialization, CodeTreeBuilder builder) {
    }

    public boolean needsRewrites() {
        return true;
    }

    public void setBoxingSplits(List<BoxingSplit> boxingSplits) {
    }

    public List<SpecializationData> filterSpecializations(List<SpecializationData> implementedSpecializations) {
        return implementedSpecializations;
    }

    public boolean isStateGuaranteed(boolean stateGuaranteed) {
        return stateGuaranteed;
    }

    public StaticConstants createConstants() {
        return staticConstants;
    }

    public ReportPolymorphismAction createReportPolymorhoismAction(ReportPolymorphismAction original) {
        return new ReportPolymorphismAction(false, false);
    }

    public CodeTree createGetLock() {
        return CodeTreeBuilder.singleString("getLock()");
    }

    public CodeTree createSuperInsert(CodeTree value) {
        return CodeTreeBuilder.createBuilder().startCall("super.insert").tree(value).end().build();
    }

    public void setUseSpecializationClass(Predicate<SpecializationData> useSpecializationClass) {
    }

    public String createExpectTypeMethodName(TypeSystemData typeSystem, TypeMirror type) {
        return null;
    }

    public CodeTree createCallExecute(FrameState frameState, ExecutableElement executableElement, CodeTree[] codeTrees) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (codeTrees.length > 1) {
            throw new AssertionError(Arrays.toString(codeTrees));
        }

        b.startCall(executableElement.getSimpleName().toString());
        addNodeCallParameters(b, false, false);
        b.end();

        return b.build();
    }

    public String createExecuteAndSpecializeName(String result) {
        return result;
    }

}
