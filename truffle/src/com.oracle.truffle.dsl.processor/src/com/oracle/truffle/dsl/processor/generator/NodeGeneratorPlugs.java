package com.oracle.truffle.dsl.processor.generator;

import java.util.List;
import java.util.function.Consumer;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.BoxingSplit;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.MultiStateBitSet;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.ReportPolymorphismAction;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.ExecutableTypeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;

public interface NodeGeneratorPlugs {
    String transformNodeMethodName(String name);

    String transformNodeInnerTypeName(String name);

    void addNodeCallParameters(CodeTreeBuilder builder, boolean isBoundary, boolean isRemoveThis);

    boolean shouldIncludeValuesInCall();

    int getMaxStateBits(int defaultValue);

    TypeMirror getBitSetType(TypeMirror defaultType);

    CodeTree createBitSetReference(BitSet bits);

    CodeTree transformValueBeforePersist(CodeTree tree);

    CodeTree createSpecializationFieldReference(FrameState frame, SpecializationData s, String fieldName, boolean useSpecializationClass, TypeMirror fieldType);

    CodeTree createNodeFieldReference(FrameState frame, NodeExecutionData execution, String nodeFieldName, boolean forRead);

    CodeTree createCacheReference(FrameState frame, SpecializationData specialization, CacheExpression cache, String sharedName, boolean forRead);

    CodeTree[] createThrowUnsupportedValues(FrameState frameState, List<CodeTree> values, CodeTreeBuilder parent, CodeTreeBuilder builder);

    void initializeFrameState(FrameState frameState, CodeTreeBuilder builder);

    boolean createCallSpecialization(FrameState frameState, SpecializationData specialization, CodeTree specializationCall, CodeTreeBuilder builder, boolean inBoundary, CodeTree[] bindings);

    boolean createCallExecuteAndSpecialize(FrameState frameState, CodeTreeBuilder builder, CodeTree call);

    void createCallBoundaryMethod(CodeTreeBuilder builder, FrameState frameState, CodeExecutableElement boundaryMethod, Consumer<CodeTreeBuilder> addArguments);

    boolean createCallWrapInAMethod(FrameState frameState, CodeTreeBuilder parentBuilder, CodeExecutableElement method, Runnable addStateParameters);

    CodeTree createCallChildExecuteMethod(NodeExecutionData execution, ExecutableTypeData method, FrameState frameState);

    CodeTree createThrowUnsupportedChild(NodeExecutionData execution);

    Boolean needsFrameToExecute(List<SpecializationData> specializations);

    void addAdditionalStateBits(List<Object> stateObjects);

    void setStateObjects(List<Object> stateObjects);

    void setMultiState(MultiStateBitSet multiState);

    int getRequiredStateBits(TypeSystemData types, Object object);

    void createSpecialize(FrameState frameState, SpecializationData specialization, CodeTreeBuilder builder);

    boolean needsRewrites();

    void setBoxingSplits(List<BoxingSplit> boxingSplits);

    List<SpecializationData> filterSpecializations(List<SpecializationData> implementedSpecializations);

    boolean isStateGuaranteed(boolean stateGuaranteed);

    StaticConstants createConstants();

    ReportPolymorphismAction createReportPolymorhoismAction(ReportPolymorphismAction original);

}
