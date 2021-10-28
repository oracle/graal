/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.truffle.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.stack.InspectedFrame;

/**
 * Represents a Truffle {@link com.oracle.truffle.api.frame.FrameInstance} where OSR occurred.
 *
 * Contains a separate field for the {@link InspectedFrame} containing the most up-to-date Frame.
 */
public final class GraalOSRFrameInstance extends GraalFrameInstance {
    private final InspectedFrame osrFrame;

    GraalOSRFrameInstance(InspectedFrame callTargetFrame, InspectedFrame callNodeFrame, InspectedFrame osrFrame) {
        super(callTargetFrame, callNodeFrame);
        this.osrFrame = osrFrame;
    }

    @TruffleBoundary
    @Override
    public Frame getFrame(FrameAccess access) {
        Frame materializedOSRFrame = getFrameFrom(osrFrame, access);
        if (getOSRRootNode() instanceof OptimizedOSRLoopNode.LoopOSRRootNode) {
            return (Frame) materializedOSRFrame.getArguments()[0];
        }
        return materializedOSRFrame;
    }

    private RootNode getOSRRootNode() {
        return ((OptimizedCallTarget) osrFrame.getLocal(GraalFrameInstance.CALL_TARGET_INDEX)).getRootNode();
    }

    @TruffleBoundary
    @Override
    public boolean isVirtualFrame() {
        return osrFrame.isVirtual(FRAME_INDEX);
    }

    @TruffleBoundary
    @Override
    public int getCompilationTier() {
        return ((CompilationState) osrFrame.getLocal(OPTIMIZATION_TIER_FRAME_INDEX)).getTier();
    }

    @TruffleBoundary
    @Override
    public boolean isCompilationRoot() {
        return ((CompilationState) osrFrame.getLocal(OPTIMIZATION_TIER_FRAME_INDEX)).isCompilationRoot();
    }
}
