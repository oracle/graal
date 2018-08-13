/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.parser.metadata.MDFile;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDKind;
import com.oracle.truffle.llvm.parser.metadata.MDNamedNode;
import com.oracle.truffle.llvm.parser.metadata.MDNode;
import com.oracle.truffle.llvm.parser.metadata.MDString;
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.metadata.MDVoidNode;
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;

final class MDUpgrade implements MetadataVisitor {

    static void perform(MetadataValueList metadata) {
        final MDNamedNode cuNode = metadata.getNamedNode(MDNamedNode.COMPILEUNIT_NAME);
        if (cuNode == null) {
            return;
        }
        final MDKind dbgKind = metadata.findKind(MDKind.DBG_NAME);
        cuNode.accept(new MDUpgrade(dbgKind));
    }

    private final MDKind dbgKind;

    private MDCompileUnit currentCU;

    private MDUpgrade(MDKind dbgKind) {
        this.dbgKind = dbgKind;
        this.currentCU = null;
    }

    @Override
    public void visit(MDCompileUnit md) {
        currentCU = md;

        if (md.getFile() instanceof MDString) {
            final MDFile fileRef = MDFile.create(md.getFile(), md.getDirectory());
            md.replace(md.getFile(), fileRef);
        }

        md.getSubprograms().accept(this);
        md.getGlobalVariables().accept(this);
        currentCU = null;
    }

    @Override
    public void visit(MDNode md) {
        for (MDBaseNode elt : md) {
            elt.accept(this);
        }
    }

    @Override
    public void visit(MDNamedNode md) {
        for (MDBaseNode elt : md) {
            elt.accept(this);
        }
    }

    @Override
    public void visit(MDSubprogram md) {
        final SymbolImpl valueSymbol = MDSymbolExtractor.getSymbol(md.getFunction());
        if (valueSymbol instanceof FunctionDefinition) {
            final FunctionDefinition function = (FunctionDefinition) valueSymbol;
            attachSymbol(function, md);
        }
        if (currentCU != null && md.getCompileUnit() == MDVoidNode.INSTANCE) {
            md.setCompileUnit(currentCU);
        }
    }

    @Override
    public void visit(MDGlobalVariable mdGlobal) {
        final SymbolImpl symbol = MDSymbolExtractor.getSymbol(mdGlobal.getVariable());
        if (symbol instanceof GlobalValueSymbol) {
            final GlobalValueSymbol global = (GlobalValueSymbol) symbol;
            attachSymbol(global, mdGlobal);
        }
        if (currentCU != null) {
            mdGlobal.setCompileUnit(currentCU);
        }
    }

    private void attachSymbol(MetadataAttachmentHolder container, MDBaseNode ref) {
        if (!container.hasAttachedMetadata() || container.getMetadataAttachment(MDKind.DBG_NAME) == null) {
            final MDAttachment dbg = MDAttachment.create(dbgKind, ref);
            container.attachMetadata(dbg);
        }
    }
}
