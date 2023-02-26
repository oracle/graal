/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Indirect call with a computed address. The address computation is emitted only after LIR
 * generation, which ensures that no other instructions get scheduled in between the computation and
 * the invocation. Only a few simple computations are supported yet. Since each computation must be
 * implemented separately for every supported platform, it is desirable to keep the computations few
 * and simple. Computations are a linear list and operate on a single temporary value. The initial
 * value is the {@link #addressBase} node.
 */
@NodeInfo
public class ComputedIndirectCallTargetNode extends LoweredCallTargetNode {
    public static final NodeClass<ComputedIndirectCallTargetNode> TYPE = NodeClass.create(ComputedIndirectCallTargetNode.class);

    public abstract static class Computation {
    }

    /**
     * Loads the provided field from the current temporary value. The result of the field load is
     * the new temporary value.
     */
    public static class FieldLoad extends Computation {
        private final ResolvedJavaField field;

        public FieldLoad(ResolvedJavaField field) {
            this.field = field;
        }

        public ResolvedJavaField getField() {
            return field;
        }
    }

    /*
     * Checks if the current temporary value is 0. If false, the temporary value remains unchanged.
     * If true, then the temporary value is replaced with the provided field loaded from the
     * provided constant object.
     */
    public static class FieldLoadIfZero extends Computation {
        private final JavaConstant object;
        private final ResolvedJavaField field;

        public FieldLoadIfZero(JavaConstant object, ResolvedJavaField field) {
            this.object = object;
            this.field = field;
        }

        public JavaConstant getObject() {
            return object;
        }

        public ResolvedJavaField getField() {
            return field;
        }
    }

    @Input protected ValueNode addressBase;
    private final Computation[] addressComputation;

    public ComputedIndirectCallTargetNode(ValueNode addressBase, Computation[] addressComputation, ValueNode[] arguments, StampPair returnStamp, JavaType[] signature, ResolvedJavaMethod target) {
        super(TYPE, arguments, returnStamp, signature, target, SubstrateCallingConventionKind.Java.toType(true), InvokeKind.Static);
        this.addressBase = addressBase;
        this.addressComputation = addressComputation;
    }

    public ValueNode getAddressBase() {
        return addressBase;
    }

    public Computation[] getAddressComputation() {
        return addressComputation;
    }

    @Override
    public String targetName() {
        return targetMethod() == null ? "[unknown]" : targetMethod().format("ComputedIndirect#%h.%n");
    }
}
