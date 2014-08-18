/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.word.*;

/**
 * Emits code to leave (pop) the current low-level stack frame which is being deoptimized. This node
 * is only used in {@link DeoptimizationStub}.
 */
@NodeInfo
public class LeaveDeoptimizedStackFrameNode extends FixedWithNextNode implements LIRLowerable {

    @Input private ValueNode frameSize;
    @Input private ValueNode initialInfo;

    public static LeaveDeoptimizedStackFrameNode create(ValueNode frameSize, ValueNode initialInfo) {
        return new LeaveDeoptimizedStackFrameNodeGen(frameSize, initialInfo);
    }

    protected LeaveDeoptimizedStackFrameNode(ValueNode frameSize, ValueNode initialInfo) {
        super(StampFactory.forVoid());
        this.frameSize = frameSize;
        this.initialInfo = initialInfo;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value frameSizeValue = gen.operand(frameSize);
        Value initialInfoValue = gen.operand(initialInfo);
        ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitLeaveDeoptimizedStackFrame(frameSizeValue, initialInfoValue);
    }

    @NodeIntrinsic
    public static native void leaveDeoptimizedStackFrame(int frameSize, Word initialInfo);
}
