/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node.NodeIntrinsicFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code ArrayLength} instruction gets the length of an array.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
@NodeIntrinsicFactory
public final class ArrayLengthNode extends FixedWithNextNode implements Canonicalizable.Unary<ValueNode>, Lowerable, Virtualizable {

    public static final NodeClass<ArrayLengthNode> TYPE = NodeClass.create(ArrayLengthNode.class);
    @Input ValueNode array;

    public ValueNode array() {
        return array;
    }

    @Override
    public ValueNode getValue() {
        return array;
    }

    public ArrayLengthNode(ValueNode array) {
        super(TYPE, StampFactory.positiveInt());
        this.array = array;
    }

    public static ValueNode create(ValueNode forValue, ConstantReflectionProvider constantReflection) {
        if (forValue instanceof AbstractNewArrayNode) {
            AbstractNewArrayNode newArray = (AbstractNewArrayNode) forValue;
            return newArray.length();
        }

        ValueNode length = readArrayLength(forValue, constantReflection);
        if (length != null) {
            return length;
        }
        return new ArrayLengthNode(forValue);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isNullConstant()) {
            return new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException);
        }
        ValueNode length = readArrayLength(forValue, tool.getConstantReflection());
        if (length != null) {
            return length;
        }
        return this;
    }

    /**
     * Gets the length of an array if possible.
     *
     * @return a node representing the length of {@code array} or null if it is not available
     */
    public static ValueNode readArrayLength(ValueNode originalArray, ConstantReflectionProvider constantReflection) {
        return GraphUtil.arrayLength(originalArray, ArrayLengthProvider.FindLengthMode.CANONICALIZE_READ, constantReflection);
    }

    public static boolean intrinsify(GraphBuilderContext b, ValueNode array) {
        ValueNode anchoredArray;
        AbstractObjectStamp arrayStamp = (AbstractObjectStamp) array.stamp(NodeView.DEFAULT);
        if (!arrayStamp.isAlwaysArray() || !arrayStamp.nonNull()) {
            /*
             * Reading the length must not float above a check whether the object is actually an
             * array. Every correct usage of the intrinsic must have a null check and an is-array
             * check beforehand. But it might not be reflected in the stamp, in which case we anchor
             * the array to the current block.
             */
            anchoredArray = b.add(new PiNode(array, arrayStamp.asAlwaysArray().asNonNull(), b.add(new BeginNode())));
        } else {
            anchoredArray = array;
        }

        b.addPush(JavaKind.Int, new ArrayLengthNode(anchoredArray));
        return true;
    }

    /**
     * Returns the length of the given array. It does not check if the provided object is an array,
     * so the caller has to check that beforehand.
     */
    @NodeIntrinsic
    public static native int arrayLength(Object array);

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(array());
        if (alias instanceof VirtualArrayNode) {
            VirtualArrayNode virtualArray = (VirtualArrayNode) alias;
            tool.replaceWithValue(ConstantNode.forInt(virtualArray.entryCount(), graph()));
        }
    }
}
