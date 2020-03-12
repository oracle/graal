/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.replacements;

import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo
public abstract class LLVMBinaryIntrinsicNode extends LLVMIntrinsicNode {
    public static final NodeClass<LLVMBinaryIntrinsicNode> TYPE = NodeClass.create(LLVMBinaryIntrinsicNode.class);

    @Input private ValueNode arg1;
    @Input private ValueNode arg2;

    public LLVMBinaryIntrinsicNode(NodeClass<? extends LLVMIntrinsicNode> c, LLVMIntrinsicOperation op, JavaKind kind, ValueNode arg1, ValueNode arg2) {
        super(c, op, kind);
        this.arg1 = arg1;
        this.arg2 = arg2;
    }

    public static ConstantNode tryFold(LLVMIntrinsicOperation op, ValueNode arg1, ValueNode arg2) {
        if (arg1.isConstant() && arg2.isConstant()) {
            JavaConstant constantArg1 = arg1.asJavaConstant();
            JavaConstant constantArg2 = arg2.asJavaConstant();
            switch (op) {
                case MIN:
                    return LLVMMinNode.fold(constantArg1, constantArg2);
                case MAX:
                    return LLVMMaxNode.fold(constantArg1, constantArg2);
                case COPYSIGN:
                    return LLVMCopySignNode.fold(constantArg1, constantArg2);
            }
        }
        return null;
    }

    static LLVMBinaryIntrinsicNode factory(LLVMIntrinsicOperation op, JavaKind kind, ValueNode arg1, ValueNode arg2) {
        switch (op) {
            case MIN:
                return new LLVMMinNode(kind, arg1, arg2);
            case MAX:
                return new LLVMMaxNode(kind, arg1, arg2);
            case COPYSIGN:
                return new LLVMCopySignNode(kind, arg1, arg2);
            default:
                throw shouldNotReachHere();
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ConstantNode folded = tryFold(op, arg1, arg2);
        return (folded != null) ? folded : this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, emitIntrinsic((LLVMIntrinsicGenerator) gen, builder.operand(arg1), builder.operand(arg2)));
    }

    protected abstract Value emitIntrinsic(LLVMIntrinsicGenerator gen, Value value1, Value value2);
}
