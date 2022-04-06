package com.oracle.truffle.dsl.processor.generator;

import java.util.List;
import java.util.function.Consumer;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
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

    boolean createCheckCast(TypeSystemData typeSystem, FrameState frameState, TypeMirror targetType, LocalVariable value, CodeTreeBuilder prepareBuilder, CodeTreeBuilder checkBuilder,
                    CodeTreeBuilder castBuilder);

    boolean createImplicitCheckCast(TypeSystemData typeSystem, FrameState frameState, TypeMirror targetType, LocalVariable value, CodeTree implicitState, CodeTreeBuilder prepareBuilder,
                    CodeTreeBuilder checkBuilder, CodeTreeBuilder castBuilder);

    boolean createImplicitCheckCastSlowPath(TypeSystemData typeSystem, FrameState frameState, TypeMirror targetType, LocalVariable value, String implicitStateName, CodeTreeBuilder prepareBuilder,
                    CodeTreeBuilder checkBuilder, CodeTreeBuilder castBuilder);

    boolean createSameTypeCast(FrameState frameState, LocalVariable value, TypeMirror genericTargetType, CodeTreeBuilder prepareBuilder, CodeTreeBuilder castBuilder);

    CodeTree[] createThrowUnsupportedValues(FrameState frameState, List<CodeTree> values, CodeTreeBuilder parent, CodeTreeBuilder builder);

    void initializeFrameState(FrameState frameState, CodeTreeBuilder builder);

    boolean createCallSpecialization(SpecializationData specialization, CodeTree specializationCall, CodeTreeBuilder builder, boolean inBoundary);

    boolean createCallExecuteAndSpecialize(CodeTreeBuilder builder, CodeTree call);

    void createCallBoundaryMethod(CodeTreeBuilder builder, FrameState frameState, CodeExecutableElement boundaryMethod, Consumer<CodeTreeBuilder> addArguments);

}
