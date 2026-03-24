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
package jdk.graal.compiler.nodes.loop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;

/**
 * This class describes a value node that is an induction variable in a counted loop.
 */
public abstract class InductionVariable {

    /**
     * Captures a computed induction-variable extremum together with the overflow conditions for the
     * arithmetic operations used to produce it. If any condition evaluates to {@code true}, the
     * corresponding operation in this extremum computation cannot be evaluated without overflow.
     * Operations whose overflow is already covered by the loop overflow guard are omitted from
     * {@link #overflowConditions()}.
     */
    public record Extremum(ValueNode extremum, List<LogicNode> overflowConditions) {
    }

    public enum Direction {
        Up,
        Down;

        public Direction opposite() {
            switch (this) {
                case Up:
                    return Down;
                case Down:
                    return Up;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(this); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    public abstract StructuredGraph graph();

    protected final Loop loop;

    public InductionVariable(Loop loop) {
        this.loop = loop;
    }

    public Loop getLoop() {
        return loop;
    }

    public abstract Direction direction();

    /**
     * Returns the value node that is described by this induction variable.
     */
    public abstract ValueNode valueNode();

    /**
     * Returns the node that gives the initial value of this induction variable.
     */
    public abstract ValueNode initNode();

    /**
     * Returns the stride of the induction variable. The stride is the value that is added to the
     * induction variable at each iteration.
     */
    public abstract ValueNode strideNode();

    public abstract boolean isConstantInit();

    public abstract boolean isConstantStride();

    public abstract long constantInit();

    public abstract long constantStride();

    /**
     * Returns the extremum value of the induction variable. The extremum value is the value of the
     * induction variable in the loop body of the last iteration, only taking into account the main
     * loop limit test. It's possible for the loop to exit before this value if
     * {@link CountedLoopInfo#isExactTripCount()} returns false for the containing loop.
     */
    public ValueNode extremumNode() {
        return extremumNode(false, valueNode().stamp(NodeView.DEFAULT));
    }

    public abstract ValueNode extremumNode(boolean assumeLoopEntered, Stamp stamp);

    public abstract ValueNode extremumNode(boolean assumeLoopEntered, Stamp stamp, ValueNode maxTripCount);

    /**
     * Computes an extremum expression together with the overflow conditions for the arithmetic used
     * to produce that expression.
     * <p>
     * The returned {@linkplain Extremum#extremum() extremum expression} uses {@code extremumStamp},
     * which is chosen by the caller. Each overflow condition is still emitted in the native
     * arithmetic width of the IV step that can overflow, so the condition matches that step's real
     * overflow semantics.
     * <p>
     * For example, consider a loop {@code for (int i = 0; i < max; i++)} containing an IV
     * {@code i * 2}. Asking for a {@code long} extremum returns {@code (((long) max) - 1L) * 2L}.
     * The accompanying overflow condition is still the {@code int}-width check
     * {@code !IntegerMulExactOverflowNode.create(max - 1, 2)}, because the multiplication overflows
     * in {@code int}, not in the final widened expression.
     */
    public Extremum extremumComputation(boolean assumeLoopEntered, Stamp extremumStamp, ValueNode effectiveMaxTripCount, InductionVariable bodyIV,
                    InductionVariable limitCheckedIV) {
        /*
         * Follow this IV's base chain to the root/basic IV, compute that root extremum and its
         * overflow conditions first, then rebuild the derived IV extrema and overflow conditions on
         * the way back out.
         */
        ArrayList<DerivedInductionVariable> derivedIVs = null;
        InductionVariable current = this;
        while (current instanceof DerivedInductionVariable derived) {
            if (derivedIVs == null) {
                derivedIVs = new ArrayList<>();
            }
            derivedIVs.add(derived);
            current = derived.getBase();
        }
        GraalError.guarantee(current instanceof BasicInductionVariable, "Expected basic induction variable but got %s", current);

        ArrayList<LogicNode> overflowConditions = new ArrayList<>();
        /*
         * The root/basic IV contributes overflow conditions in the width of its own IV stamp, even
         * if the caller wants the final extremum in some other compatible stamp.
         */
        Stamp ivStamp = current.valueNode().stamp(NodeView.DEFAULT);
        ValueNode currentExtremum = current.extremumNode(assumeLoopEntered, extremumStamp, effectiveMaxTripCount);
        ValueNode currentIvExtremum;
        /*
         * The body and limit checked IVs are already covered by the counted loop's overflow guard
         * or the guarantee that the counter does not overflow.
         */
        if (current == bodyIV || current == limitCheckedIV) {
            currentIvExtremum = current.extremumNode(assumeLoopEntered, ivStamp, effectiveMaxTripCount);
        } else {
            currentIvExtremum = current.collectLocalExtremumOverflowConditions(assumeLoopEntered, ivStamp, effectiveMaxTripCount, null, overflowConditions);
        }

        if (derivedIVs != null) {
            for (int i = derivedIVs.size() - 1; i >= 0; i--) {
                DerivedInductionVariable derived = derivedIVs.get(i);
                /*
                 * Each derived IV likewise contributes overflow conditions in the width of its own
                 * IV stamp, not in the caller-requested final extremum stamp.
                 */
                Stamp derivedIVStamp = derived.valueNode().stamp(NodeView.DEFAULT);
                if (derived == bodyIV || derived == limitCheckedIV) {
                    currentIvExtremum = derived.extremumNode(assumeLoopEntered, derivedIVStamp, effectiveMaxTripCount);
                } else {
                    currentIvExtremum = derived.collectLocalExtremumOverflowConditions(assumeLoopEntered, derivedIVStamp, effectiveMaxTripCount, currentIvExtremum, overflowConditions);
                }
                currentExtremum = derived.extremumNode(assumeLoopEntered, extremumStamp, effectiveMaxTripCount);
            }
        }
        return new Extremum(currentExtremum, overflowConditions);
    }

    /**
     * Adds the overflow conditions contributed by this induction variable's own extremum
     * arithmetic. If any added condition evaluates to {@code true}, the corresponding local step of
     * the extremum computation overflows.
     * <p>
     * The supplied {@code stamp} is the stamp that the resulting extremum value should have.
     * <p>
     * For derived IVs, {@code baseExtremum} is the already computed extremum of the base IV as
     * produced by the preceding step of this iterative computation. For a basic IV,
     * {@code baseExtremum} is unused and can be {@code null}.
     *
     * @return this IV's extremum in {@code stamp}, as threaded through the iterative overflow
     *         computation
     */
    protected abstract ValueNode collectLocalExtremumOverflowConditions(boolean assumeLoopEntered, Stamp stamp, ValueNode effectiveMaxTripCount, ValueNode baseExtremum,
                    Collection<LogicNode> conditions);

    public abstract boolean isConstantExtremum();

    public abstract long constantExtremum();

    /**
     * Returns the exit value of the induction variable. The exit value is the value of the
     * induction variable at the loop exit.
     */
    public abstract ValueNode exitValueNode();

    /**
     * Deletes any nodes created within the scope of this object that have no usages.
     */
    public abstract void deleteUnusedNodes();

    /*
     * Range check predication support.
     */

    /**
     * Is this = C * ref + n, C a constant?
     */
    public boolean isConstantScale(InductionVariable ref) {
        return this == ref;
    }

    /**
     * this = C * ref + n, returns C.
     */
    public long constantScale(InductionVariable ref) {
        assert this == ref : this + "!=" + ref;
        return 1;
    }

    /**
     * Is this = n * ref + 0?
     */
    public boolean offsetIsZero(InductionVariable ref) {
        return this == ref;
    }

    /**
     * If this = n * ref + offset, returns offset or null otherwise.
     */
    public ValueNode offsetNode(InductionVariable ref) {
        assert !offsetIsZero(ref);
        return null;
    }

    /**
     * Duplicate this iv including all (non-constant) nodes.
     */
    public abstract InductionVariable duplicate();

    /**
     * Duplicate this IV with a new init node.
     */
    public abstract InductionVariable duplicateWithNewInit(ValueNode newInit);

    /**
     * Return the value of this iv upon the first entry of the loop.
     */
    public abstract ValueNode entryTripValue();

    /**
     * Return the root induction variable of this IV. The root induction variable is a
     * {@link BasicInductionVariable} directly representing a loop phi and a stride. It is computed
     * by following {@link DerivedInductionVariable#getBase()} until the
     * {@link BasicInductionVariable} is found.
     */
    public BasicInductionVariable getRootIV() {
        if (this instanceof BasicInductionVariable) {
            return (BasicInductionVariable) this;
        }
        assert this instanceof DerivedInductionVariable : this;
        return ((DerivedInductionVariable) this).getBase().getRootIV();
    }

    public enum IVToStringVerbosity {
        /**
         * Print a full representation of the induction variable including all nodes and its type.
         */
        FULL,
        /*
         * Only print the operations in a numeric form.
         */
        NUMERIC
    }

    /**
     * Determines if the components of this IV are structurally intact, i.e., part of a graph, not
     * deleted etc.
     */
    public abstract boolean structuralIntegrityValid();

    public abstract String toString(IVToStringVerbosity verbosity);

    @Override
    public String toString() {
        return toString(IVToStringVerbosity.NUMERIC);
    }

}
