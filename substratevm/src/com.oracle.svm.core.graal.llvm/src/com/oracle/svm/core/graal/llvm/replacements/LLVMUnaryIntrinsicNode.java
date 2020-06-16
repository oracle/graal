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
public abstract class LLVMUnaryIntrinsicNode extends LLVMIntrinsicNode {
    public static final NodeClass<LLVMUnaryIntrinsicNode> TYPE = NodeClass.create(LLVMUnaryIntrinsicNode.class);

    @Input private ValueNode arg;

    public LLVMUnaryIntrinsicNode(NodeClass<? extends LLVMIntrinsicNode> c, LLVMIntrinsicOperation op, JavaKind kind, ValueNode arg) {
        super(c, op, kind);
        this.arg = arg;
    }

    public static ConstantNode tryFold(LLVMIntrinsicOperation op, ValueNode arg) {
        if (arg.isConstant()) {
            JavaConstant constantArg = arg.asJavaConstant();
            switch (op) {
                case CEIL:
                    return LLVMCeilNode.fold(constantArg);
                case FLOOR:
                    return LLVMFloorNode.fold(constantArg);
                case CTLZ:
                    return LLVMCountLeadingZerosNode.fold(constantArg);
                case CTTZ:
                    return LLVMCountTrailingZerosNode.fold(constantArg);
            }
        }
        return null;
    }

    static LLVMUnaryIntrinsicNode factory(LLVMIntrinsicOperation op, JavaKind kind, ValueNode arg) {
        switch (op) {
            case CEIL:
                return new LLVMCeilNode(kind, arg);
            case FLOOR:
                return new LLVMFloorNode(kind, arg);
            case CTLZ:
                return new LLVMCountLeadingZerosNode(kind, arg);
            case CTTZ:
                return new LLVMCountTrailingZerosNode(kind, arg);
            default:
                throw shouldNotReachHere();
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ConstantNode folded = tryFold(op, arg);
        return (folded != null) ? folded : this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, emitIntrinsic((LLVMIntrinsicGenerator) gen, builder.operand(arg)));
    }

    protected abstract Value emitIntrinsic(LLVMIntrinsicGenerator gen, Value value);
}
