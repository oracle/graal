package com.oracle.truffle.llvm.parser.base.model.visitors;

import com.oracle.truffle.llvm.parser.base.model.blocks.MetadataBlock.MetadataReference;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataBasicType;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataCompileUnit;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataCompositeType;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataDerivedType;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataEnumerator;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataFile;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataFnNode;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataGlobalVariable;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataKind;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataLexicalBlock;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataLexicalBlockFile;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataLocalVariable;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataName;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataNamedNode;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataNode;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataString;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataSubprogram;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataSubrange;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataTemplateTypeParameter;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataValue;
import com.oracle.truffle.llvm.runtime.LLVMLogger;

public interface MetadataVisitor {
    default void visit(MetadataBasicType alias) {
        LLVMLogger.info("Ignored Visit to MetadataBasicType: " + alias);
    }

    default void visit(MetadataCompileUnit alias) {
        LLVMLogger.info("Ignored Visit to MetadataCompileUnit: " + alias);
    }

    default void visit(MetadataCompositeType alias) {
        LLVMLogger.info("Ignored Visit to MetadataCompositeType: " + alias);
    }

    default void visit(MetadataDerivedType alias) {
        LLVMLogger.info("Ignored Visit to MetadataDerivedType: " + alias);
    }

    default void visit(MetadataEnumerator alias) {
        LLVMLogger.info("Ignored Visit to MetadataEnumerator: " + alias);
    }

    default void visit(MetadataFile alias) {
        LLVMLogger.info("Ignored Visit to MetadataFile: " + alias);
    }

    default void visit(MetadataFnNode alias) {
        LLVMLogger.info("Ignored Visit to MetadataFnNode: " + alias);
    }

    default void visit(MetadataGlobalVariable alias) {
        LLVMLogger.info("Ignored Visit to MetadataGlobalVariable: " + alias);
    }

    default void visit(MetadataKind alias) {
        LLVMLogger.info("Ignored Visit to MetadataKind: " + alias);
    }

    default void visit(MetadataLexicalBlock alias) {
        LLVMLogger.info("Ignored Visit to MetadataLexicalBlock: " + alias);
    }

    default void visit(MetadataLexicalBlockFile alias) {
        LLVMLogger.info("Ignored Visit to MetadataLexicalBlockFile: " + alias);
    }

    default void visit(MetadataLocalVariable alias) {
        LLVMLogger.info("Ignored Visit to MetadataLocalVariable: " + alias);
    }

    default void visit(MetadataName alias) {
        LLVMLogger.info("Ignored Visit to MetadataName: " + alias);
    }

    default void visit(MetadataNamedNode alias) {
        LLVMLogger.info("Ignored Visit to MetadataNamedNode: " + alias);
    }

    default void visit(MetadataNode alias) {
        LLVMLogger.info("Ignored Visit to MetadataNode: " + alias);
    }

    default void visit(MetadataString alias) {
        LLVMLogger.info("Ignored Visit to MetadataString: " + alias);
    }

    default void visit(MetadataSubprogram alias) {
        LLVMLogger.info("Ignored Visit to MetadataSubprogram: " + alias);
    }

    default void visit(MetadataSubrange alias) {
        LLVMLogger.info("Ignored Visit to MetadataSubrange: " + alias);
    }

    default void visit(MetadataTemplateTypeParameter alias) {
        LLVMLogger.info("Ignored Visit to MetadataTemplateTypeParameter: " + alias);
    }

    default void visit(MetadataValue alias) {
        LLVMLogger.info("Ignored Visit to MetadataValue: " + alias);
    }

    default void visit(MetadataReference alias) {
        LLVMLogger.info("Ignored Visit to MetadataReference: " + alias);
    }
}
