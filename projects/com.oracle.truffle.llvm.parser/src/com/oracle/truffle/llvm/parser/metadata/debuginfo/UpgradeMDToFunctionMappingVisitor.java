/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.metadata.MDAttachment;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDCompileUnit;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariableExpression;
import com.oracle.truffle.llvm.parser.metadata.MDKind;
import com.oracle.truffle.llvm.parser.metadata.MDNamedNode;
import com.oracle.truffle.llvm.parser.metadata.MDOldNode;
import com.oracle.truffle.llvm.parser.metadata.MDReference;
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.metadata.MDSymbolReference;
import com.oracle.truffle.llvm.parser.metadata.MDTypedValue;
import com.oracle.truffle.llvm.parser.metadata.MDValue;
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.metadata.MetadataList;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

final class UpgradeMDToFunctionMappingVisitor implements MDFollowRefVisitor {

    static void upgrade(MetadataList metadata) {
        metadata.accept(new UpgradeMDToFunctionMappingVisitor(metadata));
    }

    private final MetadataList metadata;

    private UpgradeMDToFunctionMappingVisitor(MetadataList metadata) {
        this.metadata = metadata;
    }

    @Override
    public void visit(MDCompileUnit md) {
        md.getSubprograms().accept(this);
        md.getGlobalVariables().accept(this);
    }

    @Override
    public void visit(MDNamedNode md) {
        for (MDReference ref : md) {
            ref.accept(this);
        }
    }

    @Override
    public void visit(MDOldNode md) {
        for (MDTypedValue value : md) {
            if (value instanceof MDReference) {
                ((MDReference) value).accept(this);
            }
        }
    }

    @Override
    public void visit(MDSubprogram md) {
        final MDReference valueRef = md.getFunction();
        if (valueRef == MDReference.VOID) {
            return;
        }

        final MDBaseNode valueNode = valueRef.get();
        if (valueNode instanceof MDValue) {
            final MDSymbolReference value = ((MDValue) valueNode).getValue();
            if (value.isPresent()) {
                final Symbol valueSymbol = value.get();
                if (valueSymbol instanceof FunctionDefinition) {
                    attachSymbol((FunctionDefinition) valueSymbol, md);
                }
            }
        }
    }

    @Override
    public void visit(MDGlobalVariable mdGlobal) {
        final Symbol symbol = MDSymbolExtractor.getSymbol(mdGlobal.getVariable());
        if (symbol instanceof GlobalValueSymbol) {
            final GlobalValueSymbol global = (GlobalValueSymbol) symbol;
            attachSymbol(global, mdGlobal);
        }

    }

    @Override
    public void visit(MDGlobalVariableExpression md) {
        md.getGlobalVariable().accept(this);
    }

    private void attachSymbol(MetadataAttachmentHolder container, MDBaseNode ref) {
        if (!container.hasAttachedMetadata() || container.getMetadataAttachment(MDKind.DBG_NAME) == null) {
            final MDKind dbgKind = metadata.getKind(MDKind.DBG_NAME);
            final MDAttachment dbg = new MDAttachment(dbgKind, MDReference.fromNode(ref));
            container.attachMetadata(dbg);
        }
    }
}
