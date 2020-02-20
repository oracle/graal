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

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_1, nameTemplate = "LLVMIntrinsic#{p#op}")
public class LLVMMinNode extends LLVMBinaryIntrinsicNode {
    public static final NodeClass<LLVMMinNode> TYPE = NodeClass.create(LLVMMinNode.class);

    public LLVMMinNode(JavaKind kind, ValueNode arg1, ValueNode arg2) {
        super(TYPE, LLVMIntrinsicOperation.MIN, kind, arg1, arg2);
    }

    @Override
    protected Value emitIntrinsic(LLVMIntrinsicGenerator gen, Value arg1, Value arg2) {
        return gen.emitMathMin(arg1, arg2);
    }

    public static ConstantNode fold(JavaConstant constant1, JavaConstant constant2) {
        assert constant1.getJavaKind() == constant2.getJavaKind();
        if (constant1.getJavaKind() == JavaKind.Float) {
            return ConstantNode.forFloat(Math.min(constant1.asFloat(), constant2.asFloat()));
        } else {
            return ConstantNode.forDouble(Math.min(constant1.asDouble(), constant2.asDouble()));
        }
    }
}
