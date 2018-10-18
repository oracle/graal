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
package com.oracle.truffle.llvm.nodes.func;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.base.LLVMFrameNullerUtil;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class LLVMFunctionStartNode extends RootNode {

    private static final FrameSlot[] NO_SLOTS = new FrameSlot[0];

    @Child private LLVMExpressionNode node;
    @CompilationFinal(dimensions = 1) private final FrameSlot[] frameSlotsToInitialize;
    private final String name;
    private final int explicitArgumentsCount;
    private final DebugInformation debugInformation;

    public LLVMFunctionStartNode(LLVMLanguage language, LLVMExpressionNode node, FrameDescriptor frameDescriptor, String name, int explicitArgumentsCount, String originalName, Source bcSource,
                    LLVMSourceLocation location) {
        super(language, frameDescriptor);
        this.debugInformation = new DebugInformation(originalName, bcSource, location);
        this.explicitArgumentsCount = explicitArgumentsCount;
        this.node = node;
        this.name = name;
        this.frameSlotsToInitialize = frameDescriptor.getSlots().toArray(NO_SLOTS);
    }

    @Override
    public SourceSection getSourceSection() {
        return debugInformation.sourceLocation.getSourceSection();
    }

    @Override
    public boolean isInternal() {
        return debugInformation.bcSource.isInternal();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        nullStack(frame);
        Object result = node.executeGeneric(frame);
        return result;
    }

    @ExplodeLoop
    private void nullStack(VirtualFrame frame) {
        for (FrameSlot frameSlot : frameSlotsToInitialize) {
            // In LLVM IR, it is possible that SSA values are used *before* they are defined (so
            // far, we only saw this for @llvm.dbg.value tail calls). So, even in such a case, it
            // must be possible to read from the frame in a typed way.
            LLVMFrameNullerUtil.nullFrameSlot(frame, frameSlot, true);
        }
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public String getName() {
        if (debugInformation.originalName != null) {
            return debugInformation.originalName;
        }
        return name;
    }

    public int getExplicitArgumentsCount() {
        return explicitArgumentsCount;
    }

    public String getOriginalName() {
        return debugInformation.originalName;
    }

    public String getBcName() {
        return name;
    }

    public Source getBcSource() {
        return debugInformation.bcSource;
    }

    @Override
    @TruffleBoundary
    public Map<String, Object> getDebugProperties() {
        final HashMap<String, Object> properties = new HashMap<>();
        if (debugInformation.originalName != null) {
            properties.put("originalName", debugInformation.originalName);
        }
        if (debugInformation.bcSource != null) {
            properties.put("bcSource", debugInformation.bcSource);
        }
        if (debugInformation.sourceLocation != null) {
            properties.put("sourceLocation", debugInformation.sourceLocation);
        }
        return properties;
    }

    /*
     * Encapsulation of these 4 objects keeps memory footprint low in case no debug info is
     * available.
     */
    private static final class DebugInformation {
        private final String originalName;
        private final Source bcSource;
        private final LLVMSourceLocation sourceLocation;

        DebugInformation(String originalName, Source bcSource, LLVMSourceLocation sourceLocation) {
            this.originalName = originalName;
            this.bcSource = bcSource;
            this.sourceLocation = sourceLocation;
        }
    }
}
