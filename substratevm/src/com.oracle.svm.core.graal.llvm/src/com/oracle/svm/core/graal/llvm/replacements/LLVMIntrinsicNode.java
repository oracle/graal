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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo
public abstract class LLVMIntrinsicNode extends FloatingNode implements ArithmeticLIRLowerable, Canonicalizable {
    public static final NodeClass<LLVMIntrinsicNode> TYPE = NodeClass.create(LLVMIntrinsicNode.class);

    protected final LLVMIntrinsicOperation op;

    public LLVMIntrinsicNode(NodeClass<? extends LLVMIntrinsicNode> c, LLVMIntrinsicOperation op, JavaKind kind) {
        super(c, StampFactory.forKind(kind));
        this.op = op;
    }

    public enum LLVMIntrinsicOperation {
        LOG(1),
        LOG10(1),
        EXP(1),
        POW(2),
        SIN(1),
        COS(1),
        SQRT(1),
        ABS(1),
        ROUND(1),
        RINT(1),
        CEIL(1),
        FLOOR(1),
        MIN(2),
        MAX(2),
        COPYSIGN(2),
        FMA(3),
        CTLZ(1),
        CTTZ(1),
        CTPOP(1);

        private int argCount;

        LLVMIntrinsicOperation(int argCount) {
            this.argCount = argCount;
        }

        public int argCount() {
            return argCount;
        }
    }
}
