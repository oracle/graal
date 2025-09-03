/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import java.util.List;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
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
public class PiNode extends FloatingGuardedNode implements LIRLowerable, Virtualizable, Canonicalizable, ValueProxy, IterableNodeType {

    public static final NodeClass<PiNode> TYPE = NodeClass.create(PiNode.class);
    @Input ValueNode object;
    protected Stamp piStamp;

    public ValueNode object() {
        return object;
    }

    @SuppressWarnings("this-escape")
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
        POSITIVE_INT,
        INT_NON_ZERO,
        LONG_NON_ZERO,
        DOUBLE_NON_NAN,
        FLOAT_NON_NAN
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
            case INT_NON_ZERO:
                piStamp = StampFactory.nonZeroInt();
                pushKind = JavaKind.Int;
                break;
            case LONG_NON_ZERO:
                piStamp = StampFactory.nonZeroLong();
                pushKind = JavaKind.Long;
                break;
            case FLOAT_NON_NAN:
                // non NAN float stamp
                piStamp = FloatStamp.create(Float.SIZE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, true);
                pushKind = JavaKind.Float;
                break;
            case DOUBLE_NON_NAN:
                // non NAN double stamp
                piStamp = FloatStamp.create(Double.SIZE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true);
                pushKind = JavaKind.Double;
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(intrinsifyOp); // ExcludeFromJacocoGeneratedReport
        }
        ValueNode value = canonical(input, piStamp, (GuardingNode) guard, null);
        if (value == null) {
            value = new PiNode(input, piStamp, guard);
        }
        b.push(pushKind, b.append(value));
        return true;
    }

    public static boolean intrinsify(GraphBuilderContext b, ValueNode object, ResolvedJavaType toType, boolean exactType, boolean nonNull, ValueNode guard) {
        Stamp stamp = StampFactory.object(exactType ? TypeReference.createExactTrusted(toType) : TypeReference.createWithoutAssumptions(toType),
                        nonNull || StampTool.isPointerNonNull(object.stamp(NodeView.DEFAULT)));
        ValueNode value = canonical(object, stamp, (GuardingNode) guard, null);
        if (value == null) {
            value = new PiNode(object, stamp, guard);
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

    public static ValueNode canonical(ValueNode object, Stamp piStamp, GuardingNode guard, ValueNode self) {
        /*
         * If canonicalization is not explicitly triggered, we need to be conservative and assume
         * that the graph is still building up and some usages/inputs of/to nodes are still missing.
         */
        return canonical(object, piStamp, guard, self, false);
    }

    public static ValueNode canonical(ValueNode object, Stamp piStamp, GuardingNode guard, ValueNode self, boolean allUsagesAvailable) {
        return canonical(object, piStamp, guard, self, allUsagesAvailable, true);
    }

    @SuppressFBWarnings(value = {"NP"}, justification = "We null check it before")
    public static ValueNode canonical(ValueNode object, Stamp piStamp, GuardingNode guard, ValueNode self, boolean allUsagesAvailable, boolean processUsage) {
        GraalError.guarantee(piStamp != null && object != null, "Invariant piStamp=%s object=%s guard=%s self=%s", piStamp, object, guard, self);

        // Use most up to date stamp.
        Stamp computedStamp = piStamp.improveWith(object.stamp(NodeView.DEFAULT));

        // The pi node does not give any additional information => skip it.
        if (computedStamp.equals(object.stamp(NodeView.DEFAULT))) {
            return object;
        }

        /*
         * Extracts a constant node if (1) this pi node's stamp is a non-null pointer stamp, (2)
         * 'object' is phi node that has only the same constant node and else just null constants as
         * inputs, and (3) if the constant's stamp equals this pi node's stamp.
         *
         * @formatter:off
         *  ...
         *   |
         * Merge
         *   |
         *   | C(object) null  null C(object) (...)
         *   |     |       |     |     |       |
         *   |     |       |     |     |       |
         *   |     |-------|-----+-----|-------|
         *   |                   |
         *   |----------------- phi
         *                       |
         *              piWithNonNullStamp
         *                       |
         *                      ...
         * @formatter:on
         *
         * Another important condition is that the phi node already has all inputs. During graph
         * decoding, it may happen that inputs are added later for loop phis. Therefore, either
         * 'allUsagesAvailable' is given or the phi node is not a loop phi.
         *
         * This canonicalization case has some overlap with other optimizations. In particular,
         * aggressive path duplication may get to a very similar result. Also, 'IfNode.splitIfAtPhi'
         * does a similar optimization but with different limitations. This should be considered
         * when touching them. One advantage of this canonicalization is that it happens immediately
         * when creating PiNodes.
         */
        if (StampFactory.objectNonNull().equals(piStamp) && object instanceof PhiNode phiNode && (allUsagesAvailable || !phiNode.isLoopPhi())) {
            ValueNode constantValue = null;
            for (ValueNode phiValue : phiNode.values()) {
                if (constantValue == null && phiValue.isConstant() && !phiValue.isNullConstant() && checkPhiValueStamp(piStamp, computedStamp, phiValue.stamp(NodeView.DEFAULT))) {
                    constantValue = phiValue;
                } else if (!phiValue.isNullConstant() && (constantValue == null || !isSameConstant(constantValue, phiValue))) {
                    // reset value to indicate that condition is violated
                    constantValue = null;
                    break;
                }
            }
            if (constantValue != null) {
                assert constantValue.isConstant() && !constantValue.isNullConstant() && checkPhiValueStamp(piStamp, computedStamp,
                                constantValue.stamp(NodeView.DEFAULT)) : Assertions.errorMessageContext("pi", self, "phi", phiNode, "constant", constantValue);
                return constantValue;
            }
        }

        if (guard == null) {
            // Try to merge the pi node with a load node.
            if (object instanceof ReadNode && !object.hasMoreThanOneUsage()) {
                ReadNode readNode = (ReadNode) object;
                readNode.setStamp(readNode.stamp(NodeView.DEFAULT).improveWith(piStamp));
                return readNode;
            }
        } else {
            if (processUsage && self instanceof PiNode selfPi) {
                for (Node n : guard.asNode().usages()) {
                    if (n instanceof PiNode && n != self) {
                        PiNode otherPi = (PiNode) n;
                        ValueNode canonByOtherPi = tryImproveWithOtherPi(selfPi, otherPi);
                        if (canonByOtherPi != null) {
                            return canonByOtherPi;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Canonicalizes a {@link PiNode} by comparing it with another {@link PiNode} based on guards,
     * objects, and stamps. Returns a replacement node if simplification or precision improvement is
     * possible.
     */
    public static ValueNode tryImproveWithOtherPi(PiNode p1, PiNode p2) {
        GuardingNode guard = p1.guard;
        if (p2.guard != guard) {
            // though p1 and p2 use guard they do it on different edges, one as value the other as
            // guard (or vice versa)
            return null;
        }
        if (p2.object() == p1 || p2.object() == p1.object) {
            // Check if other pi's stamp is more precise
            Stamp joinedPiStamp = p1.piStamp.improveWith(p2.piStamp());
            if (joinedPiStamp.equals(p1.piStamp)) {
                // Stamp did not get better, nothing to do.
            } else if (p2.object() == p1.object && joinedPiStamp.equals(p2.piStamp())) {
                // We can replace p1 with the other pi.
                return p2;
            } else if (p1.hasExactlyOneUsage() && p2.object == p1) {
                if (joinedPiStamp.equals(p2.piStamp)) {
                    return p1.object;
                }
            }
        }
        return null;
    }

    /**
     * Pi-phi canonicalization is only allowed if the stamp of the phi: (1) is an object stamp
     * (because PiNodes on primitive values are flaky), (2) the computed stamp is equal to the phi
     * value's stamp, and (3) the stamp of the PiNode does not add any information.
     */
    private static boolean checkPhiValueStamp(Stamp piStamp, Stamp computedStamp, Stamp phiValueStamp) {
        return phiValueStamp.isObjectStamp() && computedStamp.equals(phiValueStamp) && piStamp.join(phiValueStamp).equals(phiValueStamp);
    }

    private static boolean isSameConstant(ValueNode referenceValue, ValueNode phiValue) {
        assert referenceValue.isConstant() && !referenceValue.isNullConstant() : "reference value must be constant but not the null constant";
        assert referenceValue.asConstant() != null : "constant must not be null";
        return phiValue.isConstant() && referenceValue.asConstant().equals(phiValue.asConstant());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        // usages run during canonicalization
        final boolean processUsages = false;
        Node value = canonical(object(), piStamp(), getGuard(), this, tool.allUsagesAvailable(), processUsages);
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

    /**
     * Perform Pi canonicalizations on any PiNodes anchored at {@code guard} in an attempt to
     * eliminate all of them. This purely done to enable earlier elimination of the user of these
     * PiNodes.
     */
    public static void tryEvacuate(SimplifierTool tool, GuardingNode guard) {
        tryEvacuate(tool, guard, true);
    }

    private static void tryEvacuate(SimplifierTool tool, GuardingNode guard, boolean recurse) {
        ValueNode guardNode = guard.asNode();
        if (guardNode.hasNoUsages()) {
            return;
        }

        List<PiNode> pis = guardNode.usages().filter(PiNode.class).snapshot();
        for (PiNode pi : pis) {
            if (!pi.isAlive()) {
                continue;
            }
            if (pi.hasNoUsages()) {
                pi.safeDelete();
                continue;
            }

            /*
             * RECURSE CALL
             *
             * If there are PiNodes still anchored at this guard then either they must simplify away
             * because they are no longer necessary or this node must be replaced with a
             * ValueAnchorNode because the type injected by the PiNode is only true at this point in
             * the control flow.
             */
            if (recurse && pi.getOriginalNode() instanceof PiNode) {
                // It's not uncommon for one extra level of PiNode to inhibit removal of
                // this PiNode so try to simplify the input first.
                GuardingNode otherGuard = ((PiNode) pi.getOriginalNode()).guard;
                if (otherGuard != null) {
                    tryEvacuate(tool, otherGuard, false);
                }
            }
            /*
             * A note on the RECURSE CALL above: When we have pis with input pis on the same guard
             * (which should actually be combined) it can be that the recurse call (processing the
             * same pis again) already deletes this node (very special stamp setups necessary).
             * Thus, it can be that pi is dead at this point already, so we have to check for this
             * again.
             */
            if (!pi.isAlive()) {
                continue;
            }
            Node canonical = pi.canonical(tool);
            if (canonical != pi) {
                if (!canonical.isAlive()) {
                    canonical = guardNode.graph().addOrUniqueWithInputs(canonical);
                }
                pi.replaceAtUsages(canonical);
                pi.safeDelete();
            }
        }
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

    public static int piCastNonZero(int value, GuardingNode guard) {
        return intrinsified(value, guard, IntrinsifyOp.INT_NON_ZERO);
    }

    public static long piCastNonZero(long value, GuardingNode guard) {
        return intrinsified(value, guard, IntrinsifyOp.LONG_NON_ZERO);
    }

    @NodeIntrinsic
    private static native int intrinsified(int value, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    @NodeIntrinsic
    private static native long intrinsified(long value, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

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

    public static float piCastNonNanFloat(float input, GuardingNode guard) {
        return intrinsified(input, guard, IntrinsifyOp.FLOAT_NON_NAN);
    }

    @NodeIntrinsic
    private static native float intrinsified(float input, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    public static double piCastNonNanDouble(double input, GuardingNode guard) {
        return intrinsified(input, guard, IntrinsifyOp.DOUBLE_NON_NAN);
    }

    @NodeIntrinsic
    private static native double intrinsified(double input, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    @NodeIntrinsic
    private static native Class<?> intrinsified(Class<?> object, GuardingNode guard, @ConstantNodeParameter IntrinsifyOp intrinsifyOp);

    @NodeIntrinsic
    public static native Object piCast(Object object, @ConstantNodeParameter ResolvedJavaType toType,
                    @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull, GuardingNode guard);

    @NodeIntrinsic
    public static native Object piCast(Object object, @ConstantNodeParameter Class<?> toType,
                    @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull, GuardingNode guard);

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
            ValueNode value = graph().addOrUnique(PiNode.create(object(), snippetReplaceeStamp, null));
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

    /**
     * Maximum number of usage iterations per guard for
     * {@link PiNode#guardTrySkipPi(GuardingNode, LogicNode, boolean, NodeView)}.
     */
    private static final int MAX_PI_USAGE_ITERATIONS = 8;

    /**
     * Optimize a (Fixed)Guard-condition-pi pattern as a whole: note that this is different than
     * conditional elimination because here we detect exhaustive patterns and optimize them as a
     * whole. This is hard to express in CE as we optimize both a pi and its condition in one go.
     * There is no dedicated optimization phase in graal that does this, therefore we build on
     * simplification as a more non-local transformation.
     *
     * We are looking for the following pattern
     *
     * <pre>
     *               inputPiObject
     *               |
     *               inputPi-----------
     *               |                 |
     *               |                 |
     *            usagePiCondition     |
     *               |                 |
     *          (fixed) guard          |
     *               |                 |
     *               usagePi-----------
     * </pre>
     *
     * and we optimize the condition and the pi together to use inputPi's input if inputPi does not
     * contribute any knowledge to usagePi. This means that inputPi is totally skipped. If both
     * inputPi and usagePi ultimately work on the same input (un-pi-ed) then later conditional
     * elimination can cleanup inputPi's guard if applicable.
     *
     * The resulting IR pattern will look like this:
     *
     * <pre>
     *               inputPiObject------
     *               |                 |
     *               |                 |
     *            resultPiCondition    |
     *               |                 |
     *          (fixed) guard          |
     *               |                 |
     *               resultPi-----------
     * </pre>
     *
     * Note: this optimization does not work for subtypes of PiNode like {@link DynamicPiNode} as
     * their stamps are not yet known.
     *
     * A source code pattern illustrating the optimization can be found below
     *
     * <pre>
     * class A {
     * }
     *
     * class B {
     * }
     *
     * void foo(Object o) {
     *     A bar = (A) o;
     *     B baz = (B) bar;
     *     use(b);
     * }
     * </pre>
     *
     * For the user of {@code baz} its not relevant to go over the first cast of {@code o} to
     * {@code A} since the condition of the second cast ({@code bar instanceof B}) already proves a
     * more concrete type than the first condition. We can skip the first cast of {@code o} to
     * {@code A} and later {@link ConditionalEliminationPhase} may be able to fully remove the first
     * condition.
     *
     * Note on conditional elimination and guard pi skipping: this optimization of skipping PIs DOES
     * NOT delete any guard nodes. So even if we skip pi nodes we are never removing guards. We are
     * only skipping pis if a later condition proves more knowledge than an earlier one. Conditional
     * elimination is the only phase that can prove that an earlier guard can be deleted because of
     * a later one.
     */
    public static boolean guardTrySkipPi(GuardingNode guard, LogicNode condition, boolean negated, NodeView nodeView) {
        if (!(guard instanceof FixedGuardNode || guard instanceof GuardNode || guard instanceof BeginNode)) {
            /*
             * Unknown guard node: do not attempt to perform this transformation.
             */
            return false;
        }
        final LogicNode usagePiCondition = condition;
        if (usagePiCondition.inputs().filter(PiNode.class).isEmpty()) {
            return false;
        }
        int iterations = 0;
        boolean progress = true;
        int pisSkipped = 0;
        outer: while (progress && iterations++ < MAX_PI_USAGE_ITERATIONS) {
            progress = false;
            // look for the pattern from the javadoc
            for (PiNode usagePi : piUsageSnapshot(guard)) {
                /*
                 * Restrict this optimization to regular pi nodes only - sub classes of pi nodes
                 * implement delayed pi stamp computation or other optimizations and should thus not
                 * be skipped.
                 */
                if (!usagePi.isRegularPi()) {
                    continue;
                }
                final ValueNode usagePiObject = usagePi.object();
                final boolean usagePiObjectRegularPi = usagePiObject instanceof PiNode inputPi && inputPi.isRegularPi();
                if (!usagePiObjectRegularPi) {
                    continue;
                }
                // ensure usagePiObject is also used in the usagePiCondition
                if (!usagePiCondition.inputs().contains(usagePiObject)) {
                    continue;
                }
                final Stamp usagePiPiStamp = usagePi.piStamp();
                final PiNode inputPi = (PiNode) usagePiObject;

                /*
                 * Ensure that the pi actually "belongs" to this guard in the sense that the
                 * succeeding stamp for the guard is actually the pi stamp.
                 */
                Stamp succeedingStamp = null;
                if (usagePiCondition instanceof UnaryOpLogicNode uol) {
                    succeedingStamp = uol.getSucceedingStampForValue(negated);
                } else if (usagePiCondition instanceof BinaryOpLogicNode bol) {
                    if (bol.getX() == inputPi) {
                        succeedingStamp = bol.getSucceedingStampForX(negated, bol.getX().stamp(nodeView), bol.getY().stamp(nodeView).unrestricted());
                    } else if (bol.getY() == inputPi) {
                        succeedingStamp = bol.getSucceedingStampForY(negated, bol.getX().stamp(nodeView).unrestricted(), bol.getY().stamp(nodeView));
                    }
                }
                final boolean piProvenByCondition = succeedingStamp != null && usagePiPiStamp.equals(succeedingStamp);
                if (!piProvenByCondition) {
                    continue;
                }

                /*
                 * We want to find out if the inputPi can be skipped because usagePi's guard and pi
                 * stamp prove enough knowledge to actually skip inputPi completely. This can be
                 * relevant for complex type check patterns and interconnected pis: conditional
                 * elimination cannot enumerate all values thus we try to free up local patterns
                 * early by skipping unnecessary pis.
                 */
                final Stamp inputPiPiStamp = inputPi.piStamp();
                final Stamp inputPiObjectStamp = inputPi.object().stamp(nodeView);
                /*
                 * Determine if the stamp from inputPiObject & usagePi.piStamp is equally strong
                 * than the usagePi.piStamp, then we can build a new pi that skips the inputPi.
                 */
                final Stamp resultStampWithInputPiObjectOnly = usagePiPiStamp.improveWith(inputPiObjectStamp);
                /*
                 * The final pi will skip inputPi completely, thus inputPi.piStamp MUST NOT
                 * contribute ANY additional stamp information, i.e.,
                 * resultStampWithInputPiObjectOnly cannot become any more precise through it.
                 */
                final boolean resultPiEquallyStrongWithoutInputPiPiStamp = resultStampWithInputPiObjectOnly.tryImproveWith(inputPiPiStamp) == null;
                if (resultPiEquallyStrongWithoutInputPiPiStamp) {
                    // The input pi's object stamp was strong enough so we can skip the input pi.
                    final ValueNode resultPi = usagePiCondition.graph().addOrUnique(PiNode.create(inputPi.object(), usagePiPiStamp, usagePi.getGuard().asNode()));
                    final LogicNode resultPiCondition = (LogicNode) usagePiCondition.copyWithInputs(true);
                    resultPiCondition.replaceAllInputs(usagePiObject, inputPi.object());
                    if (guard.asNode() instanceof FixedGuardNode fg) {
                        fg.setCondition(resultPiCondition, negated);
                    } else if (guard.asNode() instanceof GuardNode floatingGuard) {
                        floatingGuard.setCondition(resultPiCondition, negated);
                    } else if (guard.asNode() instanceof BeginNode) {
                        ((IfNode) guard.asNode().predecessor()).setCondition(resultPiCondition);
                    } else {
                        GraalError.shouldNotReachHere("Unknown guard " + guard);
                    }
                    usagePi.replaceAndDelete(resultPi);
                    progress = true;
                    pisSkipped++;
                    continue outer;
                }
            }
        }
        return pisSkipped > 0;
    }

    private boolean isRegularPi() {
        return getClass() == PiNode.class;
    }

    private static Iterable<PiNode> piUsageSnapshot(GuardingNode guard) {
        return guard.asNode().usages().filter(PiNode.class).snapshot();
    }
}
