/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes.aot;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_20;

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.hotspot.nodes.DeoptimizingStubCall;
import com.oracle.graal.hotspot.nodes.type.MethodCountersPointerStamp;
import com.oracle.graal.hotspot.word.KlassPointer;
import com.oracle.graal.hotspot.word.MethodCountersPointer;
import com.oracle.graal.hotspot.word.MethodPointer;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.nodes.util.GraphUtil;

/**
 * A call to the VM via a regular stub.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_20)
public class ResolveMethodAndLoadCountersStubCall extends DeoptimizingStubCall implements Canonicalizable, LIRLowerable {
    public static final NodeClass<ResolveMethodAndLoadCountersStubCall> TYPE = NodeClass.create(ResolveMethodAndLoadCountersStubCall.class);

    @OptionalInput protected ValueNode method;
    @Input protected ValueNode klassHint;
    @Input protected ValueNode methodDescription;
    protected Constant methodConstant;

    public ResolveMethodAndLoadCountersStubCall(ValueNode method, ValueNode klassHint, ValueNode methodDescription) {
        super(TYPE, MethodCountersPointerStamp.methodCountersNonNull());
        this.klassHint = klassHint;
        this.method = method;
        this.methodDescription = methodDescription;
    }

    @NodeIntrinsic
    public static native MethodCountersPointer resolveMethodAndLoadCounters(MethodPointer method, KlassPointer klassHint, Object methodDescription);

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (method != null) {
            methodConstant = GraphUtil.foldIfConstantAndRemove(this, method);
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert methodConstant != null : "Expected method to fold: " + method;

        Value methodDescriptionValue = gen.operand(methodDescription);
        Value klassHintValue = gen.operand(klassHint);
        LIRFrameState fs = gen.state(this);
        assert fs != null : "The stateAfter is null";

        Value result = ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitResolveMethodAndLoadCounters(methodConstant, klassHintValue, methodDescriptionValue, fs);

        gen.setResult(this, result);
    }

}
