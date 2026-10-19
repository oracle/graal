/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.meta.MethodOffset;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.MethodRefSupport;
import com.oracle.svm.core.meta.SubstrateMethodOffsetConstant;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;

import com.oracle.svm.core.meta.SubstrateMethodRefStamp;
import com.oracle.svm.shared.util.SubstrateUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConvertNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Converts a method offset that is relative to the code base to an absolute method pointer. This
 * being an explicit {@link ConvertNode} enables canonicalization to compare offsets to offsets and
 * avoid unnecessary arithmetic, for example:
 *
 * <pre>
 * if (codeBase + loadedMethodOffset == constantMethodPointer) { ... }
 * </pre>
 *
 * can become
 *
 * <pre>
 * if (loadedMethodOffset == constantMethodOffset) { ... }
 * </pre>
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public final class MethodOffsetToPointerNode extends UnaryNode implements ConvertNode, LIRLowerable {
    public static final NodeClass<MethodOffsetToPointerNode> TYPE = NodeClass.create(MethodOffsetToPointerNode.class);

    public MethodOffsetToPointerNode(ValueNode methodOffset) {
        super(TYPE, SubstrateMethodRefStamp.pointerNonNull(), methodOffset);
        assert methodOffset.stamp(NodeView.DEFAULT) instanceof SubstrateMethodRefStamp inputStamp &&
                        inputStamp.isOffset() && inputStamp.nonNull();
    }

    @Override
    public Constant convert(Constant c, ConstantReflectionProvider constantReflection) {
        if (SubstrateUtil.HOSTED && c instanceof SubstrateMethodOffsetConstant offsetConstant) {
            MethodOffset offset = offsetConstant.offset();
            return new SubstrateMethodPointerConstant(MethodRefSupport.createMethodPointer(MethodRefSupport.getMethod(offset), offset.permitsRewriteToPLT()));
        }
        return null;
    }

    @Override
    public Constant reverse(Constant c, ConstantReflectionProvider constantReflection) {
        if (SubstrateUtil.HOSTED && c instanceof SubstrateMethodPointerConstant pointerConstant) {
            MethodPointer pointer = pointerConstant.pointer();
            return new SubstrateMethodOffsetConstant(MethodRefSupport.createMethodOffset(pointerConstant.getMethod(), pointer.permitsRewriteToPLT()));
        }
        return null;
    }

    @Override
    public boolean mayNullCheckSkipConversion() {
        return false;
    }

    @Override
    public boolean isLossless() {
        return false;
    }

    @Override
    public boolean preservesOrder(CanonicalCondition op) {
        return op == CanonicalCondition.EQ;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant() && tool.getMetaAccess() != null) {
            Constant converted = convert(forValue.asConstant(), tool.getConstantReflection());
            if (converted != null) {
                return ConstantNode.forConstant(stamp(NodeView.DEFAULT), converted, tool.getMetaAccess());
            }
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        ValueKind<?> kind = tool.getLIRKind(stamp(NodeView.DEFAULT));
        Register codeBaseRegister = ReservedRegisters.singleton().getCodeBaseRegister();
        Value codeBase = codeBaseRegister.asValue(kind);
        Value result = tool.getArithmetic().emitAdd(gen.operand(getValue()), codeBase, false);
        gen.setResult(this, result);
    }
}
