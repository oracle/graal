/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.lowering;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;

import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class LLVMAddressLowering extends AddressLoweringByNodePhase.AddressLowering {

    @Override
    public AddressNode lower(ValueNode base, ValueNode offset) {
        LLVMAddressNode ret = new LLVMAddressNode(base, offset);
        StructuredGraph graph = base.graph();
        return graph.unique(ret);
    }

    @NodeInfo
    public static class LLVMAddressNode extends AddressNode implements LIRLowerable {
        public static final NodeClass<LLVMAddressNode> TYPE = NodeClass.create(LLVMAddressNode.class);

        @Input private ValueNode base;
        @Input private ValueNode index;

        public LLVMAddressNode(ValueNode base, ValueNode offset) {
            super(TYPE);
            this.base = base;
            this.index = offset;
        }

        @Override
        public ValueNode getBase() {
            return base;
        }

        @Override
        public ValueNode getIndex() {
            return index;
        }

        @Override
        public long getMaxConstantDisplacement() {
            return Long.MAX_VALUE;
        }

        @Override
        public void generate(NodeLIRBuilderTool generator) {
            LIRGeneratorTool gen = generator.getLIRGeneratorTool();
            generator.setResult(this, new LLVMAddressValue(gen.getLIRKind(stamp(NodeView.DEFAULT)), generator.operand(base), generator.operand(index)));
        }
    }

    public static class LLVMAddressValue extends Value {

        private final Value base;
        private final Value index;

        LLVMAddressValue(ValueKind<?> kind, Value base, Value index) {
            super(kind);
            this.base = base;
            this.index = index;
        }

        public Value getBase() {
            return base;
        }

        public Value getIndex() {
            return index;
        }
    }
}
