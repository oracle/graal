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

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_20;

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.hotspot.meta.HotSpotConstantLoadAction;
import com.oracle.graal.hotspot.nodes.DeoptimizingStubCall;
import com.oracle.graal.hotspot.word.KlassPointer;
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
public class ResolveConstantStubCall extends DeoptimizingStubCall implements Canonicalizable, LIRLowerable {
    public static final NodeClass<ResolveConstantStubCall> TYPE = NodeClass.create(ResolveConstantStubCall.class);

    @OptionalInput protected ValueNode value;
    @Input protected ValueNode string;
    protected Constant constant;
    protected HotSpotConstantLoadAction action;

    public ResolveConstantStubCall(ValueNode value, ValueNode string) {
        super(TYPE, value.stamp());
        this.value = value;
        this.string = string;
        this.action = HotSpotConstantLoadAction.RESOLVE;
    }

    public ResolveConstantStubCall(ValueNode value, ValueNode string, HotSpotConstantLoadAction action) {
        super(TYPE, value.stamp());
        this.value = value;
        this.string = string;
        this.action = action;
    }

    @NodeIntrinsic
    public static native Object resolveObject(Object value, Object symbol);

    @NodeIntrinsic
    public static native KlassPointer resolveKlass(KlassPointer value, Object symbol);

    @NodeIntrinsic
    public static native KlassPointer resolveKlass(KlassPointer value, Object symbol, @ConstantNodeParameter HotSpotConstantLoadAction action);

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (value != null) {
            constant = GraphUtil.foldIfConstantAndRemove(this, value);
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert constant != null : "Expected the value to fold: " + value;
        Value stringValue = gen.operand(string);
        Value result;
        LIRFrameState fs = gen.state(this);
        assert fs != null : "The stateAfter is null";
        if (constant instanceof HotSpotObjectConstant) {
            result = ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitObjectConstantRetrieval(constant, stringValue, fs);
        } else if (constant instanceof HotSpotMetaspaceConstant) {
            if (action == HotSpotConstantLoadAction.RESOLVE) {
                result = ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitMetaspaceConstantRetrieval(constant, stringValue, fs);
            } else {
                assert action == HotSpotConstantLoadAction.INITIALIZE;
                result = ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitKlassInitializationAndRetrieval(constant, stringValue, fs);
            }
        } else {
            throw new BailoutException("Unsupported constant type: " + constant);
        }
        gen.setResult(this, result);
    }

}
