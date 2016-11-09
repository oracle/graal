package com.oracle.truffle.llvm.parser.base.model.visitors;

import com.oracle.truffle.llvm.parser.base.model.blocks.MetadataBlock.MetadataReference;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataBaseNode;
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
    /**
     * We normally don't need to implement all visitors, but want to have a default implementation
     * for those visitors which are not handled explicitly. This little method allows us to do so.
     */
    default void ifVisitNotOverwritten(MetadataBaseNode alias) {
        LLVMLogger.info("Ignored Visit to " + alias.getClass().getSimpleName() + ": " + alias);
    }

    default void visit(MetadataBasicType alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataCompileUnit alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataCompositeType alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataDerivedType alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataEnumerator alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataFile alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataFnNode alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataGlobalVariable alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataKind alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataLexicalBlock alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataLexicalBlockFile alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataLocalVariable alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataName alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataNamedNode alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataNode alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataString alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataSubprogram alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataSubrange alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataTemplateTypeParameter alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataValue alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MetadataReference alias) {
        ifVisitNotOverwritten(alias);
    }
}
