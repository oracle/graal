package com.oracle.truffle.dsl.processor.generator;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.MultiStateBitSet;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.ExecutableTypeData;
import com.oracle.truffle.dsl.processor.model.NodeData;
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

    CodeTree createSpecializationFieldReference(SpecializationData s, String fieldName, boolean useSpecializationClass, TypeMirror fieldType);

    CodeTree createNodeFieldReference(NodeExecutionData execution, String nodeFieldName, boolean forRead);

    CodeTree createCacheReference(SpecializationData specialization, CacheExpression cache, String sharedName, boolean forRead);

    CodeTree[] createThrowUnsupportedValues(FrameState frameState, List<CodeTree> values, CodeTreeBuilder parent, CodeTreeBuilder builder);

    void initializeFrameState(FrameState frameState, CodeTreeBuilder builder);

    boolean createCallSpecialization(FrameState frameState, SpecializationData specialization, CodeTree specializationCall, CodeTreeBuilder builder, boolean inBoundary);

    boolean createCallExecuteAndSpecialize(FrameState frameState, CodeTreeBuilder builder, CodeTree call);

    void createCallBoundaryMethod(CodeTreeBuilder builder, FrameState frameState, CodeExecutableElement boundaryMethod, Consumer<CodeTreeBuilder> addArguments);

    boolean createCallWrapInAMethod(FrameState frameState, CodeTreeBuilder parentBuilder, CodeExecutableElement method, Runnable addStateParameters);

    CodeTree createAssignExecuteChild(
                    NodeData node, FrameState originalFrameState, FrameState frameState, CodeTreeBuilder parent, NodeExecutionData execution, ExecutableTypeData forType, LocalVariable targetValue,
                    Function<FrameState, CodeTree> createExecuteAndSpecialize);

    CodeTree createThrowUnsupportedChild(NodeExecutionData execution);

    Boolean needsFrameToExecute(List<SpecializationData> specializations);

    void addAdditionalStateBits(List<Object> stateObjects);

    void setMultiState(MultiStateBitSet multiState);

    int getRequiredStateBits(TypeSystemData types, Object object);

    void createSpecialize(FrameState frameState, SpecializationData specialization, CodeTreeBuilder builder);

    boolean needsRewrites();
}
