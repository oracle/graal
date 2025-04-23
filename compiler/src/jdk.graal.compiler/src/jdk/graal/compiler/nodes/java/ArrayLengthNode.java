/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.java;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.spi.ArrayLengthProvider;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code ArrayLength} instruction gets the length of an array.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
@NodeIntrinsicFactory
public final class ArrayLengthNode extends FixedWithNextNode implements Canonicalizable.Unary<ValueNode>, Lowerable, Virtualizable, MemoryAccess, Simplifiable {

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

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.ARRAY_LENGTH_LOCATION;
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
        /**
         * Normally we run replacement to any length provider node in simplify but for snippets and
         * explode loop we run a limited form for constants before. This is needed for unrolling for
         * example that uses #canonical instead of #simplify to not recompute the cfg all the time.
         */
        ValueNode len = searchForConstantLength(tool.getConstantReflection(), forValue);
        if (len != null) {
            return len;
        }
        return this;
    }

    private static ValueNode searchForConstantLength(ConstantReflectionProvider constantReflection, ValueNode forValue) {
        ValueNode len = GraphUtil.arrayLength(forValue, ArrayLengthProvider.FindLengthMode.SEARCH_ONLY, constantReflection);
        return len != null && len.isConstant() ? len : null;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        /*
         * If we are before lowering we only fold to constants: replacing the load with a length
         * guarded pi can be done in multiple places and we only want to do it once for all users,
         * so we let it be done after lowering to catch all users at once.
         */
        ValueNode constantLength = searchForConstantLength(tool.getConstantReflection(), getValue());
        if (constantLength == null && !graph().isAfterStage(StageFlag.HIGH_TIER_LOWERING)) {
            return;
        }
        ValueNode length = constantLength == null ? readArrayLength(getValue(), tool.getConstantReflection()) : constantLength;
        if (tool.allUsagesAvailable() && length != null) {
            /**
             * If we are using the array length directly (for example from an allocation) instead of
             * this array length node we must ensure we are preserving the previously used
             * positiveInt stamp and that the positive int stamp users are not floating above the
             * position of this array length (which is dominated by the null check & min array size
             * check ensuring len>=0).
             *
             * So for code like
             *
             * <pre>
             * int[] arr = new int[length];
             * aLotOfCode();
             * use(arr.length);
             * aLotMoreCode();
             * userOptimizingBasedOnPositiveIntStamp(arr.length);
             * </pre>
             *
             * we must preserve the fact that only at the point of the original allocation the
             * property that length >= 0 is guaranteed. Thus we replace this with
             *
             *
             * <pre>
             * int[] arr = new int[length];
             * lengthPiGuardedHere = new Pi(Length >= 0);
             * aLotOfCode();
             * use(lengthPiGuardedHere);
             * aLotMoreCode();
             * userOptimizingBasedOnPositiveIntStamp(lengthPiGuardedHere);
             * </pre>
             */
            StructuredGraph graph = graph();
            ValueNode replacement = maybeAddPositivePi(length, this);
            graph.replaceFixedWithFloating(this, replacement);
        }
    }

    /**
     * If necessary, improves the {@code length}'s stamp to a positive value by adding a
     * {@link PiNode} for it. The pi will be attached to a new {@link ValueAnchorNode} after the
     * {@code insertionPosition}.
     *
     * @return the {@code length} or its {@linkplain Graph#addOrUnique unique representative} if the
     *         length's stamp is already positive; otherwise, a new {@link PiNode} proving a
     *         positive stamp for the length
     */
    public static ValueNode maybeAddPositivePi(ValueNode length, FixedWithNextNode insertionPosition) {
        StructuredGraph graph = insertionPosition.graph();
        ValueNode localLength = graph.addOrUniqueWithInputs(length);
        ValueNode replacement = localLength;
        if (!localLength.isConstant() && localLength.stamp(NodeView.DEFAULT).canBeImprovedWith(StampFactory.positiveInt())) {
            ValueAnchorNode g = graph.add(new ValueAnchorNode());
            graph.addAfterFixed(insertionPosition, g);
            replacement = graph.addWithoutUnique(new PiNode(localLength, StampFactory.positiveInt(), g));
        }
        return replacement;
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
