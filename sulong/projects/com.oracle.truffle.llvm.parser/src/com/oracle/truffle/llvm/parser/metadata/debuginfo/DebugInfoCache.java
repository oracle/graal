/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDKind;
import com.oracle.truffle.llvm.parser.metadata.MetadataAttachmentHolder;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceStaticMemberType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

final class DebugInfoCache {

    static MDBaseNode getDebugInfo(MetadataAttachmentHolder holder) {
        if (holder.hasAttachedMetadata()) {
            return holder.getMetadataAttachment(MDKind.DBG_NAME);

        } else {
            return null;
        }
    }

    private final Map<MDBaseNode, LLVMSourceSymbol> parsedVariables;
    private final DIScopeBuilder scopeBuilder;
    private final DITypeExtractor typeExtractor;

    DebugInfoCache(MetadataValueList metadata, Map<LLVMSourceStaticMemberType, SymbolImpl> staticMembers, LLVMContext context) {
        this.parsedVariables = new HashMap<>();
        this.scopeBuilder = new DIScopeBuilder(metadata, context);
        this.typeExtractor = new DITypeExtractor(scopeBuilder, metadata, staticMembers);
    }

    LLVMSourceSymbol getSourceSymbol(MDBaseNode mdVariable, boolean isStatic) {
        LLVMSourceSymbol lookup = parsedVariables.get(mdVariable);
        if (lookup != null) {
            return lookup;
        }

        LLVMSourceLocation location = scopeBuilder.buildLocation(mdVariable);
        final LLVMSourceType type = typeExtractor.parseType(mdVariable);
        final String varName = MDNameExtractor.getName(mdVariable);

        final LLVMSourceSymbol symbol = LLVMSourceSymbol.create(varName, location, type, isStatic);
        parsedVariables.put(mdVariable, symbol);

        if (location != null) {
            // this is currently the line/column where the symbol was declared, we want the
            // scope
            location = location.getParent();
        }

        if (location != null) {
            location.addSymbol(symbol);
        }

        return symbol;
    }

    LLVMSourceLocation buildLocation(MDBaseNode node) {
        return scopeBuilder.buildLocation(node);
    }

    LLVMSourceType parseType(MDBaseNode mdType) {
        return typeExtractor.parseType(mdType);
    }

    void endLocalScope() {
        scopeBuilder.clearLocalScopes();
    }

    void importScope(MDBaseNode node, LLVMSourceLocation importedScope) {
        scopeBuilder.importScope(node, importedScope);
    }

}
