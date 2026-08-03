/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements;

import java.util.Arrays;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.ArrayLengthProvider;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Unchecked intrinsification of {@link Arrays#copyOf} or {@link Arrays#copyOfRange}. The range and
 * null checks are explicitly added when lowering {@link CopyOfNode}.
 *
 * @see CopyOfNode
 */
// @formatter:off
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN,
          cyclesRationale = "unknown copy length",
          size = NodeSize.SIZE_128,
          allowedUsageTypes = {InputType.Memory})
// @formatter:on
public final class UncheckedCopyOfNode extends FixedWithNextNode implements StateSplit, SingleMemoryKill, MemoryAccess, ArrayLengthProvider, DeoptimizingNode.DeoptBefore {
    public static final NodeClass<UncheckedCopyOfNode> TYPE = NodeClass.create(UncheckedCopyOfNode.class);

    @OptionalInput(InputType.State) protected FrameState stateBefore;
    @OptionalInput(InputType.State) protected FrameState stateAfter;
    @OptionalInput(InputType.Memory) MemoryKill lastLocationAccess;

    @Input protected ValueNode source;
    @Input protected ValueNode sourceLength;
    @Input protected ValueNode from;
    @Input protected ValueNode newLength;
    @OptionalInput protected ValueNode newObjectArrayType;

    private final JavaKind elementKind;

    /**
     * Constructor for the LoweredCopyOfNode that intrinsifies {@code Arrays.copyOf} and
     * {@code Arrays.copyOfRange} calls.
     *
     * The {@link #stamp} will be {@link #updateInitialStamp updated} later.
     */
    protected UncheckedCopyOfNode(@InjectedNodeParameter MetaAccessProvider metaAccess, JavaKind elementKind, ValueNode source, ValueNode sourceLength, ValueNode from, ValueNode newLength,
                    ValueNode newObjectArrayType) {
        super(TYPE, computeArrayStamp(metaAccess, elementKind));
        this.elementKind = elementKind;
        this.source = source;
        this.sourceLength = sourceLength;
        this.from = from;
        this.newLength = newLength;
        this.newObjectArrayType = newObjectArrayType.isConstant() && newObjectArrayType.asConstant().isDefaultForKind() ? null : newObjectArrayType;
    }

    /**
     * Compute an initial stamp based on the element kind which is the only thing known at
     * construction time.
     */
    public static Stamp computeArrayStamp(MetaAccessProvider metaAccess, JavaKind elementKind) {
        Class<?> elementClass = elementKind.isObject() ? Object.class : elementKind.toJavaClass();
        ResolvedJavaType elementType = metaAccess.lookupJavaType(elementClass).getArrayClass();
        return StampFactory.object(TypeReference.create(null, elementType), true);
    }

    /**
     * Try to improve the stamp starting from the stamp of the original invoke which will at least
     * be an array type of the proper element kind.
     */
    boolean computeBestStamp(ConstantReflectionProvider constantReflection) {
        Stamp result = stamp;
        if (newObjectArrayType instanceof GetClassNode) {
            // This is a copy on object arrays, and the class is retrieved
            // from an object that has a stamp.
            GetClassNode newClass = (GetClassNode) newObjectArrayType;
            result = newClass.getObject().stamp(NodeView.DEFAULT);
        }
        if (newObjectArrayType instanceof LoadHubNode) {
            // This is the same check for the object array stamp, but for SVM.
            LoadHubNode loadHub = (LoadHubNode) newObjectArrayType;
            result = loadHub.getValue().stamp(NodeView.DEFAULT);
        }
        if (constantReflection != null && newObjectArrayType != null) {
            // A partially canonicalized graph might be hiding the underlying constant
            ValueNode arrayType = GraphUtil.originalValue(newObjectArrayType, true);
            if (arrayType instanceof ConstantNode) {
                ResolvedJavaType type = constantReflection.asJavaType(arrayType.asConstant());
                if (type.isArray()) {
                    result = new ObjectStamp(type, true, true, false, true);
                }
            }
        } else {
            // Without the ConstantReflectionProvider, it's not possible to reflect
            // about the stamp of the copied array, even though the array class is constant.
            // For the time being, we cannot improve the result type in this way.
        }

        result = result.join(StampFactory.objectNonNull());
        result = stamp.improveWith(result);

        // The stamp should always be at least some array type
        assert result instanceof ObjectStamp && ((ObjectStamp) result).isAlwaysArray() && ((ObjectStamp) result).nonNull() : result;
        return updateStamp(result);
    }

    ValueNode getSource() {
        return source;
    }

    private ValueNode getSourceLength() {
        return sourceLength;
    }

    private ValueNode getFrom() {
        return from;
    }

    private ValueNode getNewLength() {
        return newLength;
    }

    ValueNode getNewObjectArrayType() {
        return newObjectArrayType;
    }

    @Override
    public boolean inferStamp() {
        ObjectStamp objectStamp = (ObjectStamp) stamp;
        if (objectStamp.isExactType() && objectStamp.nonNull()) {
            // Can't be improved
            return false;
        }
        return computeBestStamp(null);
    }

    /**
     * @see CopyOfSnippets.Templates#lower(CopyOfNode, LoweringTool, boolean)
     */
    boolean updateInitialStamp(Stamp newStamp) {
        return updateStamp(newStamp);
    }

    public JavaKind getElementKind() {
        return elementKind;
    }

    public void addSnippetArguments(Arguments args) {
        args.add("source", getSource());
        args.add("sourceLength", getSourceLength());
        args.add("from", getFrom());
        args.add("newLength", getNewLength());
        if (elementKind == JavaKind.Object) {
            args.add("newArrayType", getNewObjectArrayType());
        }
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(elementKind);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public ValueNode findLength(FindLengthMode mode, ConstantReflectionProvider constantReflection) {
        return getNewLength();
    }

    void rewireMemoryUsages() {
        // The CopyOfNode is a MacroNode, which makes it a memory checkpoint.
        // On the other hand, the CopyOfNode does not produce a memory snapshot.
        // Therefore, once we are committed to vectorizing this node,
        // we must forward this node's memory usages to this node's memory input.
        replaceAtUsages((Node) lastLocationAccess, InputType.Memory);
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public void setStateBefore(FrameState f) {
        updateUsages(stateBefore, f);
        stateBefore = f;
    }

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return false;
    }

    @NodeIntrinsic
    public static native Object copyOf(@ConstantNodeParameter JavaKind elementKind, Object source, int sourceLength, int from, int newLength, Object newObjectArrayType);

    /**
     * Intrinsic for primitive arrays.
     */
    public static Object copyOfPrimitiveArray(@ConstantNodeParameter JavaKind elementKind, Object source, int sourceLength, int from, int newLength) {
        return copyOf(elementKind, source, sourceLength, from, newLength, null);
    }

    /**
     * Intrinsic for object arrays.
     */
    public static Object copyOfObjectArray(Object source, int sourceLength, int from, int newLength, Object newObjectArrayType) {
        return copyOf(JavaKind.Object, source, sourceLength, from, newLength, newObjectArrayType);
    }

}
