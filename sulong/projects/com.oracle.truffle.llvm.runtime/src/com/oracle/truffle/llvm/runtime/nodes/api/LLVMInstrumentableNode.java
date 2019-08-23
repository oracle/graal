/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.api;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

public abstract class LLVMInstrumentableNode extends LLVMNode implements InstrumentableNode {

    @CompilationFinal private LLVMNodeSourceDescriptor sourceDescriptor = null;

    /**
     * Get a {@link LLVMNodeSourceDescriptor descriptor} for the debug and instrumentation
     * properties of this node.
     *
     * @return a source descriptor attached to this node
     */
    public final LLVMNodeSourceDescriptor getSourceDescriptor() {
        return sourceDescriptor;
    }

    /**
     * Get a {@link LLVMNodeSourceDescriptor descriptor} for the debug and instrumentation
     * properties of this node. If no such descriptor is currently attached to this node, one will
     * be created.
     *
     * @return a source descriptor attached to this node
     */
    public final LLVMNodeSourceDescriptor getOrCreateSourceDescriptor() {
        if (sourceDescriptor == null) {
            setSourceDescriptor(new LLVMNodeSourceDescriptor());
        }
        return sourceDescriptor;
    }

    public final void setSourceDescriptor(LLVMNodeSourceDescriptor sourceDescriptor) {
        // the source descriptor should only be set in the parser, and should only be modified
        // before this node is first executed
        CompilerAsserts.neverPartOfCompilation();
        this.sourceDescriptor = sourceDescriptor;
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceDescriptor != null ? sourceDescriptor.getSourceSection() : null;
    }

    @Override
    public boolean isInstrumentable() {
        return getSourceSection() != null;
    }

    /**
     * Describes whether this node has source-level debug information attached and should be
     * considered a source-level statement for instrumentation.
     *
     * @return whether this node may provide the
     *         {@link com.oracle.truffle.api.instrumentation.StandardTags.StatementTag}
     */
    private boolean hasStatementTag() {
        return sourceDescriptor != null && sourceDescriptor.hasStatementTag();
    }

    /**
     * Get a {@link LLVMSourceLocation descriptor} for the source-level code location and scope
     * information of this node.
     *
     * @return the {@link LLVMSourceLocation} attached to this node
     */
    public LLVMSourceLocation getSourceLocation() {
        return sourceDescriptor != null ? sourceDescriptor.getSourceLocation() : null;
    }

    /**
     * If this node {@link LLVMInstrumentableNode#hasStatementTag() is a statement for source-level
     * instrumentatipon}, this function considers the node to be tagged with
     * {@link com.oracle.truffle.api.instrumentation.StandardTags.StatementTag}.
     *
     * @param tag class of a tag {@link com.oracle.truffle.api.instrumentation.ProvidedTags
     *            provided} by {@link com.oracle.truffle.llvm.runtime.LLVMLanguage}
     *
     * @return whether this node is associated with the given tag
     */
    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == StandardTags.StatementTag.class) {
            return hasStatementTag();
        } else {
            return false;
        }
    }
}
