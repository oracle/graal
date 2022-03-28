package com.oracle.truffle.dsl.processor.generator;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.Parameter;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

public interface NodeGeneratorPlugs {
    String transformNodeMethodName(String name);

    String transformNodeInnerTypeName(String name);

    void addNodeCallParameters(CodeTreeBuilder builder);

    int getMaxStateBits(int defaultValue);

    TypeMirror getBitSetType(TypeMirror defaultType);

    CodeTree createBitSetReference(BitSet bits);

    CodeTree transformValueBeforePersist(CodeTree tree);

    CodeTree createSpecializationFieldReference(SpecializationData s, String fieldName, boolean useSpecializationClass, TypeMirror fieldType);

    CodeTree createNodeFieldReference(NodeExecutionData execution, String nodeFieldName, boolean forRead);

    CodeTree createCacheReference(SpecializationData specialization, CacheExpression cache, String sharedName, boolean forRead);

}
