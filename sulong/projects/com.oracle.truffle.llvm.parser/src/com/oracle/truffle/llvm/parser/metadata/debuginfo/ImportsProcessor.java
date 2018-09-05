/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.parser.metadata.MDModule;
import com.oracle.truffle.llvm.parser.metadata.MDNamespace;
import com.oracle.truffle.llvm.parser.metadata.MDString;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

final class ImportsProcessor implements MetadataVisitor {

    static void process(MetadataValueList metadata, LLVMContext context, DebugInfoCache cache) {
        metadata.consumeExportedScopes(new ImportsProcessor(context, cache));
    }

    private final LLVMContext context;
    private final DebugInfoCache cache;

    private ImportsProcessor(LLVMContext context, DebugInfoCache cache) {
        this.context = context;
        this.cache = cache;
    }

    @Override
    public void visit(MDModule md) {
        importScope(md, md.getName());
    }

    @Override
    public void visit(MDNamespace md) {
        importScope(md, md.getName());
    }

    private void importScope(MDBaseNode scopeNode, MDBaseNode scopeName) {
        final String name = MDString.getIfInstance(scopeName);
        if (name == null) {
            return;
        }

        final LLVMSourceLocation importedScope = context.getSourceContext().getExportedScope(name);
        if (importedScope != null) {
            cache.importScope(scopeNode, importedScope);
        } else {
            final LLVMSourceLocation exportableScope = cache.buildLocation(scopeNode);
            context.getSourceContext().exportScope(name, exportableScope);
        }
    }

}
