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

public interface MetadataVisitor {
    /**
     * We normally don't need to implement all visitors, but want to have a default implementation
     * for those visitors which are not handled explicitly. This little method allows us to do so.
     */
    default void ifVisitNotOverwritten(@SuppressWarnings("unused") MDBaseNode md) {
    }

    default void visit(MDAttachment md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDBasicType md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDCompileUnit md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDCompositeType md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDDerivedType md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDEmptyNode md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDEnumerator md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDExpression md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDFile md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDGenericDebug md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDGlobalVariable md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDImportedEntity md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDKind md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDLexicalBlock md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDLexicalBlockFile md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDLocalVariable md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDMacro md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDMacroFile md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDModule md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDNamedNode md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDNamespace md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDNode md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDObjCProperty md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDString md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDSubprogram md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDSubrange md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDSubroutine md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDSymbolReference md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDTemplateType md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDTemplateTypeParameter md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDTemplateValue md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDValue md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDReference md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDLocation md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDGlobalVariableExpression md) {
        ifVisitNotOverwritten(md);
    }

    default void visit(MDVoidNode md) {
        ifVisitNotOverwritten(md);
    }
}
