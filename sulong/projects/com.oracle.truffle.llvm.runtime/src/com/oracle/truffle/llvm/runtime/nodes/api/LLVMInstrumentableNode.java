/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

@NodeField(name = "sourceLocation", type = LLVMSourceLocation.class)
@NodeField(name = "statement", type = boolean.class)
public abstract class LLVMInstrumentableNode extends LLVMNode implements InstrumentableNode {

    /**
     * Get a {@link LLVMSourceLocation descriptor} for the source-level code location and scope
     * information of this node.
     *
     * @return the {@link LLVMSourceLocation} attached to this node
     */
    public abstract LLVMSourceLocation getSourceLocation();

    public abstract void setSourceLocation(LLVMSourceLocation sourceLocation);

    @Override
    public final SourceSection getSourceSection() {
        LLVMSourceLocation sourceLocation = getSourceLocation();
        return sourceLocation == null ? null : sourceLocation.getSourceSection();
    }

    protected abstract boolean isStatement();

    protected abstract void setStatement(boolean statementTag);

    /**
     * Describes whether this node has source-level debug information attached and should be
     * considered a source-level statement for instrumentation.
     *
     * @return whether this node may provide the
     *         {@link com.oracle.truffle.api.instrumentation.StandardTags.StatementTag}
     */
    public boolean hasStatementTag() {
        return isStatement() && getSourceLocation() != null;
    }

    public void setHasStatementTag(boolean b) {
        CompilerAsserts.neverPartOfCompilation();
        setStatement(b);
    }

    @Override
    public final boolean isInstrumentable() {
        return getSourceLocation() != null;
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
