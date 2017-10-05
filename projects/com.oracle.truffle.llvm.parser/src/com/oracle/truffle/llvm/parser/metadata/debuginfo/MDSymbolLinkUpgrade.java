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
import com.oracle.truffle.llvm.parser.metadata.MDKind;
import com.oracle.truffle.llvm.parser.metadata.MDNode;
import com.oracle.truffle.llvm.parser.metadata.MDOldNode;
import com.oracle.truffle.llvm.parser.metadata.MDReference;
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.metadata.MDTypedValue;
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.metadata.MetadataList;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

import java.util.ArrayList;
import java.util.List;

final class MDSymbolLinkUpgrade implements MDFollowRefVisitor {

    static void perform(MetadataList metadata) {
        metadata.accept(new MDSymbolLinkUpgrade(metadata));
    }

    private final MetadataList metadata;
    private final List<MDBaseNode> visited;

    private MDCompileUnit currentCU;

    private MDSymbolLinkUpgrade(MetadataList metadata) {
        this.metadata = metadata;
        this.visited = new ArrayList<>(metadata.size());
        this.currentCU = null;
    }

    @Override
    public void visit(MDCompileUnit md) {
        currentCU = md;
        md.getSubprograms().accept(this);
        currentCU = null;
    }

    @Override
    public void visit(MDNode md) {
        if (visited.contains(md)) {
            return;
        } else {
            visited.add(md);
        }

        for (MDReference elt : md) {
            elt.accept(this);
        }
    }

    @Override
    public void visit(MDOldNode md) {
        if (visited.contains(md)) {
            return;
        } else {
            visited.add(md);
        }

        for (MDTypedValue elt : md) {
            if (elt instanceof MDReference) {
                ((MDReference) elt).accept(this);
            }
        }
    }

    @Override
    public void visit(MDSubprogram md) {
        if (visited.contains(md)) {
            return;
        } else {
            visited.add(md);
        }

        final Symbol valueSymbol = MDSymbolExtractor.getSymbol(md.getFunction());
        if (valueSymbol instanceof FunctionDefinition) {
            final FunctionDefinition function = (FunctionDefinition) valueSymbol;
            attachSymbol(function, md);
        }
        if (currentCU != null && md.getCompileUnit() != MDReference.VOID) {
            md.setCompileUnit(MDReference.fromNode(md));
        }
    }

    @Override
    public void visit(MDGlobalVariable mdGlobal) {
        if (visited.contains(mdGlobal)) {
            return;
        } else {
            visited.add(mdGlobal);
        }

        final Symbol symbol = MDSymbolExtractor.getSymbol(mdGlobal.getVariable());
        if (symbol instanceof GlobalValueSymbol) {
            final GlobalValueSymbol global = (GlobalValueSymbol) symbol;
            attachSymbol(global, mdGlobal);
        }
    }

    private void attachSymbol(MetadataAttachmentHolder container, MDBaseNode ref) {
        if (!container.hasAttachedMetadata() || container.getMetadataAttachment(MDKind.DBG_NAME) == null) {
            final MDKind dbgKind = metadata.getKind(MDKind.DBG_NAME);
            final MDAttachment dbg = new MDAttachment(dbgKind, MDReference.fromNode(ref));
            container.attachMetadata(dbg);
        }
    }
}
