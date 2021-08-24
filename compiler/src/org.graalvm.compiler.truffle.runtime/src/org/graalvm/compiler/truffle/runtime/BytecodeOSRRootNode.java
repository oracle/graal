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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;

final class BytecodeOSRRootNode extends BaseOSRRootNode {
    @Child private BytecodeOSRNode bytecodeOSRNode;
    private final int target;
    private final Object interpreterState;
    @CompilationFinal private boolean seenMaterializedFrame;

    BytecodeOSRRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, BytecodeOSRNode bytecodeOSRNode, int target, Object interpreterState) {
        super(language, frameDescriptor);
        this.bytecodeOSRNode = bytecodeOSRNode;
        this.target = target;
        this.interpreterState = interpreterState;
        this.seenMaterializedFrame = materializeCalled(frameDescriptor);
    }

    private static boolean materializeCalled(FrameDescriptor frameDescriptor) {
        return ((GraalTruffleRuntime) Truffle.getRuntime()).getFrameMaterializeCalled(frameDescriptor);
    }

    @Override
    public Object executeOSR(VirtualFrame frame) {
        VirtualFrame parentFrame = (VirtualFrame) frame.getArguments()[0];

        if (!seenMaterializedFrame) {
            // We aren't expecting a materialized frame. If we get one, deoptimize.
            if (materializeCalled(parentFrame.getFrameDescriptor())) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenMaterializedFrame = true;
            }
        }

        if (seenMaterializedFrame) {
            // If materialize has ever happened, just use the parent frame.
            // This will be slower, since we cannot do scalar replacement on the frame, but it is
            // required to prevent the materialized frame from getting out of sync during OSR.
            return bytecodeOSRNode.executeOSR(parentFrame, target, interpreterState);
        } else {
            bytecodeOSRNode.copyIntoOSRFrame(frame, parentFrame, target);
            try {
                return bytecodeOSRNode.executeOSR(frame, target, interpreterState);
            } finally {
                bytecodeOSRNode.restoreParentFrame(frame, parentFrame);
            }
        }
    }

    @Override
    public String toString() {
        return bytecodeOSRNode.toString() + "<OSR@" + target + ">";
    }
}
