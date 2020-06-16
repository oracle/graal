/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDBasicType;
import com.oracle.truffle.llvm.parser.metadata.MDCommonBlock;
import com.oracle.truffle.llvm.parser.metadata.MDCompositeType;
import com.oracle.truffle.llvm.parser.metadata.MDDerivedType;
import com.oracle.truffle.llvm.parser.metadata.MDEnumerator;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariableExpression;
import com.oracle.truffle.llvm.parser.metadata.MDLabel;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDModule;
import com.oracle.truffle.llvm.parser.metadata.MDNamedNode;
import com.oracle.truffle.llvm.parser.metadata.MDNamespace;
import com.oracle.truffle.llvm.parser.metadata.MDObjCProperty;
import com.oracle.truffle.llvm.parser.metadata.MDString;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.metadata.MDTemplateType;
import com.oracle.truffle.llvm.parser.metadata.MDTemplateTypeParameter;
import com.oracle.truffle.llvm.parser.metadata.MDTemplateValue;

final class MDNameExtractor implements MetadataVisitor {

    private static final String DEFAULT_STRING = "<anonymous>";

    static String getName(MDBaseNode container) {
        if (container == null) {
            return DEFAULT_STRING;
        }

        final MDNameExtractor visitor = new MDNameExtractor();
        container.accept(visitor);
        return visitor.str;
    }

    private MDNameExtractor() {
    }

    private String str = DEFAULT_STRING;

    @Override
    public void visit(MDString md) {
        str = md.getString();
    }

    @Override
    public void visit(MDGlobalVariableExpression mdGVE) {
        mdGVE.getGlobalVariable().accept(this);
    }

    @Override
    public void visit(MDGlobalVariable mdGlobal) {
        mdGlobal.getName().accept(this);
    }

    @Override
    public void visit(MDLocalVariable mdLocal) {
        mdLocal.getName().accept(this);
    }

    @Override
    public void visit(MDEnumerator mdEnumElement) {
        mdEnumElement.getName().accept(this);
    }

    @Override
    public void visit(MDNamespace mdNamespace) {
        mdNamespace.getName().accept(this);
    }

    @Override
    public void visit(MDSubprogram md) {
        md.getName().accept(this);
        if (DEFAULT_STRING.equals(str)) {
            md.getDisplayName().accept(this);
        }
        if (DEFAULT_STRING.equals(str)) {
            md.getLinkageName().accept(this);
        }
    }

    @Override
    public void visit(MDBasicType md) {
        md.getName().accept(this);
    }

    @Override
    public void visit(MDCompositeType md) {
        md.getName().accept(this);
    }

    @Override
    public void visit(MDDerivedType md) {
        md.getName().accept(this);
    }

    @Override
    public void visit(MDModule md) {
        md.getName().accept(this);
    }

    @Override
    public void visit(MDNamedNode md) {
        str = md.getName();
    }

    @Override
    public void visit(MDObjCProperty md) {
        md.getName().accept(this);
    }

    @Override
    public void visit(MDTemplateType md) {
        md.getName().accept(this);
    }

    @Override
    public void visit(MDTemplateTypeParameter md) {
        md.getName().accept(this);
    }

    @Override
    public void visit(MDTemplateValue md) {
        md.getName().accept(this);
    }

    @Override
    public void visit(MDCommonBlock md) {
        md.getName().accept(this);
    }

    @Override
    public void visit(MDLabel md) {
        md.getName().accept(this);
    }
}
