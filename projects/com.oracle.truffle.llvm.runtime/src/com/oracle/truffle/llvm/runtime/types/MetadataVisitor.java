/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.types;

import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBaseNode;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBasicType;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBlock.MetadataReference;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataCompileUnit;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataCompositeType;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataDerivedType;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataEnumerator;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataFile;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataFnNode;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataGlobalVariable;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataKind;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataLexicalBlock;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataLexicalBlockFile;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataLocalVariable;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataName;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataNamedNode;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataNode;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataString;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataSubprogram;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataSubrange;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataTemplateTypeParameter;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataValue;

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
