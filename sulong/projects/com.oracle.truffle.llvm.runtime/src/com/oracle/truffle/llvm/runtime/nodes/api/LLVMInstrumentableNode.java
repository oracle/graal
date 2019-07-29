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

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;

public interface LLVMInstrumentableNode extends InstrumentableNode {

    void setSourceDescriptor(LLVMNodeSourceDescriptor sourceDescriptor);

    /**
     * Get a {@link LLVMNodeSourceDescriptor descriptor} for the debug and instrumentation
     * properties of this node.
     *
     * @return a source descriptor attached to this node
     */
    LLVMNodeSourceDescriptor getSourceDescriptor();

    /**
     * Get a {@link LLVMNodeSourceDescriptor descriptor} for the debug and instrumentation
     * properties of this node. If no such descriptor is currently attached to this node, one will
     * be created.
     *
     * @return a source descriptor attached to this node
     */
    LLVMNodeSourceDescriptor getOrCreateSourceDescriptor();

    @Override
    default boolean isInstrumentable() {
        final LLVMNodeSourceDescriptor sourceDescriptor = getSourceDescriptor();
        if (sourceDescriptor == null) {
            return false;
        }

        return sourceDescriptor.getSourceSection() != null;
    }

    /**
     * If this node provides a non-null
     * {@link com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation}, describes whether
     * this node represents a source-level function root.
     *
     * @return whether this node should have the
     *         {@link com.oracle.truffle.api.instrumentation.StandardTags.RootTag}
     */
    default boolean hasRootTag() {
        return false;
    }

    /**
     * If this node provides a non-null
     * {@link com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation}, describes whether
     * this node represents a source-level function root.
     *
     * @return whether this node should have the
     *         {@link com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag}
     */
    default boolean hasRootBodyTag() {
        return false;
    }

    /**
     * If this node provides a non-null
     * {@link com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation}, describes whether
     * this node represents a source-level function call.
     *
     * @return whether this node should have the
     *         {@link com.oracle.truffle.api.instrumentation.StandardTags.CallTag}
     */
    default boolean hasCallTag() {
        return false;
    }

    /**
     * If this node provides a non-null
     * {@link com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation}, describes whether
     * this node represents a source-level statement.
     *
     * @return whether this node should have the
     *         {@link com.oracle.truffle.api.instrumentation.StandardTags.StatementTag}
     */
    default boolean hasStatementTag() {
        return false;
    }

    @Override
    default boolean hasTag(Class<? extends Tag> tag) {
        final LLVMNodeSourceDescriptor sourceDescriptor = getSourceDescriptor();
        if (sourceDescriptor == null) {
            return false;
        }

        // only and all nodes with attached source locations are eligible for source-level
        // instrumentation
        if ((tag == StandardTags.StatementTag.class && hasStatementTag()) || (tag == StandardTags.CallTag.class && hasCallTag()) || (tag == StandardTags.RootTag.class && hasRootTag()) ||
                        (tag == StandardTags.RootBodyTag.class && hasRootBodyTag())) {
            return sourceDescriptor.getSourceLocation() != null;
        }

        return sourceDescriptor.hasTag(tag);
    }

    @Override
    default Object getNodeObject() {
        final LLVMNodeSourceDescriptor sourceDescriptor = getSourceDescriptor();
        return sourceDescriptor != null ? sourceDescriptor.getNodeObject() : null;
    }
}
