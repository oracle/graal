/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.consumer;

import static jdk.graal.compiler.nodeinfo.InputType.Extension;
import static jdk.graal.compiler.nodeinfo.InputType.Value;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.Collections;
import java.util.List;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.java.AbstractNewArrayNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.spi.ArrayLengthProvider;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.SimplifiableVectorNode.VectorSimplifier;
import jdk.graal.compiler.vector.nodes.VectorNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Allocate a new array and initialize it with the contents of
 * {@link AbstractMaterializeVectorNode#vector}.
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
   cyclesRationale = "We cannot argue about vector nodes statically.",
   size = SIZE_UNKNOWN,
   sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public abstract class AbstractMaterializeVectorNode extends AbstractNewObjectNode implements VectorConsumer, ArrayLengthProvider, Canonicalizable {
    public static final NodeClass<AbstractMaterializeVectorNode> TYPE = NodeClass.create(AbstractMaterializeVectorNode.class);

    @Input ValueNode vector;
    @Input ValueNode length;

    @Input(Extension) ValueNode allocator;

    //@formatter:off
    @NodeInfo(allowedUsageTypes = {Value, Extension},
              cycles = CYCLES_UNKNOWN,
              cyclesRationale = "We cannot argue about vector nodes statically.",
              size = SIZE_UNKNOWN,
              sizeRationale = "We cannot argue about vector nodes statically.")
    //@formatter:on
    public abstract static class Allocator extends FloatingNode {
        public static final NodeClass<Allocator> TYPE = NodeClass.create(Allocator.class);

        protected Allocator(NodeClass<? extends Allocator> c) {
            super(c, StampFactory.forVoid());
        }

        protected abstract AbstractNewArrayNode createAllocationNode(MetaAccessProvider runtime, ValueNode length);

        public abstract JavaKind getArrayKind();
    }

    private static Stamp makeStamp(Stamp stamp) {
        if (stamp instanceof ObjectStamp) {
            return ((ObjectStamp) stamp).asNonNull();
        }
        return stamp;
    }

    protected AbstractMaterializeVectorNode(NodeClass<? extends AbstractMaterializeVectorNode> c, ValueNode allocator, Stamp stamp, ValueNode vector, ValueNode length) {
        super(c, makeStamp(stamp), false, null);
        this.vector = vector;
        this.length = length;
        this.allocator = allocator;
    }

    public VectorNode getVector() {
        return (VectorNode) vector;
    }

    public void setVector(VectorNode newVector) {
        updateUsages(vector.asNode(), newVector.asNode());
        vector = newVector.asNode();
    }

    public Allocator getAllocator() {
        return (Allocator) allocator;
    }

    @Override
    public int getMaxVectorLength(VectorArchitecture arch) {
        return arch.getMaxVectorLength(getVector().getVectorStamp().getElementStamp());
    }

    @Override
    public List<? extends ValueNode> getVectorInputs() {
        return Collections.singletonList(vector.asNode());
    }

    @Override
    public ValueNode findLength(FindLengthMode mode, ConstantReflectionProvider constantReflection) {
        return length;
    }

    @Override
    public ValueNode getLength() {
        return length;
    }

    @Override
    public void simplifyTree(VectorSimplifier simplifier) {
        setVector(simplifier.simplifyLengthHint(getVector(), length));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (this.getUsageCount() == 0) {
            if (((IntegerStamp) length.stamp(NodeView.DEFAULT)).isPositive()) {
                return null;
            }
        }
        return this;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
