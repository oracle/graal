/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.dfanalysis.analyses;

import java.util.Collections;
import java.util.HashSet;
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
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.TriState;

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
                // this is an impossible value (these might occur
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
            case IntegerStamp iStamp -> Pentagon.of(iStamp, Set.of(), Set.of());
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
                nuLBs = Set.of();
            } else {
                nuLBs = new HashSet<>(lowerBounds);
                nuLBs.retainAll(other.lowerBounds);
                if (nuLBs.isEmpty()) {
                    nuLBs = Set.of();
                }
            }
            // merge strict upper bounds
            Set<ValueNode> nuSUBs;
            if (strictUpperBounds == null) {
                nuSUBs = other.strictUpperBounds;
            } else if (other.strictUpperBounds == null) {
                nuSUBs = strictUpperBounds;
            } else if (strictUpperBounds.isEmpty() || other.strictUpperBounds.isEmpty()) {
                nuSUBs = Set.of();
            } else {
                nuSUBs = new HashSet<>(strictUpperBounds);
                nuSUBs.retainAll(other.strictUpperBounds);
                if (nuSUBs.isEmpty()) {
                    nuSUBs = Set.of();
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
                nuLBs = new HashSet<>(lowerBounds);
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
                nuSUBs = new HashSet<>(strictUpperBounds);
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
                UNRESTRICTED[logBits] = new IntegerPentagon(IntegerStamp.create(1 << logBits), Set.of(), Set.of());
            }
            for (int logBits = 0; logBits < UNRESTRICTED.length; logBits++) {
                UNEVALUATED[logBits] = new IntegerPentagon(IntegerStamp.createEmptyStamp(1 << logBits), null, null);
            }
        }
    }

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
             * All results containing constants are already covered here because all constants lie
             * directly below unevaluated. Therefore, we simply merge stamps and omit the constant.
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
             * Since in the lattice of ObjectStamps, there is a large section of the lattice below
             * non-null constants, we need special handling for such constants (which are not
             * represented in the stamps themselves, but rather as reference to the constant node).
             * If this (exclusive-)or other is constant and stronger than the respective
             * counterpart, then the result of the strengthen operation is the given element. An
             * element is considered stronger in this case when the stamp strengthen is equal to the
             * given element.
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
             * keeping a reference to the node that exactly represents this object, as well as a
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
