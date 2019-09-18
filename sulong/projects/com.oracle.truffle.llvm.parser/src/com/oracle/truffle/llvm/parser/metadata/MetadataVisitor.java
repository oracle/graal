/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.ValueList;

public interface MetadataVisitor extends ValueList.ValueVisitor<MDBaseNode> {

    default void visit(MDAttachment md) {
        defaultAction(md);
    }

    default void visit(MDBasicType md) {
        defaultAction(md);
    }

    default void visit(MDCompileUnit md) {
        defaultAction(md);
    }

    default void visit(MDCompositeType md) {
        defaultAction(md);
    }

    default void visit(MDDerivedType md) {
        defaultAction(md);
    }

    default void visit(MDEnumerator md) {
        defaultAction(md);
    }

    default void visit(MDExpression md) {
        defaultAction(md);
    }

    default void visit(MDFile md) {
        defaultAction(md);
    }

    default void visit(MDGenericDebug md) {
        defaultAction(md);
    }

    default void visit(MDGlobalVariable md) {
        defaultAction(md);
    }

    default void visit(MDImportedEntity md) {
        defaultAction(md);
    }

    default void visit(MDKind md) {
        defaultAction(md);
    }

    default void visit(MDLexicalBlock md) {
        defaultAction(md);
    }

    default void visit(MDLexicalBlockFile md) {
        defaultAction(md);
    }

    default void visit(MDLocalVariable md) {
        defaultAction(md);
    }

    default void visit(MDMacro md) {
        defaultAction(md);
    }

    default void visit(MDMacroFile md) {
        defaultAction(md);
    }

    default void visit(MDModule md) {
        defaultAction(md);
    }

    default void visit(MDNamedNode md) {
        defaultAction(md);
    }

    default void visit(MDNamespace md) {
        defaultAction(md);
    }

    default void visit(MDNode md) {
        defaultAction(md);
    }

    default void visit(MDObjCProperty md) {
        defaultAction(md);
    }

    default void visit(MDString md) {
        defaultAction(md);
    }

    default void visit(MDSubprogram md) {
        defaultAction(md);
    }

    default void visit(MDSubrange md) {
        defaultAction(md);
    }

    default void visit(MDSubroutine md) {
        defaultAction(md);
    }

    default void visit(MDTemplateType md) {
        defaultAction(md);
    }

    default void visit(MDTemplateTypeParameter md) {
        defaultAction(md);
    }

    default void visit(MDTemplateValue md) {
        defaultAction(md);
    }

    default void visit(MDValue md) {
        defaultAction(md);
    }

    default void visit(MDLocation md) {
        defaultAction(md);
    }

    default void visit(MDGlobalVariableExpression md) {
        defaultAction(md);
    }

    default void visit(MDVoidNode md) {
        defaultAction(md);
    }

    default void visit(MDCommonBlock md) {
        defaultAction(md);
    }

    default void visit(MDLabel md) {
        defaultAction(md);
    }
}
