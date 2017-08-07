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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueContainerType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugType;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

import java.util.HashMap;
import java.util.Map;

@NodeChild(value = "accessor", type = LLVMExpressionNode.class)
public abstract class LLVMDebugDeclaration extends LLVMExpressionNode {

    /** Contains the object that stores the source-level variables. */
    private final FrameSlot sourceValuesContainerSlot;
    private final String varName;
    private final LLVMDebugType varType;

    public LLVMDebugDeclaration(String varName, LLVMDebugType varType, FrameSlot sourceValuesContainerSlot) {
        this.sourceValuesContainerSlot = sourceValuesContainerSlot;
        this.varName = varName;
        this.varType = varType;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return null;
    }

    @Specialization
    public Object readAddress(VirtualFrame frame, LLVMAddress address) {
        final Object debugSlotVal = frame.getValue(sourceValuesContainerSlot);
        DynamicObject debugObj;

        if (debugSlotVal instanceof DynamicObject) {
            debugObj = (DynamicObject) debugSlotVal;

        } else {
            debugObj = LLVMDebugValueContainerType.createContainer();
            frame.setObject(sourceValuesContainerSlot, debugObj);
        }

        final LLVMDebugObject object = instantiate(varType, 0L, new LLVMAddressValueProvider(address));
        debugObj.define(varName, object);
        return null;
    }

    @Specialization
    public Object readGlobal(VirtualFrame frame, LLVMGlobalVariable global, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        final Object debugSlotVal = frame.getValue(sourceValuesContainerSlot);
        DynamicObject debugObj;

        if (debugSlotVal instanceof DynamicObject) {
            debugObj = (DynamicObject) debugSlotVal;

        } else {
            debugObj = LLVMDebugValueContainerType.createContainer();
            frame.setObject(sourceValuesContainerSlot, debugObj);
        }

        debugObj = LLVMDebugValueContainerType.findOrAddGlobalsContainer(debugObj);

        final LLVMAddress address = globalAccess.getNativeLocation(global);
        final LLVMDebugObject object = instantiate(varType, 0L, new LLVMAddressValueProvider(address));
        debugObj.define(varName, object);
        return null;
    }

    @TruffleBoundary
    private LLVMDebugObject instantiate(LLVMDebugType type, long baseOffset, LLVMDebugValueProvider value) {
        if (type.isAggregate()) {

            final Map<Object, LLVMDebugObject> members = new HashMap<>(type.getElementCount());
            final Object[] memberIdentifiers = new Object[type.getElementCount()];
            for (int i = 0; i < type.getElementCount(); i++) {
                final LLVMDebugType elementType = type.getElementType(i);
                final String elementName = type.getElementName(i);
                final long newOffset = baseOffset + elementType.getOffset();

                final LLVMDebugObject member = instantiate(elementType, newOffset, value);
                memberIdentifiers[i] = elementName;
                members.put(elementName, member);
            }
            return new LLVMDebugObjectImpl.Structured(value, baseOffset, type, memberIdentifiers, members);

        } else if (type.isEnum()) {
            return new LLVMDebugObjectImpl.Enum(value, baseOffset, type);

        } else {
            return new LLVMDebugObjectImpl.Primitive(value, baseOffset, type);
        }
    }
}
