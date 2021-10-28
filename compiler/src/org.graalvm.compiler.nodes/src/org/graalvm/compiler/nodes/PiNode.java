/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsicFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.ValueProxy;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

//JaCoCo Exclude

/**
 * A node that changes the type of its input, usually narrowing it. For example, a {@link PiNode}
 * refines the type of a receiver during type-guarded inlining to be the type tested by the guard.
 *
 * In contrast to a {@link GuardedValueNode}, a {@link PiNode} is useless as soon as the type of its
 * input is as narrow or narrower than the {@link PiNode}'s type. The {@link PiNode}, and therefore
 * also the scheduling restriction enforced by the guard, will go away.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
@NodeIntrinsicFactory
public class PiNode extends FloatingGuardedNode implements LIRLowerable, Virtualizable, Canonicalizable, ValueProxy {

    public static final NodeClass<PiNode> TYPE = NodeClass.create(PiNode.class);
    @Input ValueNode object;
    protected Stamp piStamp;

    public ValueNode object() {
        return object;
    }

    protected PiNode(NodeClass<? extends PiNode> c, ValueNode object, Stamp stamp, GuardingNode guard) {
        super(c, stamp, guard);
        this.object = object;
        this.piStamp = stamp;
        assert piStamp.isCompatible(object.stamp(NodeView.DEFAULT)) : "Object stamp not compatible to piStamp";
        inferStamp();
    }

    public PiNode(ValueNode object, Stamp stamp) {
        this(object, stamp, null);
    }

    public PiNode(ValueNode object, Stamp stamp, ValueNode guard) {
        this(TYPE, object, stamp, (GuardingNode) guard);
    }

    public PiNode(ValueNode object, ValueNode guard) {
        this(object, AbstractPointerStamp.pointerNonNull(object.stamp(NodeView.DEFAULT)), guard);
    }

    public PiNode(ValueNode object, ResolvedJavaType toType, boolean exactType, boolean nonNull) {
        this(object, StampFactory.object(exactType ? TypeReference.createExactTrusted(toType) : TypeReference.createWithoutAssumptions(toType),
                        nonNull || StampTool.isPointerNonNull(object.stamp(NodeView.DEFAULT))));
    }

    public static ValueNode create(ValueNode object, Stamp stamp) {
        ValueNode value = canonical(object, stamp, null, null);
        if (value != null) {
            return value;
        }
        return new PiNode(object, stamp);
    }

    public static ValueNode create(ValueNode object, Stamp stamp, ValueNode guard) {
        ValueNode value = canonical(object, stamp, (GuardingNode) guard, null);
        if (value != null) {
            return value;
        }
        return new PiNode(object, stamp, guard);
    }

    public static ValueNode create(ValueNode object, ValueNode guard) {
        Stamp stamp = AbstractPointerStamp.pointerNonNull(object.stamp(NodeView.DEFAULT));
        ValueNode value = canonical(object, stamp, (GuardingNode) guard, null);
        if (value != null) {
            return value;
        }
        return new PiNode(object, stamp, guard);
    }

    public enum IntrinsifyOp {
        NON_NULL,
        POSITIVE_INT
    }

    public static boolean intrinsify(GraphBuilderContext b, ValueNode input, ValueNode guard, IntrinsifyOp intrinsifyOp) {
        Stamp piStamp;
        JavaKind pushKind;
        switch (intrinsifyOp) {
            case NON_NULL:
                piStamp = AbstractPointerStamp.pointerNonNull(input.stamp(NodeView.DEFAULT));
                pushKind = JavaKind.Object;
                break;
            case POSITIVE_INT:
                piStamp = StampFactory.positiveInt();
                pushKind = JavaKind.Int;
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        ValueNode value = canonical(input, piStamp, (GuardingNode) guard, null);
        if (value == null) {
            value = new PiNode(input, piStamp, guard);
        }
        b.push(pushKind, b.append(value));
        return true;
    }

    public static boolean intrinsify(GraphBuilderContext b, ValueNode object, ResolvedJavaType toType, boolean exactType, boolean nonNull) {
        Stamp stamp = StampFactory.object(exactType ? TypeReference.createExactTrusted(toType) : TypeReference.createWithoutAssumptions(toType),
                        nonNull || StampTool.isPointerNonNull(object.stamp(NodeView.DEFAULT)));
        ValueNode value = canonical(object, stamp, null, null);
        if (value == null) {
            value = new PiNode(object, stamp);
        }
        b.push(JavaKind.Object, b.append(value));
        return true;
    }

    /**
     * A stamp expressing the property that is proved by the {@linkplain #getGuard() guard}, but not
     * more.
     * </p>
     *
     * For example, if the guard proves a property {@code x >= 0} on an {@code int} value, then the
     * {@link #piStamp()} should be {@link StampFactory#positiveInt()}. If the input value's stamp
     * is constrained, e.g., {@code [-100 - 100]}, then this pi's overall {@link #stamp(NodeView)}
     * will be {@code [0 - 100]}, computed as the join of the {@link #piStamp()} and the input's
     * stamp.
     */
    public final Stamp piStamp() {
        return piStamp;
    }

    public void strengthenPiStamp(Stamp newPiStamp) {
        assert this.piStamp.join(newPiStamp).equals(newPiStamp) : "stamp can only improve";
        this.piStamp = newPiStamp;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        if (generator.hasOperand(object)) {
            generator.setResult(this, generator.operand(object));
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(computeStamp());
    }

    private Stamp computeStamp() {
        return piStamp.improveWith(object().stamp(NodeView.DEFAULT));
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            ResolvedJavaType type = StampTool.typeOrNull(this, tool.getMetaAccess());
            if (type != null && type.isAssignableFrom(virtual.type())) {
                tool.replaceWithVirtual(virtual);
            } else {
                tool.getDebug().log(DebugContext.INFO_LEVEL, "could not virtualize Pi because of type mismatch: %s %s vs %s", this, type, virtual.type());
            }
        }
    }

    public static ValueNode canonical(ValueNode object, Stamp piStamp, GuardingNode guard, PiNode self) {
        // Use most up to date stamp.
        Stamp computedStamp = piStamp.improveWith(object.stamp(NodeView.DEFAULT));

        // The pi node does not give any additional information => skip it.
        if (computedStamp.equals(object.stamp(NodeView.DEFAULT))) {
            return object;
        }

        if (guard == null) {
            // Try to merge the pi node with a load node.
            if (object instanceof ReadNode && !object.hasMoreThanOneUsage()) {
                ReadNode readNode = (ReadNode) object;
                readNode.setStamp(readNode.stamp(NodeView.DEFAULT).improveWith(piStamp));
                return readNode;
            }
        } else {
            for (Node n : guard.asNode().usages()) {
                if (n instanceof PiNode && n != self) {
                    PiNode otherPi = (PiNode) n;
                    if (otherPi.guard != guard) {
                        assert otherPi.object() == guard;
                        /*
                         * The otherPi is unrelated because it uses this.guard as object but not as
                         * guard.
                         */
                        continue;
                    }
                    if (otherPi.object() == self || otherPi.object() == object) {
                        // Check if other pi's stamp is more precise
                        Stamp joinedStamp = piStamp.improveWith(otherPi.piStamp());
                        if (joinedStamp.equals(piStamp)) {
                            // Stamp did not get better, nothing to do.
                        } else if (otherPi.object() == object && joinedStamp.equals(otherPi.piStamp())) {
                            // We can be replaced with the other pi.
                            return otherPi;
                        } else {
                            // Create a new pi node with the more precise joined stamp.
                            return new PiNode(object, joinedStamp, guard.asNode());
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Node value = canonical(object(), piStamp(), getGuard(), this);
        if (value != null) {
            return value;
        }
        if (tool.allUsagesAvailable()) {
            for (Node usage : usages()) {
                if (!(usage instanceof VirtualState)) {
                    return this;
                }
            }
            // Only state usages: for them a more precise stamp does not matter.
            return object;
        }
        return this;
    }

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }

    public void setOriginalNode(ValueNode newNode) {
        this.updateUsages(object, newNode);
        this.object = newNode;
        assert piStamp.isCompatible(object.stamp(NodeView.DEFAULT)) : "New object stamp not compatible to piStamp";
    }

    /**
     * Casts an object to have an exact, non-null stamp representing {@link Class}.
     */
    public static Class<?> asNonNullClass(Object object) {
        return asNonNullClassIntrinsic(object, Class.class, true, true);
    }

    /**
     * Casts an object to have an exact, non-null stamp representing {@link Class}.
     */
    public static Class<?> asNonNullObject(Object object) {
        return asNonNullClassIntrinsic(object, Object.class, false, true);
    }

    @NodeIntrinsic(PiNode.class)
    private static native Class<?> asNonNullClassIntrinsic(Object object, @ConstantNodeParameter Class<?> toType, @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull);

    /**
     * Changes the stamp of an object inside a snippet to be the stamp of the node replaced by the
     * snippet.
     */
    @NodeIntrinsic(PiNode.Placeholder.class)
    public static native Object piCastToSnippetReplaceeStamp(Object object);

    /**
     * Changes the stamp of a primitive value and ensures the newly stamped value is positive and
     * does not float above a given guard.
     *
     * @param value an arbitrary {@code int} value
     * @param guard a node proving that {@code value >= 0} holds at some point in the graph
     *
     * @return the {@code value} with its stamp clamped to exclude negative values, guarded by
     *         {@code guard}
     */
    public static int piCastPositive(int value, GuardingNode guard) {
        return intrinsified(value, guard, IntrinsifyOp.POSITIVE_INT);
    }

    @NodeIntrinsic
    private static native int intrinsified(int value, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    /**
     * Changes the stamp of an object and ensures the newly stamped value is non-null and does not
     * float above a given guard.
     */
    public static Object piCastNonNull(Object object, GuardingNode guard) {
        return intrinsified(object, guard, IntrinsifyOp.NON_NULL);
    }

    @NodeIntrinsic
    private static native Object intrinsified(Object object, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    /**
     * Changes the stamp of an object and ensures the newly stamped value is non-null and does not
     * float above a given guard.
     */
    public static Class<?> piCastNonNullClass(Class<?> type, GuardingNode guard) {
        return intrinsified(type, guard, IntrinsifyOp.NON_NULL);
    }

    @NodeIntrinsic
    private static native Class<?> intrinsified(Class<?> object, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    /**
     * Changes the stamp of an object to represent a given type and to indicate that the object is
     * not null.
     */
    public static Object piCastNonNull(Object object, @ConstantNodeParameter ResolvedJavaType toType) {
        return piCast(object, toType, false, true);
    }

    @NodeIntrinsic
    public static native Object piCast(Object object, @ConstantNodeParameter ResolvedJavaType toType, @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull);

    /**
     * A placeholder node in a snippet that will be replaced with a {@link PiNode} when the snippet
     * is instantiated.
     */
    @NodeInfo(cycles = CYCLES_0, size = SIZE_0)
    public static class Placeholder extends FloatingGuardedNode {

        public static final NodeClass<Placeholder> TYPE = NodeClass.create(Placeholder.class);
        @Input ValueNode object;

        public ValueNode object() {
            return object;
        }

        protected Placeholder(NodeClass<? extends Placeholder> c, ValueNode object) {
            super(c, PlaceholderStamp.SINGLETON, null);
            this.object = object;
        }

        public Placeholder(ValueNode object) {
            this(TYPE, object);
        }

        /**
         * Replaces this node with a {@link PiNode} during snippet instantiation.
         *
         * @param snippetReplaceeStamp the stamp of the node being replace by the snippet
         */
        public void makeReplacement(Stamp snippetReplaceeStamp) {
            ValueNode value = graph().maybeAddOrUnique(PiNode.create(object(), snippetReplaceeStamp, null));
            replaceAndDelete(value);
        }
    }

    /**
     * A stamp for {@link Placeholder} nodes which are only used in snippets. It is replaced by an
     * actual stamp when the snippet is instantiated.
     */
    public static final class PlaceholderStamp extends ObjectStamp {
        private static final PlaceholderStamp SINGLETON = new PlaceholderStamp();

        public static PlaceholderStamp singleton() {
            return SINGLETON;
        }

        private PlaceholderStamp() {
            super(null, false, false, false, false);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public String toString() {
            return "PlaceholderStamp";
        }
    }
}
