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
package com.oracle.truffle.llvm.runtime.debug.scope;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.metadata.ScopeProvider;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueContainer;

import java.util.HashMap;
import java.util.Map;

public final class LLVMSourceScope extends ScopeProvider.AbstractScope {

    private final Node node;

    public LLVMSourceScope(Node node) {
        this.node = node;
    }

    @Override
    protected String getName() {
        return "<sulong scope>";
    }

    @Override
    protected Node getNode() {
        return node;
    }

    @Override
    @TruffleBoundary
    protected Object getVariables(Frame frame) {
        final Map<Object, LLVMDebugObject> vars = new HashMap<>();
        if (frame != null) {
            for (final FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
                final Object value = frame.getValue(slot);
                if (value instanceof LLVMDebugValueContainer) {
                    final LLVMDebugValueContainer container = (LLVMDebugValueContainer) value;
                    for (final Object identifier : container.getKeys()) {
                        vars.put(identifier, container.getMemberSafe(identifier));
                    }
                }
            }
        }
        return new LLVMSourceScopeVariables(vars);
    }

    @Override
    protected Object getArguments(Frame frame) {
        return null;
    }

    @Override
    protected LLVMSourceScope findParent() {
        return null;
    }
}
