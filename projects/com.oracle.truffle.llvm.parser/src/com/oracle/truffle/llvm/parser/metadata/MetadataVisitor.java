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
package com.oracle.truffle.llvm.parser.metadata;

import com.oracle.truffle.llvm.runtime.LLVMLogger;

public interface MetadataVisitor {
    /**
     * We normally don't need to implement all visitors, but want to have a default implementation
     * for those visitors which are not handled explicitly. This little method allows us to do so.
     */
    default void ifVisitNotOverwritten(MDBaseNode alias) {
        LLVMLogger.info(String.format("Ignored Visit to %s: %s", alias.getClass().getSimpleName(), alias));
    }

    default void visit(MDAttachment alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDBasicType alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDCompileUnit alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDCompositeType alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDDerivedType alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDEmptyNode alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDEnumerator alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDExpression alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDFile alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDFnNode alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDGenericDebug alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDGlobalVariable alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDImportedEntity alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDKind alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDLexicalBlock alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDLexicalBlockFile alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDLocalVariable alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDMacro alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDMacroFile alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDModule alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDNamedNode alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDNamespace alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDNode alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDObjCProperty alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDOldNode alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDString alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDSubprogram alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDSubrange alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDSubroutine alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDSymbolReference alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDTemplateType alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDTemplateTypeParameter alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDTemplateValue alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDValue alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDReference alias) {
        ifVisitNotOverwritten(alias);
    }

    default void visit(MDLocation alias) {
        ifVisitNotOverwritten(alias);
    }
}
