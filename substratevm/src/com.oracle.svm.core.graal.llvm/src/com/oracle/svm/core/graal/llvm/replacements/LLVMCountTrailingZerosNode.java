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
public class LLVMCountTrailingZerosNode extends LLVMUnaryIntrinsicNode {
    public static final NodeClass<LLVMCountTrailingZerosNode> TYPE = NodeClass.create(LLVMCountTrailingZerosNode.class);

    public LLVMCountTrailingZerosNode(JavaKind kind, ValueNode arg) {
        super(TYPE, LLVMIntrinsicOperation.CTTZ, kind, arg);
    }

    @Override
    protected Value emitIntrinsic(LLVMIntrinsicGenerator gen, Value arg) {
        return gen.emitCountTrailingZeros(arg);
    }

    public static ConstantNode fold(JavaConstant constant) {
        int ctlz;
        if (constant.getJavaKind() == JavaKind.Int) {
            ctlz = Integer.numberOfTrailingZeros(constant.asInt());
        } else {
            ctlz = Long.numberOfTrailingZeros(constant.asLong());
        }
        return ConstantNode.forInt(ctlz);
    }
}
