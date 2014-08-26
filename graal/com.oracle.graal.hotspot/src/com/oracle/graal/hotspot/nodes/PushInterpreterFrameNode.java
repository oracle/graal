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
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.word.*;

/**
 * A call to the runtime code implementing the uncommon trap logic.
 */
@NodeInfo
public class PushInterpreterFrameNode extends FixedWithNextNode implements LIRLowerable {

    @Input ValueNode framePc;
    @Input ValueNode frameSize;
    @Input ValueNode senderSp;
    @Input ValueNode initialInfo;

    public static PushInterpreterFrameNode create(ValueNode frameSize, ValueNode framePc, ValueNode senderSp, ValueNode initialInfo) {
        return USE_GENERATED_NODES ? new PushInterpreterFrameNodeGen(frameSize, framePc, senderSp, initialInfo) : new PushInterpreterFrameNode(frameSize, framePc, senderSp, initialInfo);
    }

    protected PushInterpreterFrameNode(ValueNode frameSize, ValueNode framePc, ValueNode senderSp, ValueNode initialInfo) {
        super(StampFactory.forVoid());
        this.frameSize = frameSize;
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.initialInfo = initialInfo;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value frameSizeValue = gen.operand(frameSize);
        Value framePcValue = gen.operand(framePc);
        Value senderSpValue = gen.operand(senderSp);
        Value initialInfoValue = gen.operand(initialInfo);
        ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitPushInterpreterFrame(frameSizeValue, framePcValue, senderSpValue, initialInfoValue);
    }

    @NodeIntrinsic
    public static native void pushInterpreterFrame(Word frameSize, Word framePc, Word senderSp, Word initialInfo);

}
