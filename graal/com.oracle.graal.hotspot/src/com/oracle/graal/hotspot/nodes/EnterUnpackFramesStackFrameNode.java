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
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.word.*;

/**
 * Emits code to enter a low-level stack frame specifically to call out to the C++ method
 * {@link HotSpotBackend#UNPACK_FRAMES Deoptimization::unpack_frames}.
 */
@NodeInfo
public class EnterUnpackFramesStackFrameNode extends FixedWithNextNode implements LIRLowerable {

    @Input ValueNode framePc;
    @Input ValueNode senderSp;
    @Input ValueNode senderFp;
    @Input SaveAllRegistersNode registerSaver;

    public static EnterUnpackFramesStackFrameNode create(ValueNode framePc, ValueNode senderSp, ValueNode senderFp, ValueNode registerSaver) {
        return USE_GENERATED_NODES ? new EnterUnpackFramesStackFrameNodeGen(framePc, senderSp, senderFp, registerSaver) : new EnterUnpackFramesStackFrameNode(framePc, senderSp, senderFp, registerSaver);
    }

    protected EnterUnpackFramesStackFrameNode(ValueNode framePc, ValueNode senderSp, ValueNode senderFp, ValueNode registerSaver) {
        super(StampFactory.forVoid());
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.senderFp = senderFp;
        this.registerSaver = (SaveAllRegistersNode) registerSaver;
    }

    private SaveRegistersOp getSaveRegistersOp() {
        return registerSaver.getSaveRegistersOp();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value operandValue = gen.operand(framePc);
        Value senderSpValue = gen.operand(senderSp);
        Value senderFpValue = gen.operand(senderFp);
        ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitEnterUnpackFramesStackFrame(operandValue, senderSpValue, senderFpValue, getSaveRegistersOp());
    }

    @NodeIntrinsic
    public static native void enterUnpackFramesStackFrame(Word framePc, Word senderSp, Word senderFp, long registerSaver);
}
