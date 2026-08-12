/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.dfanalysis.analyses;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.TriState;

/**
 * This interface is the root of a little inheritance hierarchy for elements of the domain of
 * pentagons. The domain of pentagons knows four different types of values: logic values
 * ({@link LogicPentagon}), objects({@link ObjectPentagon}), floating point numbers
 * ({@link FloatPentagon}), and integers ({@link IntegerPentagon}). Each type forms a sublattice in
 * the domain. The domain of pentagons was first introduced in the paper
 * <a href="https://dl.acm.org/doi/10.1145/3679007.3685059">Pentagons: a weakly relational abstract
 * domain for the efficient validation of array accesses</a>. In addition to numeric bounds for
 * integers, it also captures symbolic strict upper bound relationships between variables like
 * {@code x < y}.
 */
public sealed interface Pentagon {

    sealed interface StampPentagon extends Pentagon {
        Stamp getStamp();
    }

    static LogicPentagon of(boolean logic) {
        return logic ? LogicPentagon.TRUE : LogicPentagon.FALSE;
    }

    static LogicPentagon of(TriState logic) {
        return logic == null ? LogicPentagon.UNEVALUATED : switch (logic) {
            case TRUE -> LogicPentagon.TRUE;
            case FALSE -> LogicPentagon.FALSE;
            case UNKNOWN -> LogicPentagon.UNRESTRICTED;
        };
    }

    static IntegerPentagon of(IntegerStamp range, Set<ValueNode> lowerBounds, Set<ValueNode> strictUpperBounds) {
        // canonicalize all impossible values to the same UNEVALUATED
        if (range.isEmpty() || lowerBounds == null || strictUpperBounds == null) {
            return IntegerPentagon.UNEVALUATED[CodeUtil.log2(range.getBits())];
        }
        // do the cheap check first
        if (range.isUnrestricted() && lowerBounds.isEmpty() && strictUpperBounds.isEmpty()) {
            return IntegerPentagon.UNRESTRICTED[CodeUtil.log2(range.getBits())];
        }
        // finally do the little more expensive check impossible values
        // (we need to canonicalize those to UNEVALUATED)
        for (ValueNode sub : strictUpperBounds) {
            if (lowerBounds.contains(sub)) {
                // this is an impossible value
                return IntegerPentagon.UNEVALUATED[CodeUtil.log2(range.getBits())];
            }
        }
        // neither UNEVALUATED nor UNRESTRICTED, we need a new instance
        return new IntegerPentagon(range, lowerBounds, strictUpperBounds);
    }

    static FloatPentagon of(FloatStamp stamp) {
        if (stamp.getBits() == Float.SIZE) {
            if (stamp.isEmpty()) {
                return FloatPentagon.FLOAT_UNEVALUATED;
            } else if (stamp.isUnrestricted()) {
                return FloatPentagon.FLOAT_UNRESTRICTED;
            }
        } else {
            assert stamp.getBits() == Double.SIZE : "Unknown floating point bit size " + stamp.getBits();
            if (stamp.isEmpty()) {
                return FloatPentagon.DOUBLE_UNEVALUATED;
            } else if (stamp.isUnrestricted()) {
                return FloatPentagon.DOUBLE_UNRESTRICTED;
            }
        }
        return new FloatPentagon(stamp);
    }

    static ObjectPentagon of(AbstractObjectStamp stamp) {
        return Pentagon.of(stamp, null);
    }

    static ObjectPentagon of(AbstractObjectStamp stamp, Constant constant) {
        return new ObjectPentagon(stamp, constant);
    }

    static ObjectPentagon of(ConstantNode constant) {
        GraalError.guarantee(constant.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp aost && aost.isExactType(),
                        "Cannot create an ObjectPentagon from non-object constant node or inexact typed stamp %s", constant.stamp(NodeView.DEFAULT));
        return new ObjectPentagon((AbstractObjectStamp) constant.stamp(NodeView.DEFAULT), constant.getValue());
    }

    static Pentagon ofGeneralStamp(Stamp stamp) {
        return switch (stamp) {
            case IntegerStamp iStamp -> Pentagon.of(iStamp, CollectionsUtil.setOf(), CollectionsUtil.setOf());
            case FloatStamp fStamp -> Pentagon.of(fStamp);
            case AbstractObjectStamp oStamp -> Pentagon.of(oStamp);
            default -> throw GraalError.shouldNotReachHere("Cannot convert Stamp %s to Pentagon".formatted(stamp));
        };
    }

    default LogicPentagon asLogic() {
        throw new UnsupportedOperationException("cannot convert %s to LogicPentagon".formatted(this.getClass().getName()));
    }

    default IntegerPentagon asInteger() {
        throw new UnsupportedOperationException("cannot convert %s to IntegerPentagon".formatted(this.getClass().getName()));
    }

    default FloatPentagon asFloat() {
        throw new UnsupportedOperationException("cannot convert %s to FloatPentagon".formatted(this.getClass().getName()));
    }

    default ObjectPentagon asObject() {
        throw new UnsupportedOperationException("cannot convert %s to ObjectPentagon".formatted(this.getClass().getName()));
    }

    default StampPentagon asStamp() {
        if (this instanceof StampPentagon sPtg) {
            return sPtg;
        } else {
            throw new UnsupportedOperationException("cannot convert %s to ObjectPentagon".formatted(this.getClass().getName()));
        }
    }

    boolean isUnevaluated();

    boolean isUnrestricted();

    Pentagon merge(Pentagon other);

    Pentagon strengthen(Pentagon other);

    boolean isConstant();

    /**
     * A value in the domain of pentagons representing a boolean value.
     */
    final class LogicPentagon implements Pentagon {
        public final TriState logic;

        private LogicPentagon(TriState logic) {
            this.logic = logic;
        }

        @Override
        public LogicPentagon asLogic() {
            return this;
        }

        @Override
        public boolean isUnevaluated() {
            return logic == null;
        }

        @Override
        public boolean isUnrestricted() {
            return logic != null && logic.isUnknown();
        }

        @Override
        public LogicPentagon merge(Pentagon o) {
            GraalError.guarantee(o instanceof LogicPentagon, "merging unmergable Pentagons %s and %s", this, o);
            LogicPentagon other = o.asLogic();
            if (isUnevaluated() || other.isUnrestricted()) {
                return other;
            } else if (other.isUnevaluated() || isUnrestricted() || equals(other)) {
                return this;
            }
            assert logic.isKnown() && other.logic.isKnown() && logic != other.logic : "expected TRUE merge FALSE (or FALSE merge TRUE), got %s merge %s".formatted(logic.name(), other.logic.name());
            return UNRESTRICTED;
        }

        @Override
        public LogicPentagon strengthen(Pentagon o) {
            GraalError.guarantee(o instanceof LogicPentagon, "strengthening Pentagon %s with incompatible %s", this, o);
            LogicPentagon other = o.asLogic();
            if (isUnevaluated() || other.isUnrestricted() || equals(other)) {
                return this;
            } else if (other.isUnevaluated() || isUnrestricted()) {
                return other;
            }
            assert logic.isKnown() && other.logic.isKnown() &&
                            logic != other.logic : "expected TRUE strengthen FALSE (or FALSE strengthen TRUE), got %s strengthen %s".formatted(logic.name(), other.logic.name());
            return UNEVALUATED;
        }

        @Override
        public boolean isConstant() {
            return logic != null && logic.isKnown();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof LogicPentagon otherPentagon && logic == otherPentagon.logic;
        }

        @Override
        public int hashCode() {
            return logic == null ? -1 : logic.ordinal();
        }

        @Override
        public String toString() {
            return switch (logic) {
                case null -> "Pntg<Logic>{unevaluated}";
                case TRUE -> "Pntg<Logic>{true}";
                case FALSE -> "Pntg<Logic>{false}";
                case UNKNOWN -> "Pntg<Logic>{unrestricted}";
            };
        }

        public static final LogicPentagon UNEVALUATED = new LogicPentagon(null);
        public static final LogicPentagon TRUE = new LogicPentagon(TriState.TRUE);
        public static final LogicPentagon FALSE = new LogicPentagon(TriState.FALSE);
        public static final LogicPentagon UNRESTRICTED = new LogicPentagon(TriState.UNKNOWN);
    }

    /**
     * <p>
     * This is the most complex element in the domain of pentagons. In the original paper, a
     * variable has a range which is described by numeric bounds and a set of other values that form
     * strict upper bounds to the given variable. The range is represented using a standard integer
     * stamp. The set of strict upper bounds in our case is a set of nodes. The framework operates
     * on a per-node basis and the transfer function can only return an abstract value that is then
     * associated with the node at hand and not a different node. Therefore, we also need to be able
     * to represent the inverse direction when the node we are currently evaluating is a strict
     * upper bound to another node. This could be represented as a set of strict lower bounds.
     * </p>
     * <p>
     * But that still misses operations like proxies or phis with a singular reachable input, which
     * do not change the value. Consider the following example:
     * </p>
     *
     * <pre>
     * int a = array.length - 1
     * int len = proxy(array.length)
     * prove a < len
     * </pre>
     *
     * <p>
     * Initially, we recognize that the array length is an upper bound to the result of the
     * addition, namely {@code a}. Evaluating the proxy, we would ideally add the output of the
     * proxy ({@code len}) to the strict upper bounds of {@code a}, but this is not possible in the
     * framework. Since the proxy leaves the value unchanged, the array length is neither a strict
     * upper nor a strict lower bound to {@code len}. Therefore, proving {@code a < len} would be
     * impossible here.
     * </p>
     * <p>
     * As a compromise between complexity and precision, we add the "may be equal" information to
     * the strict lower bounds set, yielding a set of non-strict lower bounds in
     * {@link IntegerPentagon#lowerBounds}. Now {@code len} has the array length as a non-strict
     * lower bound. To prove {@code a < len}, we check for a non-empty set intersection between the
     * strict upper bounds of {@code a} and the non-strict lower bounds of {@code len}. With this we
     * can find that {@code a < array.length <= len}, with which we can prove this example.
     * </p>
     */
    final class IntegerPentagon implements StampPentagon {
        public final IntegerStamp range;
        public final Set<ValueNode> lowerBounds;
        public final Set<ValueNode> strictUpperBounds;

        private IntegerPentagon(IntegerStamp range, Set<ValueNode> lowerBounds, Set<ValueNode> strictUpperBounds) {
            this.range = range;
            this.lowerBounds = lowerBounds;
            this.strictUpperBounds = strictUpperBounds;
        }

        public static boolean isLowerEqual(ValueNode lower, IntegerPentagon lowerPtg, ValueNode upper, IntegerPentagon upperPtg) {
            if (lowerPtg.range.upperBound() <= upperPtg.range.lowerBound() || upperPtg.lowerBounds.contains(lower)) {
                return true;
            }
            return isLowerThan(lowerPtg, upper, upperPtg);
        }

        public static boolean isLowerThan(IntegerPentagon lowerPtg, ValueNode upper, IntegerPentagon upperPtg) {
            if (lowerPtg.isUnrestricted() || upperPtg.isUnrestricted()) {
                // we cannot prove anything for unrestricted inputs
                return false;
            }
            if (lowerPtg.range.upperBound() < upperPtg.range.lowerBound() || lowerPtg.strictUpperBounds.contains(upper)) {
                return true;
            }
            for (ValueNode sub : lowerPtg.strictUpperBounds) {
                if (upperPtg.lowerBounds.contains(sub)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public IntegerStamp getStamp() {
            return range;
        }

        @Override
        public IntegerPentagon asInteger() {
            return this;
        }

        @Override
        public boolean isUnevaluated() {
            return range.isEmpty() && lowerBounds == null && strictUpperBounds == null;
        }

        @Override
        public boolean isUnrestricted() {
            return range.isUnrestricted() && lowerBounds.isEmpty() && strictUpperBounds.isEmpty();
        }

        @Override
        public IntegerPentagon merge(Pentagon o) {
            GraalError.guarantee(o instanceof IntegerPentagon, "merging unmergable Pentagons %s and %s", this, o);
            IntegerPentagon other = o.asInteger();
            if (isUnevaluated() || other.isUnrestricted()) {
                return other;
            } else if (other.isUnevaluated() || isUnrestricted() || equals(other)) {
                return this;
            }
            // merge ranges
            IntegerStamp nuRange = (IntegerStamp) range.meet(other.range);
            // merge strict upper bounds
            Set<ValueNode> nuLBs;
            if (lowerBounds == null) {
                nuLBs = other.lowerBounds;
            } else if (other.lowerBounds == null) {
                nuLBs = lowerBounds;
            } else if (lowerBounds.isEmpty() || other.lowerBounds.isEmpty()) {
                nuLBs = CollectionsUtil.setOf();
            } else {
                nuLBs = new EconomicHashSet<>(lowerBounds);
                nuLBs.retainAll(other.lowerBounds);
                if (nuLBs.isEmpty()) {
                    nuLBs = CollectionsUtil.setOf();
                }
            }
            // merge strict upper bounds
            Set<ValueNode> nuSUBs;
            if (strictUpperBounds == null) {
                nuSUBs = other.strictUpperBounds;
            } else if (other.strictUpperBounds == null) {
                nuSUBs = strictUpperBounds;
            } else if (strictUpperBounds.isEmpty() || other.strictUpperBounds.isEmpty()) {
                nuSUBs = CollectionsUtil.setOf();
            } else {
                nuSUBs = new EconomicHashSet<>(strictUpperBounds);
                nuSUBs.retainAll(other.strictUpperBounds);
                if (nuSUBs.isEmpty()) {
                    nuSUBs = CollectionsUtil.setOf();
                }
            }
            return Pentagon.of(nuRange, nuLBs, nuSUBs);
        }

        @Override
        public IntegerPentagon strengthen(Pentagon o) {
            GraalError.guarantee(o instanceof IntegerPentagon, "strengthening Pentagon %s with incompatible %s", this, o);
            IntegerPentagon other = o.asInteger();
            if (isUnevaluated() || other.isUnrestricted() || equals(other)) {
                return this;
            } else if (other.isUnevaluated() || isUnrestricted()) {
                return other;
            }
            // strengthen ranges
            IntegerStamp nuRange = range.join(other.range);
            // strengthen lower bounds
            Set<ValueNode> nuLBs;
            if (lowerBounds == null || other.lowerBounds == null) {
                nuLBs = null;
            } else if (lowerBounds.isEmpty()) {
                nuLBs = other.lowerBounds;
            } else if (other.lowerBounds.isEmpty()) {
                nuLBs = lowerBounds;
            } else {
                nuLBs = new EconomicHashSet<>(lowerBounds);
                nuLBs.addAll(other.lowerBounds);
            }
            // strengthen strict upper bounds
            Set<ValueNode> nuSUBs;
            if (strictUpperBounds == null || other.strictUpperBounds == null) {
                nuSUBs = null;
            } else if (strictUpperBounds.isEmpty()) {
                nuSUBs = other.strictUpperBounds;
            } else if (other.strictUpperBounds.isEmpty()) {
                nuSUBs = strictUpperBounds;
            } else {
                nuSUBs = new EconomicHashSet<>(strictUpperBounds);
                nuSUBs.addAll(other.strictUpperBounds);
                // TODO maybe drop that because we don't modify the set anyway at any other point
                nuSUBs = Collections.unmodifiableSet(nuSUBs);
            }
            return Pentagon.of(nuRange, nuLBs, nuSUBs);
        }

        @Override
        public boolean isConstant() {
            return range.isConstant();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return o instanceof IntegerPentagon other &&
                            range.equals(other.range) &&
                            Objects.equals(lowerBounds, other.lowerBounds) &&
                            Objects.equals(strictUpperBounds, other.strictUpperBounds);
        }

        @Override
        public int hashCode() {
            int hash = range == null ? -1 : range.hashCode();
            if (lowerBounds == null) {
                hash = hash * 31 - 1;
            } else {
                for (ValueNode lb : lowerBounds) {
                    hash = hash * 31 + lb.hashCode();
                }
            }
            if (strictUpperBounds == null) {
                hash = hash * 31 - 1;
            } else {
                for (ValueNode sub : strictUpperBounds) {
                    hash = hash * 31 + sub.hashCode();
                }
            }
            return hash;
        }

        @Override
        @SuppressWarnings("deprecation")
        public String toString() {
            var sb = new StringBuilder().append('[');
            if (lowerBounds == null) {
                sb.append("ALL");
            } else if (lowerBounds.isEmpty()) {
                sb.append("NONE");
            } else {
                boolean first = true;
                for (ValueNode lb : lowerBounds) {
                    if (!first) {
                        sb.append(", ");
                    } else {
                        first = false;
                    }
                    String clsName = lb.getClass().getSimpleName();
                    if (clsName.endsWith("Node")) {
                        clsName = clsName.substring(0, clsName.length() - "Node".length());
                    }
                    sb.append(lb.getId()).append('|').append(clsName);
                }
            }
            sb.append(']');
            boolean isValid = true;
            String lbs = sb.toString();
            sb = new StringBuilder().append('[');
            if (strictUpperBounds == null) {
                sb.append("ALL");
            } else if (strictUpperBounds.isEmpty()) {
                sb.append("NONE");
            } else {
                boolean first = true;
                for (ValueNode sub : strictUpperBounds) {
                    if (lowerBounds.contains(sub)) {
                        isValid = false;
                    }
                    if (!first) {
                        sb.append(", ");
                    } else {
                        first = false;
                    }
                    String clsName = sub.getClass().getSimpleName();
                    if (clsName.endsWith("Node")) {
                        clsName = clsName.substring(0, clsName.length() - "Node".length());
                    }
                    sb.append(sub.getId()).append('|').append(clsName);
                }
            }
            sb.append(']');
            String subs = sb.toString();
            return isValid ? "Pntg<Int>{%s; %s <= this < %s}".formatted(range, lbs, subs)
                            : "Pntg<Int><INVALID>{%s; %s <= this < %s}".formatted(range, lbs, subs);
        }

        public static final IntegerPentagon[] UNEVALUATED = new IntegerPentagon[CodeUtil.log2(64) + 1];
        public static final IntegerPentagon[] UNRESTRICTED = new IntegerPentagon[CodeUtil.log2(64) + 1];

        static {
            for (int logBits = 0; logBits < UNRESTRICTED.length; logBits++) {
                UNRESTRICTED[logBits] = new IntegerPentagon(IntegerStamp.create(1 << logBits), CollectionsUtil.setOf(), CollectionsUtil.setOf());
            }
            for (int logBits = 0; logBits < UNEVALUATED.length; logBits++) {
                UNEVALUATED[logBits] = new IntegerPentagon(IntegerStamp.createEmptyStamp(1 << logBits), null, null);
            }
        }
    }

    /**
     * A wrapper of float stamps to make them accessible for the domain of pentagons.
     */
    final class FloatPentagon implements StampPentagon {
        public final FloatStamp stamp;

        private FloatPentagon(FloatStamp stamp) {
            this.stamp = stamp;
        }

        @Override
        public FloatStamp getStamp() {
            return stamp;
        }

        @Override
        public FloatPentagon asFloat() {
            return this;
        }

        @Override
        public boolean isUnevaluated() {
            return stamp.isEmpty();
        }

        @Override
        public boolean isUnrestricted() {
            return stamp.isUnrestricted();
        }

        @Override
        public FloatPentagon merge(Pentagon o) {
            GraalError.guarantee(o instanceof FloatPentagon, "merging unmergable Pentagons %s and %s", this, o);
            FloatPentagon other = o.asFloat();
            if (other == this) {
                return this;
            } else if (isUnrestricted() || other.isUnevaluated()) {
                return this;
            } else if (other.isUnrestricted() || isUnevaluated()) {
                return other;
            }
            return Pentagon.of((FloatStamp) stamp.meet(other.stamp));
        }

        @Override
        public FloatPentagon strengthen(Pentagon o) {
            GraalError.guarantee(o instanceof FloatPentagon, "strengthening Pentagon %s with incompatible %s", this, o);
            FloatPentagon other = o.asFloat();
            if (other == this) {
                return this;
            } else if (isUnevaluated() || other.isUnrestricted()) {
                return this;
            } else if (other.isUnevaluated() || isUnrestricted()) {
                return other;
            }
            return Pentagon.of((FloatStamp) stamp.join(other.stamp));
        }

        @Override
        public boolean isConstant() {
            return stamp.isConstant();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            return stamp.equals(((FloatPentagon) o).stamp);
        }

        @Override
        public int hashCode() {
            return stamp.hashCode();
        }

        @Override
        public String toString() {
            return "Pntg<Float>{%s}".formatted(stamp);
        }

        public static final FloatPentagon FLOAT_UNEVALUATED = new FloatPentagon(FloatStamp.createEmpty(Float.SIZE));
        public static final FloatPentagon FLOAT_UNRESTRICTED = new FloatPentagon(FloatStamp.createUnrestricted(Float.SIZE));

        public static final FloatPentagon DOUBLE_UNEVALUATED = new FloatPentagon(FloatStamp.createEmpty(Double.SIZE));
        public static final FloatPentagon DOUBLE_UNRESTRICTED = new FloatPentagon(FloatStamp.createUnrestricted(Double.SIZE));
    }

    /**
     * This domain element mainly wraps an object stamp to make them usable in the domain of
     * pentagons. In addition to that, object pentagons are also capable of representing non-null
     * object constants.
     */
    final class ObjectPentagon implements StampPentagon {
        public final AbstractObjectStamp stamp;
        public final Constant constant; // nullable

        private ObjectPentagon(AbstractObjectStamp stamp, Constant constant) {
            this.stamp = Objects.requireNonNull(stamp, "Stamp of ObjectPentagon must not be null!");
            this.constant = constant;
        }

        @Override
        public AbstractObjectStamp getStamp() {
            return stamp;
        }

        @Override
        public ObjectPentagon asObject() {
            return this;
        }

        @Override
        public boolean isUnevaluated() {
            return stamp.isEmpty();
        }

        @Override
        public boolean isUnrestricted() {
            return stamp.isUnrestricted();
        }

        @Override
        public ObjectPentagon merge(Pentagon o) {
            GraalError.guarantee(o instanceof ObjectPentagon, "merging unmergable Pentagons %s and %s", this, o);
            ObjectPentagon other = o.asObject();
            if (equals(other)) {
                return this;
            } else if (isUnrestricted() || other.isUnevaluated()) {
                return this;
            } else if (other.isUnrestricted() || isUnevaluated()) {
                return other;
            }
            /*
             * We do not need a special case for constants here. To obtain a constant result from a
             * merge one either needs 2 instances of the same constant (covered by the equals case),
             * or one constant and one unevaluated value (covered by the other two cases).
             * Therefore, we simply merge stamps here.
             */
            return Pentagon.of((AbstractObjectStamp) stamp.meet(other.stamp));
        }

        @Override
        public ObjectPentagon strengthen(Pentagon o) {
            GraalError.guarantee(o instanceof ObjectPentagon, "strengthening Pentagon %s with incompatible %s", this, o);
            ObjectPentagon other = o.asObject();
            if (equals(other)) {
                return this;
            } else if (isUnrestricted() || other.isUnevaluated()) {
                return other;
            } else if (other.isUnrestricted() || isUnevaluated()) {
                return this;
            }
            /*
             * In contrast to the case in merge, not all non-null constant results are covered at
             * this point. Since these are not covered by ObjectStamps, we need special cases to
             * handle them. For example strengthening a non-null type value with a non-null constant
             * of a compatible type is not covered by the cases above.
             */
            AbstractObjectStamp joined = (AbstractObjectStamp) stamp.join(other.stamp);
            if (isConstant() && !other.isConstant() && joined.equals(stamp)) {
                return this;
            } else if (!isConstant() && other.isConstant() && joined.equals(other.stamp)) {
                return other;
            }
            return Pentagon.of(joined);
        }

        @Override
        public boolean isConstant() {
            /*
             * Null constants are represented using the stamp. Other constants are represented by
             * keeping a reference to the constant that exactly represents this object, as well as a
             * stamp with the exact type of the given object.
             */
            return stamp.isConstant() || isNonNullConstant();
        }

        public boolean isNonNullConstant() {
            return constant != null;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ObjectPentagon that = (ObjectPentagon) o;
            // only constant is nullable
            return stamp.equals(that.stamp) && Objects.equals(constant, that.constant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stamp, constant);
        }

        @Override
        public String toString() {
            if (isNonNullConstant()) {
                return "Pntg<Obj>{%s,%s}".formatted(constant, stamp);
            } else {
                return "Pntg<Obj>{%s}".formatted(stamp);
            }
        }
    }
}
