package com.oracle.truffle.dsl.processor.generator;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class NodeGeneratorPlugs {
    public String transformNodeMethodName(String name) {
        return name;
    }

    public String transformNodeInnerTypeName(String name) {
        return name;
    }

    public void addNodeCallParameters(CodeTreeBuilder builder) {
    }

    public int getMaxStateBits(int defaultValue) {
        return defaultValue;
    }

    public TypeMirror getBitSetType(TypeMirror defaultType) {
        return defaultType;
    }

    public CodeTree createBitSetReference(BitSet bits) {
        return CodeTreeBuilder.singleString("this." + bits.getName() + "_");
    }

    public CodeTree transformValueBeforePersist(CodeTree tree) {
        return tree;
    }
}
