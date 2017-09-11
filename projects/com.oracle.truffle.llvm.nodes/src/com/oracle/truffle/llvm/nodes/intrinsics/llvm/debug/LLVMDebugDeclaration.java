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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueContainer;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild(value = "container", type = LLVMExpressionNode.class), @NodeChild(value = "value", type = LLVMExpressionNode.class)})
public abstract class LLVMDebugDeclaration extends LLVMExpressionNode {

    private final String varName;
    private final LLVMSourceType varType;

    private final FrameSlot containerSlot;

    public LLVMDebugDeclaration(String varName, LLVMSourceType varType, FrameSlot containerSlot) {
        this.varName = varName;
        this.varType = varType;
        this.containerSlot = containerSlot;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return null;
    }

    @Specialization
    public Object readAddress(LLVMDebugValueContainer container, LLVMAddress address) {
        final LLVMDebugObject object = LLVMDebugObject.instantiate(varType, 0L, new LLVMAllocationValueProvider(address));
        container.addMember(varName, object);
        return null;
    }

    @Specialization
    public Object readGlobal(LLVMDebugValueContainer container, LLVMGlobalVariable global) {
        final LLVMDebugObject object = LLVMDebugObject.instantiate(varType, 0L, new LLVMConstantGlobalValueProvider(global));
        final LLVMDebugValueContainer globalsContainer = LLVMDebugValueContainer.findOrAddGlobalsContainer(container);
        globalsContainer.addMember(varName, object);
        return null;
    }

    @Specialization
    public Object initializeOnAddress(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMAddress address) {
        final LLVMDebugValueContainer container = LLVMDebugValueContainer.createContainer();
        frame.setObject(containerSlot, container);
        return readAddress(container, address);
    }

    @Specialization
    public Object initializeOnGlobal(VirtualFrame frame, @SuppressWarnings("unused") Object defaultValue, LLVMGlobalVariable global) {
        final LLVMDebugValueContainer container = LLVMDebugValueContainer.createContainer();
        frame.setObject(containerSlot, container);
        return readGlobal(container, global);
    }
}
