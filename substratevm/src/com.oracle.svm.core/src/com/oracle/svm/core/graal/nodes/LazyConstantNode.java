/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import java.util.function.Function;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaConstant;

/**
 * A node that eventually replaces itself with a {@link ConstantNode} when the actual constant value
 * is available. That must be before the first lowering.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_1)
public final class LazyConstantNode extends FloatingNode implements Canonicalizable, Lowerable {
    public static final NodeClass<LazyConstantNode> TYPE = NodeClass.create(LazyConstantNode.class);

    private final Function<CoreProviders, JavaConstant> constantSupplier;

    protected LazyConstantNode(Stamp stamp, Function<CoreProviders, JavaConstant> constantSupplier) {
        super(TYPE, stamp);
        this.constantSupplier = constantSupplier;
    }

    public static ValueNode create(Stamp stamp, Function<CoreProviders, JavaConstant> constantSupplier, CoreProviders providers) {
        ValueNode result = findSynonym(stamp, constantSupplier, providers);
        if (result != null) {
            return result;
        }
        return new LazyConstantNode(stamp, constantSupplier);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ValueNode result = findSynonym(stamp, constantSupplier, tool);
        if (result != null) {
            return result;
        }
        return this;
    }

    private static ValueNode findSynonym(Stamp stamp, Function<CoreProviders, JavaConstant> constantSupplier, CoreProviders providers) {
        JavaConstant constant = constantSupplier.apply(providers);
        if (constant == null) {
            /* Constant not available yet. */
            return null;
        }
        ConstantNode constantNode = ConstantNode.forConstant(constant, providers.getMetaAccess());

        Stamp newStamp = constantNode.stamp(NodeView.DEFAULT);
        GraalError.guarantee(newStamp.join(stamp) == newStamp, "Stamp can only improve");

        return constantNode;
    }

    @Override
    public void lower(LoweringTool tool) {
        throw GraalError.shouldNotReachHere("Constant value must have been computed before first lowering");
    }
}
